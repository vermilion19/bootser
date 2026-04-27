/**
 * Analytics API 처리량 및 쿼리 성능 테스트
 *
 * 목적: Analytics API의 다양한 집계 쿼리가 시간 범위별로 SLO 내에 응답하는지 검증
 *
 * 측정 포인트:
 *   - 최신 디바이스 조회 성능
 *   - 분당 이벤트 집계 (시간 범위별)
 *   - 운전 이벤트 카운터 집계
 *   - 히트맵 조회 성능
 *
 * SLO:
 *   - 1시간 범위: p95 < 200ms
 *   - 24시간 범위: p95 < 500ms
 *   - 7일 범위: p95 < 2000ms
 *
 * 실행:
 *   docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-analytics.js
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ANALYTICS_URL = __ENV.BASE_URL_ANALYTICS || 'http://localhost:8094';

// Custom metrics - 쿼리 유형별 응답시간
const latestDevicesDuration = new Trend('analytics_latest_devices_duration', true);
const eventsPerMinuteDuration = new Trend('analytics_events_per_minute_duration', true);
const drivingCountersDuration = new Trend('analytics_driving_counters_duration', true);
const heatmapDuration = new Trend('analytics_heatmap_duration', true);
const slowQueries = new Counter('analytics_slow_queries_total');

// 시간 범위별 느린 쿼리 카운터
const slowQuery1h = new Counter('analytics_slow_query_1h_total');
const slowQuery24h = new Counter('analytics_slow_query_24h_total');
const slowQuery7d = new Counter('analytics_slow_query_7d_total');

export const options = {
  scenarios: {
    // 읽기 부하: VU 기반으로 다양한 쿼리 혼합
    mixed_queries: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },   // 워밍업
        { duration: '1m', target: 50 },    // 50 VU
        { duration: '2m', target: 100 },   // 100 VU 유지
        { duration: '1m', target: 150 },   // 스파이크
        { duration: '1m', target: 50 },    // 복구
        { duration: '30s', target: 0 },    // 쿨다운
      ],
    },
  },
  thresholds: {
    // 전체 응답시간 SLO
    'http_req_duration': ['p(95)<2000'],
    'http_req_failed': ['rate<0.01'],

    // 쿼리 유형별 SLO
    'analytics_latest_devices_duration': ['p(95)<200'],
    'analytics_events_per_minute_duration': ['p(95)<1000'],
    'analytics_driving_counters_duration': ['p(95)<1000'],
    'analytics_heatmap_duration': ['p(95)<1500'],
  },
};

// ISO 날짜 포맷
function formatISODate(date) {
  return date.toISOString();
}

// 시간 범위 생성
function getTimeRange(hours) {
  const now = new Date();
  const from = new Date(now.getTime() - hours * 60 * 60 * 1000);
  return { from: formatISODate(from), to: formatISODate(now) };
}

export default function () {
  // 쿼리 유형 가중치: 최신디바이스 30%, 분당이벤트 30%, 드라이빙 20%, 히트맵 20%
  const rand = Math.random();

  if (rand < 0.3) {
    queryLatestDevices();
  } else if (rand < 0.6) {
    queryEventsPerMinute();
  } else if (rand < 0.8) {
    queryDrivingEventCounters();
  } else {
    queryRegionHeatmap();
  }

  sleep(randomIntBetween(1, 3));
}

function queryLatestDevices() {
  group('Latest Devices', () => {
    const limit = [10, 20, 50, 100][randomIntBetween(0, 3)];
    const start = Date.now();

    const res = http.get(
      `${ANALYTICS_URL}/analytics/v1/devices/latest?limit=${limit}`,
      { tags: { name: 'GET /analytics/v1/devices/latest' } }
    );

    const duration = Date.now() - start;
    latestDevicesDuration.add(duration);

    check(res, { 'latest devices 200': r => r.status === 200 });

    if (duration > 200) slowQueries.add(1);
  });
}

function queryEventsPerMinute() {
  group('Events Per Minute', () => {
    // 시간 범위: 1시간, 6시간, 24시간, 7일 중 랜덤
    const hoursOptions = [1, 6, 24, 168];
    const hours = hoursOptions[randomIntBetween(0, 3)];
    const { from, to } = getTimeRange(hours);

    const start = Date.now();

    // 이벤트 타입 필터 (50% 확률로 적용)
    let url = `${ANALYTICS_URL}/analytics/v1/events-per-minute?from=${from}&to=${to}`;
    if (Math.random() < 0.5) {
      const eventTypes = ['TELEMETRY', 'DEVICE_HEALTH', 'DRIVING_EVENT'];
      url += `&eventType=${eventTypes[randomIntBetween(0, 2)]}`;
    }

    const res = http.get(url, {
      tags: { name: `GET /analytics/v1/events-per-minute [${hours}h]` },
    });

    const duration = Date.now() - start;
    eventsPerMinuteDuration.add(duration);

    check(res, { 'events per minute 200': r => r.status === 200 });

    // 시간 범위별 slow query 추적
    if (hours <= 1 && duration > 200) slowQuery1h.add(1);
    if (hours <= 24 && duration > 500) slowQuery24h.add(1);
    if (duration > 2000) slowQuery7d.add(1);
  });
}

function queryDrivingEventCounters() {
  group('Driving Event Counters', () => {
    const hoursOptions = [1, 6, 24, 168];
    const hours = hoursOptions[randomIntBetween(0, 3)];
    const { from, to } = getTimeRange(hours);

    const start = Date.now();

    // 필터 조합 (디바이스ID, 이벤트 타입)
    let url = `${ANALYTICS_URL}/analytics/v1/driving-events/counters?from=${from}&to=${to}`;

    if (Math.random() < 0.3) {
      url += `&deviceId=device-${String(randomIntBetween(1, 100)).padStart(5, '0')}`;
    }
    if (Math.random() < 0.5) {
      const types = ['HARD_BRAKE', 'OVERSPEED', 'CRASH'];
      url += `&drivingEventType=${types[randomIntBetween(0, 2)]}`;
    }

    const res = http.get(url, {
      tags: { name: `GET /analytics/v1/driving-events/counters [${hours}h]` },
    });

    const duration = Date.now() - start;
    drivingCountersDuration.add(duration);

    check(res, { 'driving counters 200': r => r.status === 200 });

    if (duration > 1000) slowQueries.add(1);
  });
}

function queryRegionHeatmap() {
  group('Region Heatmap', () => {
    const hoursOptions = [1, 6, 24];
    const hours = hoursOptions[randomIntBetween(0, 2)];
    const { from, to } = getTimeRange(hours);

    const start = Date.now();

    // 서울 지역 범위 (랜덤하게 작은 영역 선택)
    const baseLat = 37.5665;
    const baseLon = 126.9780;
    const range = 0.05 + Math.random() * 0.1;  // 0.05 ~ 0.15도 범위

    let url = `${ANALYTICS_URL}/analytics/v1/heatmap/regions?from=${from}&to=${to}`;
    url += `&minLat=${(baseLat - range).toFixed(4)}`;
    url += `&maxLat=${(baseLat + range).toFixed(4)}`;
    url += `&minLon=${(baseLon - range).toFixed(4)}`;
    url += `&maxLon=${(baseLon + range).toFixed(4)}`;

    const res = http.get(url, {
      tags: { name: `GET /analytics/v1/heatmap/regions [${hours}h]` },
    });

    const duration = Date.now() - start;
    heatmapDuration.add(duration);

    check(res, { 'heatmap 200': r => r.status === 200 });

    if (duration > 1500) slowQueries.add(1);
  });
}

export function handleSummary(data) {
  const metrics = data.metrics;

  console.log('\n============ Analytics API Performance Summary ============');
  console.log('\n[Query Type Performance]');
  console.log(`  Latest Devices   p95: ${metrics.analytics_latest_devices_duration?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);
  console.log(`  Events/Min       p95: ${metrics.analytics_events_per_minute_duration?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);
  console.log(`  Driving Counters p95: ${metrics.analytics_driving_counters_duration?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);
  console.log(`  Heatmap          p95: ${metrics.analytics_heatmap_duration?.values?.['p(95)']?.toFixed(2) || 'N/A'} ms`);

  console.log('\n[Slow Queries by Time Range]');
  console.log(`  1h range (>200ms):  ${metrics.analytics_slow_query_1h_total?.values?.count || 0}`);
  console.log(`  24h range (>500ms): ${metrics.analytics_slow_query_24h_total?.values?.count || 0}`);
  console.log(`  7d range (>2s):     ${metrics.analytics_slow_query_7d_total?.values?.count || 0}`);

  console.log('\n=============================================================\n');

  return {};
}
