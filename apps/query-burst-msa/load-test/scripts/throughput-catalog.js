/**
 * [Step 2] catalog-service 처리량 한계 분석
 *
 * 목적:
 *   DB 기반 catalog-service가 SLO를 지키면서
 *   최대 몇 RPS까지 처리할 수 있는지 측정한다.
 *   ranking-service(Step 1)와 비교하면 Redis vs DB 성능 차이를 확인할 수 있다.
 *
 * SLO (합격 기준):
 *   - p95 응답시간 < 500ms
 *   - 에러율 < 1%
 *   위 기준을 초과하는 순간의 RPS = catalog-service 처리량 한계
 *
 * 해석 방법:
 *   1. Grafana → http_server_requests_seconds (catalog-service) p95 추이 확인
 *   2. hikaricp_connections_active 가 maximum-pool-size(50)에 근접하면 DB 커넥션 포화
 *   3. hikaricp_connections_pending > 0 이면 병목이 DB 커넥션 풀임
 *   4. CPU 낮은데 응답시간 높으면 → 쿼리 자체가 느린 것
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-catalog.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';

export const options = {
  scenarios: {
    throughput_test: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      stages: [
        { duration: '30s', target: 10  },  // 워밍업
        { duration: '1m',  target: 50  },  // 50 RPS
        { duration: '1m',  target: 100 },  // 100 RPS
        { duration: '1m',  target: 150 },  // 150 RPS
        { duration: '1m',  target: 200 },  // 200 RPS
        { duration: '1m',  target: 300 },  // 300 RPS — 한계 탐색
        { duration: '30s', target: 0   },  // 정리
      ],
    },
  },

  thresholds: {
    http_req_duration: [
      { threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '10s' },
    ],
    http_req_failed: [
      { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' },
    ],
  },
};

export function setup() {
  // 카테고리 ID 미리 수집 (현실적인 필터 조회를 위해)
  const res = http.get(`${BASE_URL}/api/categories`);
  if (res.status !== 200) return { categoryIds: [] };
  return { categoryIds: JSON.parse(res.body).map(c => c.id) };
}

export default function ({ categoryIds }) {
  // 3가지 엔드포인트 혼합 (실제 트래픽 패턴 반영)
  //   50% - 상품 전체 목록
  //   30% - 카테고리별 필터 조회 (인덱스 사용 여부 관찰)
  //   20% - 카테고리 목록
  const r = Math.random();

  if (r < 0.5) {
    const res = http.get(
      `${BASE_URL}/api/products?size=${randomIntBetween(10, 30)}`,
      { tags: { name: 'GET /api/products' } }
    );
    check(res, { 'products 200': r => r.status === 200 });

  } else if (r < 0.8 && categoryIds.length > 0) {
    const categoryId = categoryIds[randomIntBetween(0, categoryIds.length - 1)];
    const res = http.get(
      `${BASE_URL}/api/products?categoryId=${categoryId}&size=${randomIntBetween(10, 30)}`,
      { tags: { name: 'GET /api/products?categoryId' } }
    );
    check(res, { 'category filter 200': r => r.status === 200 });

  } else {
    const res = http.get(
      `${BASE_URL}/api/categories`,
      { tags: { name: 'GET /api/categories' } }
    );
    check(res, { 'categories 200': r => r.status === 200 });
  }
}