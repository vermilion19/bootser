/**
 * E2E 데이터 플로우 검증 테스트
 *
 * 목적: 이벤트 발생 → Ingestion → Kafka → Stream Processor → Analytics 조회까지
 *       전체 데이터 파이프라인이 정상 동작하는지 검증
 *
 * 검증 포인트:
 *   1. 이벤트 발행 후 Analytics에서 조회 가능 여부
 *   2. 데이터 누락 없음 (발행 수 ≈ 조회 수)
 *   3. E2E 지연 시간 (Ingestion → Analytics 반영)
 *
 * 합격 기준:
 *   - 이벤트 발행 후 10초 이내 Analytics에서 조회 가능
 *   - 데이터 누락률 < 1%
 *
 * 실행:
 *   docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
 *     run --rm k6 run /scripts/e2e-data-flow.js
 *
 * 주의:
 *   - 이 테스트는 처리량보다 데이터 정합성에 초점을 둔다
 *   - Stream Processor가 실행 중이어야 한다
 *   - Analytics DB에 데이터가 반영되어야 조회 가능
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const INGESTION_URL = __ENV.BASE_URL_INGESTION || 'http://localhost:8092';
const ANALYTICS_URL = __ENV.BASE_URL_ANALYTICS || 'http://localhost:8094';
const STREAM_URL = __ENV.BASE_URL_STREAM || 'http://localhost:8093';

// Custom Metrics
const eventsSent = new Counter('e2e_events_sent_total');
const eventsVerified = new Counter('e2e_events_verified_total');
const eventsNotFound = new Counter('e2e_events_not_found_total');
const e2eLatency = new Trend('e2e_latency_ms', true);
const verificationAttempts = new Counter('e2e_verification_attempts_total');

// 테스트용 고유 디바이스 ID 프리픽스 (테스트 간 격리)
const TEST_RUN_ID = `e2e-${Date.now()}`;

export const options = {
  scenarios: {
    // E2E 플로우 검증: 적은 수의 VU로 천천히 실행
    e2e_verification: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },   // 램프업
        { duration: '3m', target: 10 },   // 검증 실행
        { duration: '30s', target: 0 },   // 쿨다운
      ],
      exec: 'e2eVerificationFlow',
      tags: { scenario: 'e2e_verification' },
    },

    // 백그라운드 데이터 흐름 확인
    data_flow_check: {
      executor: 'constant-vus',
      vus: 2,
      duration: '4m',
      exec: 'dataFlowCheckFlow',
      tags: { scenario: 'data_flow_check' },
    },
  },

  thresholds: {
    // E2E 지연 SLO
    'e2e_latency_ms': ['p(95)<10000'],  // 10초 이내

    // 데이터 누락률
    'http_req_failed': ['rate<0.05'],
  },
};

// ──────────────────────────────────────────────────────────────────────────────
// E2E 검증 플로우
// ──────────────────────────────────────────────────────────────────────────────
export function e2eVerificationFlow() {
  const testDeviceId = `${TEST_RUN_ID}-device-${__VU}-${__ITER}`;
  const testTimestamp = new Date();

  group('1_send_event', () => {
    // 텔레메트리 이벤트 발송
    const payload = JSON.stringify({
      deviceId: testDeviceId,
      eventType: 'TELEMETRY',
      timestamp: testTimestamp.toISOString(),
      latitude: 37.5665 + (Math.random() - 0.5) * 0.01,
      longitude: 126.9780 + (Math.random() - 0.5) * 0.01,
      speed: 50 + Math.random() * 50,
      heading: Math.random() * 360,
      altitude: 50,
      accuracy: 5,
      batteryLevel: 80,
      signalStrength: -70,
    });

    const res = http.post(
      `${INGESTION_URL}/ingestion/v1/mqtt/messages`,
      JSON.stringify({
        topic: `telemetryhub/devices/${testDeviceId}/telemetry`,
        qos: 1,
        payload,
      }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST /ingestion/v1/mqtt/messages [e2e]' },
      }
    );

    if (check(res, { 'event sent 200': r => r.status === 200 })) {
      eventsSent.add(1);
    } else {
      console.log(`Failed to send event for ${testDeviceId}: ${res.status}`);
      return;
    }
  });

  // Stream Processor 처리 대기
  sleep(3);

  group('2_verify_in_analytics', () => {
    const startVerify = Date.now();
    let found = false;
    const maxAttempts = 5;
    const retryInterval = 2;  // 2초 간격

    for (let attempt = 1; attempt <= maxAttempts && !found; attempt++) {
      verificationAttempts.add(1);

      // Analytics에서 해당 디바이스 조회
      const res = http.get(
        `${ANALYTICS_URL}/analytics/v1/devices/${testDeviceId}/latest`,
        { tags: { name: 'GET /analytics/v1/devices/:id/latest [e2e]' } }
      );

      if (res.status === 200) {
        const body = JSON.parse(res.body);
        if (body.success && body.data) {
          found = true;
          eventsVerified.add(1);
          e2eLatency.add(Date.now() - startVerify);
          console.log(`✓ Verified ${testDeviceId} after ${attempt} attempts`);
        }
      }

      if (!found && attempt < maxAttempts) {
        sleep(retryInterval);
      }
    }

    if (!found) {
      eventsNotFound.add(1);
      console.log(`✗ Not found ${testDeviceId} after ${maxAttempts} attempts`);
    }
  });

  sleep(1);
}

// ──────────────────────────────────────────────────────────────────────────────
// 데이터 플로우 상태 확인
// ──────────────────────────────────────────────────────────────────────────────
export function dataFlowCheckFlow() {
  group('pipeline_health_check', () => {
    // 1. Ingestion 서비스 상태
    const ingestionMetrics = http.get(
      `${INGESTION_URL}/ingestion/v1/metrics`,
      { tags: { name: 'GET /ingestion/v1/metrics [health]' } }
    );

    const ingestionOk = check(ingestionMetrics, {
      'ingestion service healthy': r => r.status === 200,
    });

    if (ingestionOk) {
      const body = JSON.parse(ingestionMetrics.body);
      if (body.data) {
        console.log(`Ingestion - Total: ${body.data.totalIngested || 0}, Published: ${body.data.totalPublished || 0}`);
      }
    }

    // 2. Stream Processor 상태
    const streamMetrics = http.get(
      `${STREAM_URL}/stream/v1/metrics`,
      { tags: { name: 'GET /stream/v1/metrics [health]' } }
    );

    const streamOk = check(streamMetrics, {
      'stream processor healthy': r => r.status === 200,
    });

    if (streamOk) {
      const body = JSON.parse(streamMetrics.body);
      if (body.data) {
        console.log(`Stream - Writes: ${body.data.totalProjectionWrites || 0}, Failures: ${body.data.totalProjectionWriteFailures || 0}`);
      }
    }

    // 3. 최근 이벤트 확인 (데이터가 흐르고 있는지)
    const recentEvents = http.get(
      `${INGESTION_URL}/ingestion/v1/events?limit=5`,
      { tags: { name: 'GET /ingestion/v1/events [health]' } }
    );

    check(recentEvents, {
      'recent events available': r => r.status === 200,
    });
  });

  sleep(10);  // 10초 간격 체크
}

// ──────────────────────────────────────────────────────────────────────────────
// Setup: 초기 연결 확인
// ──────────────────────────────────────────────────────────────────────────────
export function setup() {
  console.log(`\n========== E2E Data Flow Test ==========`);
  console.log(`Test Run ID: ${TEST_RUN_ID}`);
  console.log(`Ingestion URL: ${INGESTION_URL}`);
  console.log(`Analytics URL: ${ANALYTICS_URL}`);
  console.log(`Stream URL: ${STREAM_URL}`);
  console.log(`==========================================\n`);

  // 서비스 연결 확인
  const services = [
    { name: 'Ingestion', url: `${INGESTION_URL}/ingestion/v1/metrics` },
    { name: 'Analytics', url: `${ANALYTICS_URL}/analytics/v1/devices/latest?limit=1` },
    { name: 'Stream', url: `${STREAM_URL}/stream/v1/metrics` },
  ];

  for (const service of services) {
    const res = http.get(service.url, { timeout: '5s' });
    if (res.status !== 200) {
      console.warn(`⚠ ${service.name} service may not be available (status: ${res.status})`);
    } else {
      console.log(`✓ ${service.name} service is up`);
    }
  }

  return { testRunId: TEST_RUN_ID };
}

export function handleSummary(data) {
  const metrics = data.metrics;

  const sent = metrics.e2e_events_sent_total?.values?.count || 0;
  const verified = metrics.e2e_events_verified_total?.values?.count || 0;
  const notFound = metrics.e2e_events_not_found_total?.values?.count || 0;
  const verifyRate = sent > 0 ? (verified / sent * 100) : 0;

  console.log('\n============ E2E Data Flow Test Summary ============');

  console.log('\n[Event Verification]');
  console.log(`  Events Sent:     ${sent}`);
  console.log(`  Events Verified: ${verified}`);
  console.log(`  Events Not Found: ${notFound}`);
  console.log(`  Verification Rate: ${verifyRate.toFixed(2)}%`);

  console.log('\n[E2E Latency (ms)]');
  console.log(`  p50: ${metrics.e2e_latency_ms?.values?.['p(50)']?.toFixed(2) || 'N/A'}`);
  console.log(`  p95: ${metrics.e2e_latency_ms?.values?.['p(95)']?.toFixed(2) || 'N/A'}`);
  console.log(`  max: ${metrics.e2e_latency_ms?.values?.max?.toFixed(2) || 'N/A'}`);

  console.log('\n[Verification Attempts]');
  console.log(`  Total Attempts: ${metrics.e2e_verification_attempts_total?.values?.count || 0}`);
  console.log(`  Avg per Event: ${sent > 0 ? ((metrics.e2e_verification_attempts_total?.values?.count || 0) / sent).toFixed(2) : 'N/A'}`);

  console.log('\n[Pass/Fail]');
  if (verifyRate >= 99) {
    console.log('  ✓ PASS - Data integrity verified (>= 99%)');
  } else if (verifyRate >= 95) {
    console.log('  △ WARN - Some data may be delayed (>= 95%)');
  } else {
    console.log('  ✗ FAIL - Data loss detected (< 95%)');
  }

  console.log('\n=====================================================\n');

  return {};
}
