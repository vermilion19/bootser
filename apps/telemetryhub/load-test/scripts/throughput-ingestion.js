/**
 * Ingestion Service 처리량 한계 테스트
 *
 * 목적: Ingestion Service가 SLO를 유지하면서 최대 몇 RPS까지 처리할 수 있는지 측정
 *
 * 측정 방식:
 *   - ramping-arrival-rate executor: RPS를 직접 제어하여 한계 지점 탐색
 *   - threshold 초과 시 테스트 자동 중단 (abortOnFail: true)
 *   - 중단 직전 단계의 RPS = 처리량 한계
 *
 * SLO:
 *   - p95 < 100ms
 *   - 에러율 < 1%
 *
 * 실행:
 *   docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-ingestion.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const INGESTION_URL = __ENV.BASE_URL_INGESTION || 'http://localhost:8092';

// Custom metrics
const ingestionSuccess = new Counter('ingestion_success_total');
const ingestionFailure = new Counter('ingestion_failure_total');

// 디바이스 및 이벤트 타입 풀
const DEVICE_IDS = Array.from({ length: 1000 }, (_, i) => `device-${String(i + 1).padStart(5, '0')}`);
const EVENT_TYPES = ['TELEMETRY', 'DEVICE_HEALTH', 'DRIVING_EVENT'];
const DRIVING_EVENT_TYPES = ['HARD_BRAKE', 'OVERSPEED', 'CRASH'];

export const options = {
  scenarios: {
    throughput_test: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '30s', target: 50 },    // 워밍업
        { duration: '30s', target: 200 },   // 200 RPS
        { duration: '30s', target: 500 },   // 500 RPS
        { duration: '30s', target: 1000 },  // 1000 RPS
        { duration: '30s', target: 1500 },  // 1500 RPS
        { duration: '30s', target: 2000 },  // 2000 RPS (한계 탐색)
        { duration: '30s', target: 2500 },  // 2500 RPS
        { duration: '20s', target: 50 },    // 쿨다운
      ],
    },
  },
  thresholds: {
    http_req_duration: [
      { threshold: 'p(95)<100', abortOnFail: true, delayAbortEval: '30s' },
    ],
    http_req_failed: [
      { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' },
    ],
  },
};

// 텔레메트리 이벤트 생성
function generateTelemetryPayload(deviceId) {
  return JSON.stringify({
    metadata: {
      eventId: uuidv4(),
      deviceId,
      eventType: 'TELEMETRY',
      eventTime: new Date().toISOString(),
      ingestTime: null,
    },
    lat: 37.5665 + (Math.random() - 0.5) * 0.1,
    lon: 126.9780 + (Math.random() - 0.5) * 0.1,
    speed: Math.random() * 120,
    heading: Math.random() * 360,
    accelX: (Math.random() - 0.5) * 2,
    accelY: (Math.random() - 0.5) * 2,
  });
}

// Device Health 이벤트 생성
function generateDeviceHealthPayload(deviceId) {
  return JSON.stringify({
    metadata: {
      eventId: uuidv4(),
      deviceId,
      eventType: 'DEVICE_HEALTH',
      eventTime: new Date().toISOString(),
      ingestTime: null,
    },
    battery: 50 + Math.random() * 50,
    temperature: 30 + Math.random() * 40,
    signalStrength: randomIntBetween(-100, -30),
    firmwareVersion: '2.1.0',
    errorCode: null,
  });
}

// Driving Event 생성
function generateDrivingEventPayload(deviceId) {
  const drivingEventType = DRIVING_EVENT_TYPES[randomIntBetween(0, DRIVING_EVENT_TYPES.length - 1)];
  return JSON.stringify({
    metadata: {
      eventId: uuidv4(),
      deviceId,
      eventType: 'DRIVING_EVENT',
      eventTime: new Date().toISOString(),
      ingestTime: null,
    },
    type: drivingEventType,
    severity: randomIntBetween(1, 5),
    context: JSON.stringify({
      speed: Math.random() * 120,
      duration: randomIntBetween(100, 5000),
    }),
  });
}

// MQTT 토픽 생성
function generateTopic(deviceId, eventType) {
  const type = eventType.toLowerCase().replace('_', '-');
  return `telemetryhub/devices/${deviceId}/${type}`;
}

export default function () {
  const deviceId = DEVICE_IDS[randomIntBetween(0, DEVICE_IDS.length - 1)];
  const eventTypeIdx = randomIntBetween(0, 2);
  let payload;
  let eventType;

  // 이벤트 타입별 페이로드 생성 (텔레메트리 70%, 헬스 20%, 드라이빙 10%)
  const rand = Math.random();
  if (rand < 0.7) {
    eventType = 'TELEMETRY';
    payload = generateTelemetryPayload(deviceId);
  } else if (rand < 0.9) {
    eventType = 'DEVICE_HEALTH';
    payload = generateDeviceHealthPayload(deviceId);
  } else {
    eventType = 'DRIVING_EVENT';
    payload = generateDrivingEventPayload(deviceId);
  }

  const topic = generateTopic(deviceId, eventType);

  const res = http.post(
    `${INGESTION_URL}/ingestion/v1/mqtt/messages`,
    JSON.stringify({
      topic,
      qos: 0,
      payload,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /ingestion/v1/mqtt/messages' },
    }
  );

  const success = check(res, {
    'ingestion 200': r => r.status === 200,
  });

  if (success) {
    ingestionSuccess.add(1);
  } else {
    ingestionFailure.add(1);
  }
}

export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration?.values?.['p(95)'] || 0;
  const errorRate = data.metrics.http_req_failed?.values?.rate || 0;
  const totalRequests = data.metrics.http_reqs?.values?.count || 0;
  const rps = data.metrics.http_reqs?.values?.rate || 0;

  console.log('\n============ Ingestion Throughput Test Summary ============');
  console.log(`Total Requests: ${totalRequests}`);
  console.log(`Average RPS: ${rps.toFixed(2)}`);
  console.log(`P95 Latency: ${p95.toFixed(2)} ms`);
  console.log(`Error Rate: ${(errorRate * 100).toFixed(2)}%`);
  console.log('=============================================================\n');

  return {};
}
