/**
 * ranking-service 읽기 부하 테스트
 *
 * 목적: Redis Sorted Set 기반 실시간 랭킹 API 처리량 측정
 *       응답시간이 catalog(DB)보다 현저히 낮아야 정상
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run /scripts/ranking-read.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL_RANKING || 'http://localhost:18112';

export const options = {
  stages: [
    { duration: '30s', target: 100 },  // warm-up
    { duration: '2m',  target: 100 },  // steady
    { duration: '30s', target: 500 },  // spike (Redis 한계 측정)
    { duration: '2m',  target: 500 },  // spike 유지
    { duration: '30s', target: 0   },  // ramp-down
  ],
  thresholds: {
    // Redis 기반: 95th percentile 200ms 이내 목표
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const windowHours = randomIntBetween(1, 24);
  const size        = randomIntBetween(5, 20);

  const res = http.get(`${BASE_URL}/api/rankings/products?windowHours=${windowHours}&size=${size}`);
  check(res, { 'ranking 200': r => r.status === 200 });

  sleep(randomIntBetween(1, 2));
}