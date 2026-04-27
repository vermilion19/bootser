/**
 * 현실적 IoT 시나리오 부하 테스트
 *
 * 목적: 실제 차량 텔레매틱스 플랫폼에서 발생하는 복잡한 트래픽 패턴 시뮬레이션
 *
 * 시나리오:
 *   1. normal_operation (VU): 정상 운영 — 1000대 디바이스가 지속적으로 데이터 전송
 *   2. rush_hour_burst (arrival-rate): 출퇴근 시간 트래픽 폭발
 *   3. network_recovery (arrival-rate): 네트워크 복구 후 밀린 데이터 일괄 전송
 *   4. dashboard_load (VU): 관리자 대시보드 동시 조회 부하
 *   5. realtime_monitor (VU): 실시간 모니터링 API 지속 호출
 *
 * 측정 포인트:
 *   - ingestion_success_total / ingestion_failure_total
 *   - kafka_consumer_lag (Grafana에서 확인)
 *   - analytics_query_duration
 *   - burst 구간의 에러율/지연 변화
 *
 * 실행:
 *   docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/realistic-iot-scenario.js
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const INGESTION_URL = __ENV.BASE_URL_INGESTION || 'http://localhost:8092';
const ANALYTICS_URL = __ENV.BASE_URL_ANALYTICS || 'http://localhost:8094';
const STREAM_URL = __ENV.BASE_URL_STREAM || 'http://localhost:8093';

// Custom Metrics
const ingestionSuccess = new Counter('ingestion_success_total');
const ingestionFailure = new Counter('ingestion_failure_total');
const burstEventsSent = new Counter('burst_events_sent_total');
const analyticsSlowQuery = new Counter('analytics_slow_query_total');
const ingestionErrorRate = new Rate('ingestion_error_rate');
const batchIngestionDuration = new Trend('batch_ingestion_duration', true);

// 디바이스 풀
const DEVICE_POOL = Array.from({ length: 5000 }, (_, i) => ({
  id: `vehicle-${String(i + 1).padStart(6, '0')}`,
  region: ['SEOUL', 'BUSAN', 'DAEGU', 'INCHEON', 'GWANGJU'][i % 5],
  type: ['TRUCK', 'BUS', 'TAXI', 'DELIVERY'][i % 4],
}));

const DRIVING_EVENT_TYPES = ['HARD_BRAKE', 'OVERSPEED', 'CRASH'];

export const options = {
  scenarios: {
    // 1. 정상 운영: 지속적인 텔레메트리 데이터 수집
    normal_operation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },  // 램프업
        { duration: '5m', target: 200 },   // 정상 운영 유지
        { duration: '30s', target: 100 },  // 다운
      ],
      exec: 'normalOperationFlow',
      tags: { scenario: 'normal_operation' },
    },

    // 2. 출퇴근 시간 버스트: 갑작스런 트래픽 폭발
    rush_hour_burst: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: 800,
      stages: [
        { duration: '1m', target: 100 },   // 정상
        { duration: '15s', target: 1000 }, // 트래픽 폭발
        { duration: '2m', target: 1000 },  // 피크 유지
        { duration: '30s', target: 100 },  // 복구
        { duration: '1m', target: 100 },   // 안정화
      ],
      exec: 'rushHourBurstFlow',
      tags: { scenario: 'rush_hour_burst' },
      startTime: '1m',  // normal_operation 워밍업 후 시작
    },

    // 3. 네트워크 복구: 밀린 데이터 배치 전송
    network_recovery: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 300,
      stages: [
        { duration: '30s', target: 10 },   // 대기
        { duration: '10s', target: 500 },  // 복구 시점 — 대량 배치 전송
        { duration: '1m', target: 500 },   // 밀린 데이터 소진
        { duration: '30s', target: 50 },   // 정상화
      ],
      exec: 'networkRecoveryFlow',
      tags: { scenario: 'network_recovery' },
      startTime: '4m',  // rush_hour 후 시작
    },

    // 4. 대시보드 부하: 관리자 동시 조회
    dashboard_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '5m', target: 30 },
        { duration: '30s', target: 5 },
      ],
      exec: 'dashboardLoadFlow',
      tags: { scenario: 'dashboard_load' },
    },

    // 5. 실시간 모니터링: 지속적인 상태 폴링
    realtime_monitor: {
      executor: 'constant-vus',
      vus: 5,
      duration: '6m',
      exec: 'realtimeMonitorFlow',
      tags: { scenario: 'realtime_monitor' },
    },
  },

  thresholds: {
    // 시나리오별 응답시간 SLO
    'http_req_duration{scenario:normal_operation}': ['p(95)<200'],
    'http_req_duration{scenario:rush_hour_burst}': ['p(95)<500'],
    'http_req_duration{scenario:network_recovery}': ['p(95)<1000'],
    'http_req_duration{scenario:dashboard_load}': ['p(95)<2000'],
    'http_req_duration{scenario:realtime_monitor}': ['p(95)<300'],

    // 전체 에러율
    'http_req_failed': ['rate<0.05'],
    'ingestion_error_rate': ['rate<0.02'],
  },
};

// ──────────────────────────────────────────────────────────────────────────────
// 이벤트 생성 함수들
// ──────────────────────────────────────────────────────────────────────────────

function generateTelemetryEvent(device) {
  const baseCoords = {
    SEOUL: { lat: 37.5665, lon: 126.9780 },
    BUSAN: { lat: 35.1796, lon: 129.0756 },
    DAEGU: { lat: 35.8714, lon: 128.6014 },
    INCHEON: { lat: 37.4563, lon: 126.7052 },
    GWANGJU: { lat: 35.1595, lon: 126.8526 },
  };

  const base = baseCoords[device.region] || baseCoords.SEOUL;

  return {
    topic: `telemetryhub/devices/${device.id}/telemetry`,
    qos: 0,
    payload: JSON.stringify({
      metadata: {
        eventId: uuidv4(),
        deviceId: device.id,
        eventType: 'TELEMETRY',
        eventTime: new Date().toISOString(),
        ingestTime: null,
      },
      lat: base.lat + (Math.random() - 0.5) * 0.1,
      lon: base.lon + (Math.random() - 0.5) * 0.1,
      speed: Math.random() * 100,
      heading: Math.random() * 360,
      accelX: (Math.random() - 0.5) * 2,
      accelY: (Math.random() - 0.5) * 2,
    }),
  };
}

function generateDrivingEvent(device) {
  const drivingEventType = DRIVING_EVENT_TYPES[randomIntBetween(0, DRIVING_EVENT_TYPES.length - 1)];

  return {
    topic: `telemetryhub/devices/${device.id}/driving-event`,
    qos: 1,
    payload: JSON.stringify({
      metadata: {
        eventId: uuidv4(),
        deviceId: device.id,
        eventType: 'DRIVING_EVENT',
        eventTime: new Date().toISOString(),
        ingestTime: null,
      },
      type: drivingEventType,
      severity: randomIntBetween(1, 5),
      context: JSON.stringify({
        speed: 30 + Math.random() * 90,
        duration: randomIntBetween(500, 3000),
        gForce: 0.3 + Math.random() * 0.7,
      }),
    }),
  };
}

function generateDeviceHealthEvent(device) {
  return {
    topic: `telemetryhub/devices/${device.id}/device-health`,
    qos: 0,
    payload: JSON.stringify({
      metadata: {
        eventId: uuidv4(),
        deviceId: device.id,
        eventType: 'DEVICE_HEALTH',
        eventTime: new Date().toISOString(),
        ingestTime: null,
      },
      battery: 50 + Math.random() * 50,
      temperature: 35 + Math.random() * 25,
      signalStrength: randomIntBetween(-100, -30),
      firmwareVersion: '2.1.0',
      errorCode: null,
    }),
  };
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario 1: 정상 운영
// ──────────────────────────────────────────────────────────────────────────────
export function normalOperationFlow() {
  const device = DEVICE_POOL[randomIntBetween(0, DEVICE_POOL.length - 1)];

  // 텔레메트리 80%, 운전이벤트 15%, 헬스체크 5%
  const rand = Math.random();
  let event;
  if (rand < 0.80) {
    event = generateTelemetryEvent(device);
  } else if (rand < 0.95) {
    event = generateDrivingEvent(device);
  } else {
    event = generateDeviceHealthEvent(device);
  }

  const res = http.post(
    `${INGESTION_URL}/ingestion/v1/mqtt/messages`,
    JSON.stringify(event),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /ingestion/v1/mqtt/messages [normal]' },
    }
  );

  const success = check(res, { 'ingestion 200': r => r.status === 200 });
  ingestionErrorRate.add(!success);
  if (success) ingestionSuccess.add(1);
  else ingestionFailure.add(1);

  sleep(randomIntBetween(0, 2));
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario 2: 출퇴근 시간 버스트
// ──────────────────────────────────────────────────────────────────────────────
export function rushHourBurstFlow() {
  // 버스트 시에는 많은 디바이스가 동시에 활성화됨
  const device = DEVICE_POOL[randomIntBetween(0, Math.min(2000, DEVICE_POOL.length - 1))];
  const event = generateTelemetryEvent(device);

  const res = http.post(
    `${INGESTION_URL}/ingestion/v1/mqtt/messages`,
    JSON.stringify(event),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /ingestion/v1/mqtt/messages [burst]' },
    }
  );

  const success = check(res, { 'burst ingestion 200': r => r.status === 200 });
  ingestionErrorRate.add(!success);
  if (success) {
    ingestionSuccess.add(1);
    burstEventsSent.add(1);
  } else {
    ingestionFailure.add(1);
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario 3: 네트워크 복구 — 배치 전송
// ──────────────────────────────────────────────────────────────────────────────
export function networkRecoveryFlow() {
  // 밀린 데이터를 배치로 전송 (5~20개 이벤트)
  const batchSize = randomIntBetween(5, 20);
  const messages = [];

  for (let i = 0; i < batchSize; i++) {
    const device = DEVICE_POOL[randomIntBetween(0, DEVICE_POOL.length - 1)];
    const event = generateTelemetryEvent(device);
    messages.push({
      topic: event.topic,
      qos: event.qos,
      payload: event.payload,
    });
  }

  const start = Date.now();
  const res = http.post(
    `${INGESTION_URL}/ingestion/v1/mqtt/messages/batch`,
    JSON.stringify({ messages }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /ingestion/v1/mqtt/messages/batch [recovery]' },
    }
  );
  batchIngestionDuration.add(Date.now() - start);

  const success = check(res, { 'batch ingestion 200': r => r.status === 200 });
  if (success) {
    ingestionSuccess.add(batchSize);
  } else {
    ingestionFailure.add(batchSize);
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario 4: 대시보드 부하
// ──────────────────────────────────────────────────────────────────────────────
export function dashboardLoadFlow() {
  const now = new Date();
  const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
  const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

  // 대시보드 새로고침: 여러 API를 한번에 호출
  group('dashboard_refresh', () => {
    // 1. 최신 디바이스 상태
    const latestRes = http.get(
      `${ANALYTICS_URL}/analytics/v1/devices/latest?limit=50`,
      { tags: { name: 'GET /analytics/v1/devices/latest [dashboard]' } }
    );
    check(latestRes, { 'latest devices 200': r => r.status === 200 });

    // 2. 분당 이벤트 (1시간)
    const start1 = Date.now();
    const eventsRes = http.get(
      `${ANALYTICS_URL}/analytics/v1/events-per-minute?from=${oneHourAgo.toISOString()}&to=${now.toISOString()}`,
      { tags: { name: 'GET /analytics/v1/events-per-minute [dashboard]' } }
    );
    if (Date.now() - start1 > 500) analyticsSlowQuery.add(1);
    check(eventsRes, { 'events per minute 200': r => r.status === 200 });

    // 3. 운전 이벤트 카운터 (24시간)
    const start2 = Date.now();
    const countersRes = http.get(
      `${ANALYTICS_URL}/analytics/v1/driving-events/counters?from=${oneDayAgo.toISOString()}&to=${now.toISOString()}`,
      { tags: { name: 'GET /analytics/v1/driving-events/counters [dashboard]' } }
    );
    if (Date.now() - start2 > 1000) analyticsSlowQuery.add(1);
    check(countersRes, { 'driving counters 200': r => r.status === 200 });

    // 4. 히트맵 (서울 지역)
    const start3 = Date.now();
    const heatmapRes = http.get(
      `${ANALYTICS_URL}/analytics/v1/heatmap/regions?from=${oneHourAgo.toISOString()}&to=${now.toISOString()}&minLat=37.4&maxLat=37.7&minLon=126.8&maxLon=127.1`,
      { tags: { name: 'GET /analytics/v1/heatmap/regions [dashboard]' } }
    );
    if (Date.now() - start3 > 1500) analyticsSlowQuery.add(1);
    check(heatmapRes, { 'heatmap 200': r => r.status === 200 });
  });

  sleep(randomIntBetween(5, 15));  // 대시보드 새로고침 간격
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario 5: 실시간 모니터링 (짧은 폴링 간격)
// ──────────────────────────────────────────────────────────────────────────────
export function realtimeMonitorFlow() {
  group('realtime_poll', () => {
    // Ingestion 메트릭
    const ingestionRes = http.get(
      `${INGESTION_URL}/ingestion/v1/metrics`,
      { tags: { name: 'GET /ingestion/v1/metrics [monitor]' } }
    );
    check(ingestionRes, { 'ingestion metrics 200': r => r.status === 200 });

    // Stream Processor 메트릭
    const streamRes = http.get(
      `${STREAM_URL}/stream/v1/metrics`,
      { tags: { name: 'GET /stream/v1/metrics [monitor]' } }
    );
    check(streamRes, { 'stream metrics 200': r => r.status === 200 });

    // 최신 이벤트 (데이터 플로우 확인)
    const eventsRes = http.get(
      `${INGESTION_URL}/ingestion/v1/events?limit=10`,
      { tags: { name: 'GET /ingestion/v1/events [monitor]' } }
    );
    check(eventsRes, { 'recent events 200': r => r.status === 200 });
  });

  sleep(2);  // 2초 폴링
}

export function handleSummary(data) {
  const metrics = data.metrics;

  console.log('\n============ Realistic IoT Scenario Summary ============');

  console.log('\n[Ingestion Stats]');
  console.log(`  Success: ${metrics.ingestion_success_total?.values?.count || 0}`);
  console.log(`  Failure: ${metrics.ingestion_failure_total?.values?.count || 0}`);
  console.log(`  Error Rate: ${((metrics.ingestion_error_rate?.values?.rate || 0) * 100).toFixed(2)}%`);
  console.log(`  Burst Events: ${metrics.burst_events_sent_total?.values?.count || 0}`);

  console.log('\n[Response Time by Scenario]');
  console.log(`  Normal Operation p95: ${metrics['http_req_duration{scenario:normal_operation}']?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);
  console.log(`  Rush Hour Burst p95:  ${metrics['http_req_duration{scenario:rush_hour_burst}']?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);
  console.log(`  Network Recovery p95: ${metrics['http_req_duration{scenario:network_recovery}']?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);
  console.log(`  Dashboard Load p95:   ${metrics['http_req_duration{scenario:dashboard_load}']?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);

  console.log('\n[Batch Ingestion]');
  console.log(`  Avg Duration: ${metrics.batch_ingestion_duration?.values?.avg?.toFixed(2) || 'N/A'} ms`);

  console.log('\n[Analytics]');
  console.log(`  Slow Queries (>threshold): ${metrics.analytics_slow_query_total?.values?.count || 0}`);

  console.log('\n==========================================================\n');

  return {};
}
