/**
 * order-service 쓰기 부하 테스트
 *
 * 목적: 주문 생성 API 처리량 측정
 *       - order-service → catalog-service HTTP 재고 예약 경로 부하
 *       - Outbox → Kafka 발행 지연 관찰 (analytics/ranking 서비스 Consumer lag)
 *
 * 사전 조건: catalog-service 데이터 초기화 완료 (상품 재고 필요)
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run /scripts/order-write.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween, uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ORDER_URL   = __ENV.BASE_URL_ORDER   || 'http://localhost:18115';
const CATALOG_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';

export const options = {
  stages: [
    { duration: '30s', target: 10 },  // warm-up
    { duration: '2m',  target: 10 },  // steady
    { duration: '30s', target: 30 },  // 부하 증가
    { duration: '2m',  target: 30 },  // steady
    { duration: '30s', target: 0  },  // ramp-down
  ],
  thresholds: {
    // 재고 예약 HTTP 왕복 포함이므로 여유 있게 설정
    http_req_duration: ['p(95)<3000'],
    http_req_failed:   ['rate<0.05'],
  },
};

export function setup() {
  // 실제 상품 ID 수집 (재고 예약에 유효한 ID 필요)
  const res = http.get(`${CATALOG_URL}/api/products?size=100`);
  if (res.status !== 200) return { products: [] };

  const products = JSON.parse(res.body).filter(p => p.status === 'ACTIVE');
  console.log(`부하 테스트용 상품 ${products.length}개 로드 완료`);
  return { products };
}

export default function ({ products }) {
  if (products.length === 0) {
    console.warn('사용 가능한 상품 없음. 데이터 초기화를 먼저 실행하세요.');
    return;
  }

  // 주문 항목 구성 (1~3개 상품)
  const itemCount = randomIntBetween(1, 3);
  const items = Array.from({ length: itemCount }, () => {
    const product = products[randomIntBetween(0, products.length - 1)];
    return { productId: product.id, quantity: randomIntBetween(1, 5) };
  });

  const res = http.post(
    `${ORDER_URL}/api/orders`,
    JSON.stringify({ memberId: randomIntBetween(1, 1000000), items }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': uuidv4(),  // 중복 주문 방지
      },
    }
  );

  check(res, {
    'order 201': r => r.status === 201,
  });

  sleep(randomIntBetween(1, 2));
}