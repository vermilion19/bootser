/**
 * 복합 시나리오 부하 테스트
 *
 * 목적: 실제 사용자 행동 패턴을 시뮬레이션하여 전체 시스템 부하 측정
 *
 * 시나리오:
 *   - browse_flow  (70% 트래픽): 카테고리 조회 + 랭킹 조회
 *   - order_flow   (30% 트래픽): 주문생성 → 결제 → 주문상세 조회
 *   - 기존 대비 약 20~25% 더 높은 동시 사용자 부하
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run /scripts/complex-flow.js
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const CATALOG_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';
const ORDER_URL   = __ENV.BASE_URL_ORDER   || 'http://localhost:18115';
const RANKING_URL = __ENV.BASE_URL_RANKING || 'http://localhost:18112';

const orderDetail404 = new Counter('order_detail_404');
const orderDetail5xx = new Counter('order_detail_5xx');
const orderDetailOther = new Counter('order_detail_other');

export const options = {
  scenarios: {
    // 탐색 유저 (읽기 위주)
    browse_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 1700 },
        { duration: '4m',  target: 1700 },
        { duration: '30s', target: 0  },
      ],
      exec: 'browseFlow',
    },
    // 구매 유저 (쓰기 포함)
    order_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 750 },
        { duration: '4m',  target: 750 },
        { duration: '30s', target: 0  },
      ],
      exec: 'orderFlow',
    },
  },
  thresholds: {
    'http_req_duration{scenario:browse_flow}': ['p(95)<500'],
    'http_req_duration{scenario:order_flow}':  ['p(95)<3000'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  const res = http.get(`${CATALOG_URL}/api/products?status=ACTIVE&size=100`, {
    tags: { name: 'GET /api/products?status=ACTIVE' },
  });
  if (res.status !== 200) {
    throw new Error(`상품 조회 실패: status=${res.status}`);
  }

  const products = JSON.parse(res.body);
  if (products.length === 0) {
    throw new Error('주문 가능한 ACTIVE 상품이 없습니다. catalog data-init 또는 재고 상태를 먼저 확인하세요.');
  }

  console.log(`복합 시나리오: 주문 가능한 ACTIVE 상품 ${products.length}개 로드 완료`);
  return { products };
}

// 시나리오 1: 카탈로그 탐색 + 랭킹 조회
export function browseFlow({ products }) {
  group('카테고리 조회', () => {
    const res = http.get(`${CATALOG_URL}/api/categories`, {
      tags: { name: 'GET /api/categories' },
    });
    check(res, { 'categories 200': r => r.status === 200 });
    sleep(1);
  });

  group('실시간 랭킹 조회', () => {
    const res = http.get(`${RANKING_URL}/api/rankings/realtime?windowHours=24&size=10`, {
      tags: { name: 'GET /api/rankings/realtime' },
    });
    check(res, { 'ranking 200': r => r.status === 200 });
    sleep(2);
  });
}

// 시나리오 2: 주문 생성 → 결제 → 주문 상세 조회
export function orderFlow({ products }) {
  if (products.length === 0) return;

  let orderId;

  group('주문 생성', () => {
    const product = products[randomIntBetween(0, products.length - 1)];
    const res = http.post(
      `${ORDER_URL}/api/orders`,
      JSON.stringify({
        memberId: randomIntBetween(1, 1000000),
        items: [{ productId: product.id, quantity: randomIntBetween(1, 3), unitPrice: product.price }],
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': uuidv4(),
        },
        tags: { name: 'POST /api/orders' },
      }
    );

    if (check(res, { 'order 201': r => r.status === 201 })) {
      orderId = JSON.parse(res.body).orderId;
    }
    sleep(1);
  });

  if (!orderId) return;

  group('결제 처리', () => {
    const res = http.patch(`${ORDER_URL}/api/orders/${orderId}/pay`, null, {
      tags: { name: 'PATCH /api/orders/:orderId/pay' },
    });
    check(res, { 'pay 204': r => r.status === 204 });
    sleep(1);
  });

  group('주문 상세 조회', () => {
    const res = http.get(`${ORDER_URL}/api/orders/${orderId}`, {
      tags: { name: 'GET /api/orders/:orderId' },
    });

    if (res.status === 404) {
      orderDetail404.add(1);
    } else if (res.status >= 500) {
      orderDetail5xx.add(1);
    } else if (res.status !== 200) {
      orderDetailOther.add(1);
    }

    check(res, { 'order detail 200': r => r.status === 200 });
    sleep(1);
  });
}
