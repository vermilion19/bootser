/**
 * [Step 1] ranking-service 처리량 한계 분석
 *
 * 목적:
 *   Redis Sorted Set 기반 ranking-service가 SLO를 지키면서
 *   최대 몇 RPS까지 처리할 수 있는지 측정한다.
 *
 * SLO (합격 기준):
 *   - p95 응답시간 < 200ms
 *   - 에러율 < 1%
 *   위 기준을 초과하는 순간의 RPS = ranking-service 처리량 한계
 *
 * 해석 방법:
 *   1. Grafana → http_server_requests_seconds (ranking-service) p95 추이 확인
 *   2. RPS가 올라갈수록 p95가 점진적으로 증가
 *   3. p95가 200ms를 넘거나 에러율이 1%를 초과하는 구간 = 한계 지점
 *   4. 테스트 종료 후 k6 output에서 "FAILED" threshold 확인
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-ranking.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL_RANKING || 'http://localhost:18112';

export const options = {
  scenarios: {
    throughput_test: {
      executor: 'ramping-arrival-rate',
      startRate: 10,        // 초기 10 RPS
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '30s', target: 10  },  // 워밍업
        { duration: '1m',  target: 100 },  // 100 RPS
        { duration: '1m',  target: 300 },  // 300 RPS
        { duration: '1m',  target: 600 },  // 600 RPS
        { duration: '1m',  target: 1000 }, // 1000 RPS
        { duration: '1m',  target: 1500 }, // 1500 RPS — 한계 탐색
        { duration: '30s', target: 0   },  // 정리
      ],
    },
  },

  thresholds: {
    // 이 기준을 초과하면 테스트 즉시 중단 → 그 시점의 RPS = 처리량 한계
    http_req_duration: [
      { threshold: 'p(95)<200', abortOnFail: true, delayAbortEval: '10s' },
    ],
    http_req_failed: [
      { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' },
    ],
  },
};

export default function () {
  const windowHours = randomIntBetween(1, 24);
  const size = randomIntBetween(5, 20);

  const res = http.get(
    `${BASE_URL}/api/rankings/realtime?windowHours=${windowHours}&size=${size}`,
    { tags: { name: 'GET /api/rankings/realtime' } }
  );

  check(res, { 'status 200': r => r.status === 200 });
}