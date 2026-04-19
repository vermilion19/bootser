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
const HEALTH_RETRY_COUNT = 30;
const HEALTH_RETRY_INTERVAL_SECONDS = 1;

// 201 응답 중 STOCK_RESERVED 상태 카운터
// 주의: idempotent 재반환도 201/STOCK_RESERVED일 수 있으므로 이 값만으로 중복 주문 여부를 단정할 수 없다.
const reservedReplayCount = new Counter('idempotency_reserved_replay_count');
// 201 응답이지만 기대한 주문 상태가 아닌 경우
const unexpectedCreatedStatusCount = new Counter('idempotency_unexpected_created_status_count');
// 서버 에러 카운터 — idempotent 재처리에서 0이어야 함
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
    idempotency_unexpected_created_status_count: ['count==0'],
    idempotency_server_error_count: ['count==0'],
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
        console.log(`[idempotency] ${name} health check 통과 (${attempt}/${HEALTH_RETRY_COUNT})`);
        return;
      }
    }

    console.log(`[idempotency] ${name} 준비 대기 중... (${attempt}/${HEALTH_RETRY_COUNT})`);
    sleep(HEALTH_RETRY_INTERVAL_SECONDS);
  }

  throw new Error(`${name} health check 실패: ${baseUrl}/actuator/health`);
}

export function setup() {
  waitForHealthyService('catalog-service', CATALOG_URL);
  waitForHealthyService('order-service', ORDER_URL);

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
    unitPrice: product.price,
    memberId: 999999,  // 동일 memberId 사용
  };
}

export default function ({ sharedKey, productId, unitPrice, memberId }) {
  // 모든 VU가 완전히 동시에 요청하도록 sleep 없이 즉시 전송
  const res = http.post(
    `${ORDER_URL}/api/orders`,
    JSON.stringify({
      memberId,
      items: [{ productId, quantity: 1, unitPrice }],
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
      reservedReplayCount.add(1);
    } else {
      unexpectedCreatedStatusCount.add(1);
    }
    check(res, {
      '201 응답': r => r.status === 201,
      '201 응답은 STOCK_RESERVED': _ => body.status === 'STOCK_RESERVED',
    });

  } else if (res.status >= 500) {
    serverErrorCount.add(1);
    check(res, { '5xx 없어야 함': _ => false });

  } else {
    // 현재 구현은 동일 키 재요청도 기존 주문을 재반환하므로 4xx는 이상 징후로 본다.
    unexpectedCreatedStatusCount.add(1);
    check(res, { '4xx는 없어야 함': _ => false });
  }
}

export function handleSummary(data) {
  const reserved201 = data.metrics['idempotency_reserved_replay_count']?.values?.count ?? 0;
  const unexpected201 = data.metrics['idempotency_unexpected_created_status_count']?.values?.count ?? 0;
  const serverError = data.metrics['idempotency_server_error_count']?.values?.count ?? 0;
  const total       = data.metrics['http_reqs']?.values?.count ?? 0;

  let verdict;
  if (serverError === 0 && unexpected201 === 0) {
    verdict = '자동 판정 통과 — 모든 요청이 201/STOCK_RESERVED로 처리됨 (동일 주문 재반환 가능성 포함)';
  } else if (serverError > 0) {
    verdict = `실패 — ${serverError}건의 5xx 발생`;
  } else {
    verdict = `실패 — 201이지만 기대하지 않은 상태/4xx가 ${unexpected201}건 발생`;
  }

  const summary = {
    '총 요청 수': total,
    '201 + STOCK_RESERVED 응답 수': reserved201,
    '예상치 못한 201 상태/4xx 건수 (기대값: 0)': unexpected201,
    '5xx 서버 에러 건수 (기대값: 0)': serverError,
    '판정': verdict,
    '주의': '여러 201/STOCK_RESERVED 응답은 동일 주문의 idempotent 재반환일 수 있으므로, 이 스크립트는 응답 횟수만으로 중복 주문을 단정하지 않는다.',
    '수동 확인 SQL': [
      `-- catalog-service DB`,
      `SELECT COUNT(*) FROM inventory_reservation WHERE request_id = '{sharedKey}';  -- 1이어야 함`,
      `-- order-service DB`,
      `SELECT COUNT(*) FROM customer_order WHERE idempotency_key = '{sharedKey}';  -- 1이어야 함`,
    ],
  };

  console.log('\n========== Idempotency 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));

  return { stdout: JSON.stringify(summary, null, 2) };
}
