/**
 * catalog-service 읽기 부하 테스트
 *
 * 목적: 상품/카테고리 조회 API의 처리량 및 응답시간 측정
 *       --scale catalog-service=N 효과 검증
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run /scripts/catalog-read.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';

export const options = {
  stages: [
    { duration: '30s', target: 50  },  // warm-up
    { duration: '1m',  target: 50  },  // steady
    { duration: '30s', target: 200 },  // spike
    { duration: '2m',  target: 200 },  // spike 유지
    { duration: '30s', target: 0   },  // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed:   ['rate<0.01'],
  },
};

export function setup() {
  const res = http.get(`${BASE_URL}/api/categories`);
  if (res.status !== 200) return { categoryIds: [] };
  return { categoryIds: JSON.parse(res.body).map(c => c.id) };
}

export default function ({ categoryIds }) {
  const scenario = randomIntBetween(1, 3);

  if (scenario === 1) {
    // 상품 전체 목록
    const res = http.get(`${BASE_URL}/api/products?size=20`);
    check(res, { 'products 200': r => r.status === 200 });

  } else if (scenario === 2 && categoryIds.length > 0) {
    // 카테고리 필터 조회
    const categoryId = categoryIds[randomIntBetween(0, categoryIds.length - 1)];
    const res = http.get(`${BASE_URL}/api/products?categoryId=${categoryId}&size=20`);
    check(res, { 'category filter 200': r => r.status === 200 });

  } else {
    // 카테고리 목록
    const res = http.get(`${BASE_URL}/api/categories`);
    check(res, { 'categories 200': r => r.status === 200 });
  }

  sleep(randomIntBetween(1, 3));
}