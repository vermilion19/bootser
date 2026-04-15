/**
 * [동시성 검증 2] 중복 주문 (Idempotency) 테스트
 *
 * 검증하려는 것:
 *   동일한 Idempotency-Key로 동시에 N개의 주문 요청이 들어올 때
 *   주문이 1건만 생성되는가? (중복 주문 방지)
 *
 * 코드 분석:
 *   catalog-service: InventoryReservationEntity.request_id에 UNIQUE 제약 존재
 *     → findByRequestId로 먼저 조회, 없으면 createReservation → TOCTOU 윈도우 존재
 *     → 동시에 진입 시 DB UNIQUE 제약이 최후 방어선
 *
 *   order-service: orderId를 Snowflake로 매번 새로 생성
 *     → catalog 예약이 1건만 성공해도 order는 2건 시도 가능
 *     → 두 번째 order에서 catalog 예약 실패(UNIQUE 제약 위반) → 500 또는 REJECTED 반환
 *
 * 합격 기준:
 *   STOCK_RESERVED 상태의 주문이 정확히 1건   ← 중복 주문 없음
 *   나머지 응답은 REJECTED 또는 에러여야 함
 *
 * 검증 포인트 (테스트 후 수동 확인):
 *   SELECT COUNT(*) FROM customer_order WHERE ...; -- 1건이어야 함
 *   SELECT COUNT(*) FROM inventory_reservation WHERE request_id = '{sharedKey}'; -- 1건이어야 함
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/concurrency-idempotency.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ORDER_URL   = __ENV.BASE_URL_ORDER   || 'http://localhost:18115';
const CATALOG_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';

// 정상 예약 성공 카운터 — 1이어야 함
const reservedCount   = new Counter('idempotency_reserved_count');
// 서버 에러 카운터 — UNIQUE 제약 충돌 시 발생 (0이 이상적, 409로 처리되어야 함)
const serverErrorCount = new Counter('idempotency_server_error_count');

const CONCURRENT_VUS = 50;  // 동일 키로 동시에 요청할 VU 수

export const options = {
  scenarios: {
    idempotency_test: {
      // shared-iterations: 전체 VU가 같은 iteration pool을 공유 → 동시 시작 보장
      executor: 'shared-iterations',
      vus: CONCURRENT_VUS,
      iterations: CONCURRENT_VUS,  // VU 당 1번씩
      maxDuration: '30s',
    },
  },
  thresholds: {
    // STOCK_RESERVED 응답이 1건 초과면 중복 주문 발생
    idempotency_reserved_count: ['count<=1'],
    // 서버 에러는 0건이 이상적 (현실적으론 UNIQUE 제약 충돌로 5xx 발생할 수 있음)
    // → 이 값이 크면 catalog-service가 409로 처리하도록 개선 필요
    idempotency_server_error_count: ['count<50'],
  },
};

export function setup() {
  const products = JSON.parse(
    http.get(`${CATALOG_URL}/api/products?status=ACTIVE&size=10`).body
  );
  if (products.length === 0) throw new Error('ACTIVE 상품 없음');

  // 모든 VU가 공유할 단일 Idempotency-Key
  const sharedKey = uuidv4();
  const product = products[0];

  console.log(`[idempotency] 공유 Idempotency-Key: ${sharedKey}`);
  console.log(`[idempotency] ${CONCURRENT_VUS}개 VU가 동시에 동일 키로 주문 요청`);
  console.log('[idempotency] 기대 결과: STOCK_RESERVED 1건, 나머지는 REJECTED or 에러');

  return {
    sharedKey,
    productId: product.id,
    memberId: 999999,  // 동일 memberId 사용
  };
}

export default function ({ sharedKey, productId, memberId }) {
  // 모든 VU가 완전히 동시에 요청하도록 sleep 없이 즉시 전송
  const res = http.post(
    `${ORDER_URL}/api/orders`,
    JSON.stringify({
      memberId,
      items: [{ productId, quantity: 1 }],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': sharedKey,  // ← 모든 VU가 동일한 키 사용
      },
      tags: { name: 'POST /api/orders [idempotency]' },
    }
  );

  if (res.status === 201) {
    const body = JSON.parse(res.body);
    if (body.status === 'STOCK_RESERVED') {
      reservedCount.add(1);
    }
    check(res, { '201 응답': r => r.status === 201 });

  } else if (res.status >= 500) {
    // 5xx: UNIQUE 제약 충돌이 제대로 핸들링되지 않는 경우
    // → catalog-service가 409 Conflict으로 처리해야 함
    serverErrorCount.add(1);
    check(res, { '5xx 없어야 함 (409로 처리 필요)': _ => false });

  } else {
    // 4xx: REJECTED (정상 - 중복 키 거부)
    check(res, { '4xx 정상 거부': r => r.status >= 400 && r.status < 500 });
  }
}

export function handleSummary(data) {
  const reserved    = data.metrics['idempotency_reserved_count']?.values?.count ?? 0;
  const serverError = data.metrics['idempotency_server_error_count']?.values?.count ?? 0;
  const total       = data.metrics['http_reqs']?.values?.count ?? 0;

  let verdict;
  if (reserved === 1 && serverError === 0) {
    verdict = '✅ 정상 — 중복 주문 없음, 에러 없이 처리됨';
  } else if (reserved === 1 && serverError > 0) {
    verdict = `⚠️  부분 통과 — 중복 주문은 없지만 ${serverError}건의 5xx 발생 (DB UNIQUE 제약이 최후 방어선으로 작동, 409로 처리하도록 개선 필요)`;
  } else if (reserved > 1) {
    verdict = `❌ 중복 주문 감지 — ${reserved}건의 STOCK_RESERVED 응답 (Idempotency 위반)`;
  } else {
    verdict = `⚠️  예상치 못한 결과 — STOCK_RESERVED: ${reserved}건`;
  }

  const summary = {
    '총 요청 수': total,
    'STOCK_RESERVED 건수 (기대값: 1)': reserved,
    '5xx 서버 에러 건수 (기대값: 0)': serverError,
    '판정': verdict,
    '수동 확인 SQL': [
      `-- catalog-service DB`,
      `SELECT COUNT(*) FROM inventory_reservation WHERE request_id = '{sharedKey}';  -- 1이어야 함`,
      `-- order-service DB`,
      `SELECT COUNT(*), status FROM customer_order GROUP BY status;  -- STOCK_RESERVED는 1건이어야 함`,
    ],
  };

  console.log('\n========== Idempotency 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));

  return { stdout: JSON.stringify(summary, null, 2) };
}
