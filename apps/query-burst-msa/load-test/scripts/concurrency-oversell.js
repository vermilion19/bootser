/**
 * [동시성 검증 1] 재고 초과 예약 (Oversell) 테스트
 *
 * 검증하려는 것:
 *   동일 상품에 재고보다 많은 주문이 동시에 들어올 때
 *   CatalogService.createReservation()의 SELECT FOR UPDATE가 재고 음수를 막는가?
 *
 * 코드 분석:
 *   CatalogService.createReservation() → findAllByIdInForUpdate() → product.reserve()
 *   → FOR UPDATE로 행 잠금 후 재고 확인 → 보호가 되어야 함
 *   단, 다품목 주문 (A+B, B+A 순서)이 혼재하면 Deadlock 발생 가능
 *
 * 합격 기준:
 *   Σ(STOCK_RESERVED 응답 수) <= 초기 재고 수량  ← Oversell 없음
 *   Deadlock 에러(5xx) 발생 시 재고가 안전하게 보호되는지 확인
 *
 * 검증 포인트 (테스트 후 수동 확인):
 *   SELECT stock FROM product WHERE id = {hotProductId};  -- 음수면 버그
 *   SELECT COUNT(*) FROM inventory_reservation WHERE status = 'RESERVED';  -- 재고 초과 여부
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/concurrency-oversell.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ORDER_URL   = __ENV.BASE_URL_ORDER   || 'http://localhost:18115';
const CATALOG_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';
const HEALTH_RETRY_COUNT = 30;
const HEALTH_RETRY_INTERVAL_SECONDS = 1;

// 재고 예약 성공 카운터 — 최종값이 초기 재고를 초과하면 Oversell 발생
const stockReservedCount = new Counter('oversell_reserved_count');
// 재고 부족으로 REJECTED된 카운터
const stockRejectedCount = new Counter('oversell_rejected_count');
// Oversell 감지 카운터 — 이 값이 0이어야 정상
const oversellDetected   = new Counter('oversell_detected');
// setup에서 선택한 핫 상품 정보
const hotProductStockGauge = new Gauge('oversell_hot_product_stock');
const hotProductIdGauge = new Gauge('oversell_hot_product_id');

// 테스트 설정: 핫 상품 실제 재고보다 많은 동시 요청을 발생시킴
const CONCURRENT_VUS = 200;  // 동시 요청 수 (재고의 4배)

export const options = {
  scenarios: {
    oversell_test: {
      // constant-arrival-rate: 특정 RPS를 보장 → 동시성 극대화
      executor: 'constant-arrival-rate',
      rate: 300,           // 초당 300 요청
      timeUnit: '1s',
      duration: '15s',     // 15초 = 총 4500 요청 (재고의 90배)
      preAllocatedVUs: CONCURRENT_VUS,
      maxVUs: 500,
    },
  },
  thresholds: {
    // Oversell이 한 건도 없어야 함
    oversell_detected: ['count==0'],
  },
};

function waitForHealthyService(name, baseUrl) {
  for (let attempt = 1; attempt <= HEALTH_RETRY_COUNT; attempt++) {
    const res = http.get(`${baseUrl}/actuator/health`, {
      tags: { name: `GET ${name} /actuator/health [preflight]` },
      timeout: '2s',
    });

    if (res.status === 200) {
      const body = JSON.parse(res.body);
      if (body.status === 'UP') {
        console.log(`[oversell] ${name} health check 통과 (${attempt}/${HEALTH_RETRY_COUNT})`);
        return;
      }
    }

    console.log(`[oversell] ${name} 준비 대기 중... (${attempt}/${HEALTH_RETRY_COUNT})`);
    sleep(HEALTH_RETRY_INTERVAL_SECONDS);
  }

  throw new Error(`${name} health check 실패: ${baseUrl}/actuator/health`);
}

export function setup() {
  waitForHealthyService('catalog-service', CATALOG_URL);
  waitForHealthyService('order-service', ORDER_URL);

  const products = JSON.parse(
    http.get(`${CATALOG_URL}/api/products?status=ACTIVE&size=100`).body
  );

  if (products.length === 0) {
    throw new Error('ACTIVE 상품이 없습니다. catalog data-init 먼저 실행하세요.');
  }

  // 현재 주문 가능한 ACTIVE 상품 중 재고가 가장 낮은 상품을 핫 상품으로 사용
  const hotProduct = products.reduce((min, current) =>
    current.stock < min.stock ? current : min
  );

  console.log(`[oversell] 핫 상품: id=${hotProduct.id}, 시작 재고=${hotProduct.stock}`);
  console.log(`[oversell] 기준 재고(실측): ${hotProduct.stock}, 동시 요청: ${CONCURRENT_VUS}`);
  console.log('[oversell] 주의: 테스트 후 catalog DB에서 product.stock 직접 확인 필요');
  hotProductStockGauge.add(hotProduct.stock);
  hotProductIdGauge.add(hotProduct.id);

  return {
    hotProductId: hotProduct.id,
    unitPrice: hotProduct.price,
    initialStock: hotProduct.stock,
  };
}

export default function ({ hotProductId, unitPrice }) {
  const res = http.post(
    `${ORDER_URL}/api/orders`,
    JSON.stringify({
      memberId: randomIntBetween(1, 1000000),
      items: [{ productId: hotProductId, quantity: 1, unitPrice }],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': uuidv4(),  // 각 요청마다 고유 키 → 순수한 동시 재고 경합 테스트
      },
      tags: { name: 'POST /api/orders [oversell]' },
    }
  );

  if (res.status !== 201) {
    check(res, { '5xx 없음': r => r.status < 500 });
    return;
  }

  const body = JSON.parse(res.body);
  const isReserved = body.status === 'STOCK_RESERVED';
  const isRejected = body.status === 'REJECTED';

  if (isReserved) {
    stockReservedCount.add(1);
  } else if (isRejected) {
    stockRejectedCount.add(1);
  }
}

export function handleSummary(data) {
  const reserved = data.metrics['oversell_reserved_count']?.values?.count ?? 0;
  const rejected = data.metrics['oversell_rejected_count']?.values?.count ?? 0;
  const initialStock = data.metrics['oversell_hot_product_stock']?.values?.value ?? 'unknown';
  const hotProductId = data.metrics['oversell_hot_product_id']?.values?.value ?? 'unknown';

  const summary = {
    '총 STOCK_RESERVED 수': reserved,
    '총 REJECTED(재고부족) 수': rejected,
    '핫 상품 ID': hotProductId,
    '기준 재고(실측)': initialStock,
    '판정': reserved <= initialStock
      ? `✅ 정상 — Oversell 없음 (예약 ${reserved}건 <= 재고 ${initialStock}건)`
      : `❌ Oversell 감지 — 예약 ${reserved}건 > 재고 ${initialStock}건`,
    '수동 확인 필요': [
      `SELECT id, stock, status FROM catalog_product WHERE id = ${hotProductId};  -- stock이 음수면 버그`,
      `SELECT COUNT(*) FROM inventory_reservation_item iri JOIN inventory_reservation ir ON ir.id = iri.reservation_id WHERE iri.product_id = ${hotProductId} AND ir.status = 'RESERVED';`,
    ],
  };

  console.log('\n========== Oversell 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));

  return { stdout: JSON.stringify(summary, null, 2) };
}
