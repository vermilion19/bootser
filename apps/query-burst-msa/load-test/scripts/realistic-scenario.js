/**
 * 현실적 복합 시나리오 부하 테스트
 *
 * 목적: 실제 커머스 플랫폼에서 발생하는 복잡한 트래픽 패턴 시뮬레이션
 *
 * 시나리오:
 *   1. flash_sale          (arrival-rate): 플래시 세일 → 핫 상품 재고 경합·소진 관찰
 *   2. full_lifecycle      (ramping-vus) : 주문→결제→배송시작→배송완료 전체 흐름
 *   3. browse_then_abandon (ramping-vus) : 탐색→주문→30% 취소 (재고 반환 경합)
 *   4. analytics_dashboard (ramping-vus) : 관리자 통계 대시보드 조회 (DB 집계 부하)
 *   5. cursor_pagination   (ramping-vus) : 주문/멤버 목록 커서 페이지네이션
 *
 * 측정 포인트:
 *   - flash sale 구간의 재고 충돌 비율 (stock_exhausted_total)
 *   - 전체 라이프사이클 완료 수 (lifecycle_completed_total)
 *   - 주문 취소 → 재고 반환 경합 (order_cancelled_total)
 *   - analytics 500ms 초과 slow query (analytics_slow_query_total)
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run /scripts/realistic-scenario.js
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const CATALOG_URL   = __ENV.BASE_URL_CATALOG   || 'http://localhost:18113';
const ORDER_URL     = __ENV.BASE_URL_ORDER     || 'http://localhost:18115';
const RANKING_URL   = __ENV.BASE_URL_RANKING   || 'http://localhost:18112';
const MEMBER_URL    = __ENV.BASE_URL_MEMBER    || 'http://localhost:18114';
const ANALYTICS_URL = __ENV.BASE_URL_ANALYTICS || 'http://localhost:18111';

// ── Custom Metrics ──────────────────────────────────────────────────────────
const stockExhausted     = new Counter('stock_exhausted_total');       // 재고 소진 응답 (409/400) 수
const lifecycleCompleted = new Counter('lifecycle_completed_total');   // 주문 전체 완료 수
const orderCancelled     = new Counter('order_cancelled_total');       // 취소 완료 수
const analyticsSlowQuery = new Counter('analytics_slow_query_total'); // 500ms 초과 analytics 쿼리
const flashSaleConflict  = new Rate('flash_sale_conflict_rate');       // 재고 충돌 비율

export const options = {
  scenarios: {

    // ── 1. 플래시 세일: arrival-rate로 갑작스런 트래픽 폭발 ─────────────
    //    핵심 관찰: 재고 소진 시 409 비율, 랭킹 서비스 지연 변화
    flash_sale: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 600,
      stages: [
        { duration: '30s', target: 5   },  // 사전 워밍업
        { duration: '10s', target: 300 },  // 세일 오픈 → 트래픽 폭발
        { duration: '90s', target: 300 },  // 세일 진행 (재고 소진 관찰)
        { duration: '20s', target: 5   },  // 세일 종료
      ],
      exec: 'flashSaleFlow',
      tags: { scenario: 'flash_sale' },
    },

    // ── 2. 전체 주문 라이프사이클: 생성→결제→배송시작→배송완료 ──────────
    //    핵심 관찰: 각 상태 전이 API 응답시간, Outbox 이벤트 Kafka lag
    full_lifecycle: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '3m',  target: 20 },
        { duration: '30s', target: 50 },
        { duration: '2m',  target: 50 },
        { duration: '30s', target: 0  },
      ],
      exec: 'fullLifecycleFlow',
      tags: { scenario: 'full_lifecycle' },
      startTime: '30s',  // flash_sale 워밍업 이후 시작
    },

    // ── 3. 탐색 후 구매 → 30% 취소 ────────────────────────────────────
    //    핵심 관찰: 주문 취소 시 재고 반환 경합, order-service 처리량
    browse_then_abandon: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 80  },
        { duration: '3m',  target: 80  },
        { duration: '30s', target: 0   },
      ],
      exec: 'browseThenAbandonFlow',
      tags: { scenario: 'browse_then_abandon' },
      startTime: '20s',
    },

    // ── 4. 관리자 통계 대시보드 (analytics-service DB 집계) ───────────
    //    핵심 관찰: 날짜 범위가 넓을수록 쿼리 시간 증가, slow query 비율
    analytics_dashboard: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '4m',  target: 10 },
        { duration: '30s', target: 0  },
      ],
      exec: 'analyticsDashboardFlow',
      tags: { scenario: 'analytics_dashboard' },
    },

    // ── 5. 커서 기반 페이지네이션 ────────────────────────────────────
    //    핵심 관찰: 커서 깊이에 따른 응답시간 변화, DB 인덱스 효율
    cursor_pagination: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '4m',  target: 50 },
        { duration: '30s', target: 0  },
      ],
      exec: 'cursorPaginationFlow',
      tags: { scenario: 'cursor_pagination' },
    },
  },

  thresholds: {
    // 시나리오별 응답시간 SLO
    'http_req_duration{scenario:flash_sale}':          ['p(95)<2000'],
    'http_req_duration{scenario:full_lifecycle}':      ['p(95)<5000'],
    'http_req_duration{scenario:browse_then_abandon}': ['p(95)<3000'],
    'http_req_duration{scenario:analytics_dashboard}': ['p(95)<1500'],
    'http_req_duration{scenario:cursor_pagination}':   ['p(95)<500'],

    // 전체 오류율 (재고 소진 409는 flash_sale_conflict_rate로 별도 추적)
    http_req_failed: ['rate<0.10'],

    // 재고 충돌은 60% 이하여야 재고가 충분히 남아있는 것으로 간주
    flash_sale_conflict_rate: ['rate<0.60'],
  },
};

// ── Setup: 공통 데이터 수집 ─────────────────────────────────────────────────
export function setup() {
  const productsRes = http.get(`${CATALOG_URL}/api/products?status=ACTIVE&size=100`);
  if (productsRes.status !== 200) {
    throw new Error(`상품 조회 실패: status=${productsRes.status}`);
  }
  const products = JSON.parse(productsRes.body);
  if (products.length === 0) {
    throw new Error('ACTIVE 상품 없음. catalog data-init 먼저 실행하세요.');
  }

  const categoriesRes = http.get(`${CATALOG_URL}/api/categories`);
  const categories = categoriesRes.status === 200 ? JSON.parse(categoriesRes.body) : [];

  // flash sale 대상: 앞 10개를 핫 아이템으로 지정 (집중 경쟁 유발)
  const hotProducts = products.slice(0, Math.min(10, products.length));

  console.log(
    `setup 완료 — 상품: ${products.length}개, 카테고리: ${categories.length}개, 핫상품: ${hotProducts.length}개`
  );
  return { products, hotProducts, categories };
}

// ── Scenario 1: 플래시 세일 ─────────────────────────────────────────────────
//  - 핫 상품 80% / 일반 상품 20% 비율로 집중 주문
//  - 재고 소진 시 409/400 → stock_exhausted_total 카운터 증가
//  - 주문 성공 직후 실시간 랭킹 조회 (실제 유저 행동 반영)
export function flashSaleFlow({ hotProducts, products }) {
  const isHotItem = Math.random() < 0.8;
  const pool = isHotItem ? hotProducts : products;
  const product = pool[randomIntBetween(0, pool.length - 1)];

  const orderRes = http.post(
    `${ORDER_URL}/api/orders`,
    JSON.stringify({
      memberId: randomIntBetween(1, 100000),
      items: [{ productId: product.id, quantity: randomIntBetween(1, 2), unitPrice: product.price }],
    }),
    {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
      tags: { name: 'POST /api/orders [flash-sale]' },
    }
  );

  const success = orderRes.status === 201;
  const stockOut = orderRes.status === 409 || orderRes.status === 400;

  // 재고 소진도 정상 비즈니스 응답 — 오류로 처리하지 않음
  check(orderRes, {
    'flash-sale: 201 또는 재고소진(409/400)': r =>
      r.status === 201 || r.status === 409 || r.status === 400,
  });

  if (stockOut) {
    stockExhausted.add(1);
    flashSaleConflict.add(true);
  } else {
    flashSaleConflict.add(false);
  }

  // 주문 직후 실시간 랭킹 확인 (성공한 경우만 — 실제 유저 행동 패턴)
  if (success) {
    const rankRes = http.get(`${RANKING_URL}/api/rankings/realtime?windowHours=1&size=10`, {
      tags: { name: 'GET /api/rankings/realtime [flash-sale]' },
    });
    check(rankRes, { 'ranking 200': r => r.status === 200 });
  }

  sleep(randomIntBetween(0, 1));
}

// ── Scenario 2: 전체 주문 라이프사이클 ────────────────────────────────────
//  - 주문 생성 → 결제 → 배송시작 → 배송완료 → 최종 상태 확인
//  - complex-flow.js 대비 ship/deliver 추가
//  - 각 단계 실패 시 다음 단계 스킵 (현실적인 에러 전파)
export function fullLifecycleFlow({ products }) {
  if (products.length === 0) return;

  let orderId;
  const product = products[randomIntBetween(0, products.length - 1)];

  group('1_주문생성', () => {
    const res = http.post(
      `${ORDER_URL}/api/orders`,
      JSON.stringify({
        memberId: randomIntBetween(1, 1000000),
        items: [{ productId: product.id, quantity: randomIntBetween(1, 2), unitPrice: product.price }],
      }),
      {
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
        tags: { name: 'POST /api/orders [lifecycle]' },
      }
    );
    if (check(res, { 'create 201': r => r.status === 201 })) {
      orderId = JSON.parse(res.body).orderId;
    }
    sleep(1);
  });

  if (!orderId) return;

  group('2_결제처리', () => {
    const res = http.patch(`${ORDER_URL}/api/orders/${orderId}/pay`, null, {
      tags: { name: 'PATCH /api/orders/:id/pay' },
    });
    if (!check(res, { 'pay 204': r => r.status === 204 })) orderId = null;
    sleep(1);
  });

  if (!orderId) return;

  group('3_배송시작', () => {
    const res = http.patch(`${ORDER_URL}/api/orders/${orderId}/ship`, null, {
      tags: { name: 'PATCH /api/orders/:id/ship' },
    });
    if (!check(res, { 'ship 204': r => r.status === 204 })) orderId = null;
    sleep(randomIntBetween(1, 2));
  });

  if (!orderId) return;

  group('4_배송완료', () => {
    const res = http.patch(`${ORDER_URL}/api/orders/${orderId}/deliver`, null, {
      tags: { name: 'PATCH /api/orders/:id/deliver' },
    });
    if (!check(res, { 'deliver 204': r => r.status === 204 })) orderId = null;
    sleep(1);
  });

  if (!orderId) return;

  group('5_최종상태확인', () => {
    const res = http.get(`${ORDER_URL}/api/orders/${orderId}`, {
      tags: { name: 'GET /api/orders/:id [lifecycle]' },
    });
    if (check(res, { 'final detail 200': r => r.status === 200 })) {
      lifecycleCompleted.add(1);
    }
  });
}

// ── Scenario 3: 탐색 후 구매 → 일부 취소 ────────────────────────────────
//  - 랭킹 → 카탈로그 탐색 → 주문 생성 → 30% 확률로 취소
//  - 취소 시 재고 반환 경합 발생, 재고가 반환되면 다음 flash_sale에서 주문 가능
export function browseThenAbandonFlow({ products, categories }) {
  let orderId;

  group('1_랭킹탐색', () => {
    const windowHours = [1, 6, 24][randomIntBetween(0, 2)];
    const res = http.get(
      `${RANKING_URL}/api/rankings/realtime?windowHours=${windowHours}&size=${randomIntBetween(5, 20)}`,
      { tags: { name: 'GET /api/rankings/realtime [browse]' } }
    );
    check(res, { 'ranking 200': r => r.status === 200 });
    sleep(randomIntBetween(1, 2));
  });

  group('2_카탈로그탐색', () => {
    // 60% 확률로 카테고리 필터, 나머지는 전체 목록
    let url = `${CATALOG_URL}/api/products?size=${randomIntBetween(10, 30)}`;
    if (categories.length > 0 && Math.random() < 0.6) {
      const cat = categories[randomIntBetween(0, categories.length - 1)];
      url += `&categoryId=${cat.id}`;
    }
    const res = http.get(url, { tags: { name: 'GET /api/products [browse]' } });
    check(res, { 'products 200': r => r.status === 200 });
    sleep(randomIntBetween(2, 4));
  });

  group('3_주문생성', () => {
    const product = products[randomIntBetween(0, products.length - 1)];
    const itemCount = randomIntBetween(1, 3);
    const items = Array.from({ length: itemCount }, () => ({
      productId: product.id,
      quantity: randomIntBetween(1, 2),
      unitPrice: product.price,
    }));

    const res = http.post(
      `${ORDER_URL}/api/orders`,
      JSON.stringify({ memberId: randomIntBetween(1, 1000000), items }),
      {
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': uuidv4() },
        tags: { name: 'POST /api/orders [browse]' },
      }
    );
    if (check(res, { 'order 201': r => r.status === 201 })) {
      orderId = JSON.parse(res.body).orderId;
    }
    sleep(1);
  });

  if (!orderId) return;

  // 30% 확률로 취소 (마음 바뀜 / 결제 이탈)
  if (Math.random() < 0.30) {
    group('4a_주문취소', () => {
      const res = http.del(`${ORDER_URL}/api/orders/${orderId}`, null, {
        tags: { name: 'DELETE /api/orders/:id' },
      });
      if (check(res, { 'cancel 204': r => r.status === 204 })) {
        orderCancelled.add(1);
      }
      sleep(1);
    });
  } else {
    group('4b_결제진행', () => {
      const res = http.patch(`${ORDER_URL}/api/orders/${orderId}/pay`, null, {
        tags: { name: 'PATCH /api/orders/:id/pay [browse]' },
      });
      check(res, { 'pay 204': r => r.status === 204 });
      sleep(1);
    });
  }
}

// ── Scenario 4: 관리자 통계 대시보드 ─────────────────────────────────────
//  - analytics-service의 DB 집계 쿼리에 지속적 부하
//  - 날짜 범위를 7/30/90일로 랜덤 선택 → 범위에 따른 쿼리 비용 차이 관찰
//  - 500ms 초과 응답은 analytics_slow_query_total로 별도 추적
export function analyticsDashboardFlow() {
  const today = new Date();
  const fmt = d => d.toISOString().slice(0, 10);

  const days = [7, 30, 90][randomIntBetween(0, 2)];
  const from = new Date(today);
  from.setDate(from.getDate() - days);
  const fromStr = fmt(from);
  const toStr = fmt(today);

  group('1_일별매출요약', () => {
    const start = Date.now();
    const res = http.get(
      `${ANALYTICS_URL}/api/statistics/daily-sales?from=${fromStr}&to=${toStr}`,
      { tags: { name: 'GET /api/statistics/daily-sales' } }
    );
    if (Date.now() - start > 500) analyticsSlowQuery.add(1);
    check(res, { 'daily-sales 200': r => r.status === 200 });
    sleep(1);
  });

  group('2_오늘인기상품TOP', () => {
    const start = Date.now();
    const res = http.get(
      `${ANALYTICS_URL}/api/statistics/top-products?date=${toStr}`,
      { tags: { name: 'GET /api/statistics/top-products' } }
    );
    if (Date.now() - start > 500) analyticsSlowQuery.add(1);
    check(res, { 'top-products 200': r => r.status === 200 });
    sleep(randomIntBetween(1, 2));
  });

  group('3_상품별트렌드', () => {
    const productId = randomIntBetween(1, 50);
    const start = Date.now();
    const res = http.get(
      `${ANALYTICS_URL}/api/statistics/products/${productId}/trend?from=${fromStr}&to=${toStr}`,
      { tags: { name: 'GET /api/statistics/products/:id/trend' } }
    );
    if (Date.now() - start > 500) analyticsSlowQuery.add(1);
    // 데이터 없는 상품은 404 — 정상 응답으로 처리
    check(res, { 'trend 200 or 404': r => r.status === 200 || r.status === 404 });
    sleep(randomIntBetween(2, 3));
  });
}

// ── Scenario 5: 커서 기반 페이지네이션 ──────────────────────────────────
//  - 주문 목록을 커서로 최대 5페이지 스크롤 (실제 앱 사용 패턴)
//  - 커서 깊이가 깊어질수록 DB 인덱스 효율 변화를 Grafana에서 관찰
//  - 멤버 목록도 함께 조회
export function cursorPaginationFlow() {
  group('주문목록_커서페이징', () => {
    let cursor = null;
    const pageSize = randomIntBetween(10, 20);
    const maxPages = randomIntBetween(2, 5);

    for (let page = 0; page < maxPages; page++) {
      const url = cursor
        ? `${ORDER_URL}/api/orders?cursor=${cursor}&size=${pageSize}`
        : `${ORDER_URL}/api/orders?size=${pageSize}`;

      const res = http.get(url, { tags: { name: 'GET /api/orders [cursor-page]' } });
      if (!check(res, { 'orders page 200': r => r.status === 200 })) break;

      const body = JSON.parse(res.body);
      if (!Array.isArray(body) || body.length === 0) break;

      // 마지막 항목 ID를 다음 커서로 사용
      cursor = body[body.length - 1].orderId;
      sleep(randomIntBetween(1, 2));
    }
  });

  group('멤버목록_커서페이징', () => {
    let cursor = null;
    const pageSize = randomIntBetween(10, 30);

    // 멤버는 최대 3페이지
    for (let page = 0; page < 3; page++) {
      const url = cursor
        ? `${MEMBER_URL}/api/members?cursor=${cursor}&size=${pageSize}`
        : `${MEMBER_URL}/api/members?size=${pageSize}`;

      const res = http.get(url, { tags: { name: 'GET /api/members [cursor-page]' } });
      if (!check(res, { 'members 200': r => r.status === 200 })) break;

      const body = JSON.parse(res.body);
      if (!Array.isArray(body) || body.length === 0) break;

      cursor = body[body.length - 1].memberId ?? body[body.length - 1].id;
      sleep(1);
    }
  });
}
