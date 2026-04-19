/**
 * [Step 3] order-service 처리량 한계 분석
 *
 * 목적:
 *   DB + catalog-service HTTP 호출이 포함된 order-service가 SLO를 지키면서
 *   최대 몇 RPS까지 처리할 수 있는지 측정한다.
 *   Step 1, 2 대비 얼마나 낮은지 → 외부 의존성이 처리량에 미치는 영향 확인
 *
 * SLO (합격 기준):
 *   - p95 응답시간 < 3000ms  (catalog-service HTTP 왕복 포함)
 *   - 에러율 < 5%
 *   위 기준을 초과하는 순간의 RPS = order-service 처리량 한계
 *
 * 해석 방법:
 *   1. order-service p95가 높으면 → catalog-service 호출 시간 확인 (Tempo에서 span 조회)
 *   2. hikaricp_connections_pending > 0 → order-service DB 커넥션 풀 포화
 *   3. catalog-service p95도 함께 상승 → 연쇄 지연 발생 중
 *   4. resilience4j_circuitbreaker_state 변화 → CB 동작 여부 확인
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-order.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ORDER_URL   = __ENV.BASE_URL_ORDER   || 'http://localhost:18115';
const CATALOG_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';

// 재고 부족 응답은 처리량 한계와 별개로 추적
const stockRejected = new Counter('order_stock_rejected_total');

export const options = {
  scenarios: {
    throughput_test: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: '30s', target: 5  },  // 워밍업
        { duration: '1m',  target: 10 },  // 10 RPS
        { duration: '1m',  target: 20 },  // 20 RPS
        { duration: '1m',  target: 30 },  // 30 RPS
        { duration: '1m',  target: 50 },  // 50 RPS — 한계 탐색
        { duration: '1m',  target: 70 },  // 70 RPS
        { duration: '30s', target: 0  },  // 정리
      ],
    },
  },

  thresholds: {
    http_req_duration: [
      { threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '10s' },
    ],
    http_req_failed: [
      { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '10s' },
    ],
  },
};

export function setup() {
  const res = http.get(`${CATALOG_URL}/api/products?status=ACTIVE&size=100`);
  if (res.status !== 200) throw new Error(`상품 조회 실패: ${res.status}`);

  const products = JSON.parse(res.body);
  if (products.length === 0) throw new Error('ACTIVE 상품 없음. data-init 먼저 실행하세요.');

  console.log(`주문 가능 상품 ${products.length}개 로드 완료`);
  return { products };
}

export default function ({ products }) {
  const product = products[randomIntBetween(0, products.length - 1)];

  const res = http.post(
    `${ORDER_URL}/api/orders`,
    JSON.stringify({
      memberId: randomIntBetween(1, 1000000),
      items: [{ productId: product.id, quantity: randomIntBetween(1, 2), unitPrice: product.price }],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': uuidv4(),
      },
      tags: { name: 'POST /api/orders' },
    }
  );

  // 재고 부족(REJECTED)은 비즈니스 응답 → 에러율에 포함하지 않음
  const isRejected = res.status === 201 &&
    JSON.parse(res.body)?.status === 'REJECTED';

  if (isRejected) {
    stockRejected.add(1);
  }

  check(res, {
    // 201이면 성공 (REJECTED 여부는 별도 메트릭으로 추적)
    'order accepted': r => r.status === 201,
  });
}
