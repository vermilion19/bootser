/**
 * [동시성 검증 3] 결제-취소 경합 (Race Condition) 테스트
 *
 * 검증하려는 것:
 *   같은 주문에 pay와 cancel이 동시에 들어올 때 최종 상태가 일관성 있는가?
 *
 * 코드 분석:
 *   OrderApplicationService.pay() / cancel() 모두:
 *     orderRepository.findWithItemsById(orderId).ifPresent(order -> { ... })
 *     → 비관적 락(SELECT FOR UPDATE) 없음
 *     → @Version 낙관적 락도 없음
 *     → 두 트랜잭션이 동시에 같은 row를 읽고 수정 → Last-Write-Wins
 *
 *   위험한 시나리오:
 *     TX1(pay):    read(STOCK_RESERVED) → catalog.commit() → order.pay() → commit
 *     TX2(cancel): read(STOCK_RESERVED) → catalog.release() → order.cancel() → commit
 *     결과: order=CANCELED, reservation=COMMITTED (재고 차감되었지만 주문 취소됨)
 *     또는: order=PAID, reservation=RELEASED (재고 반환되었지만 주문 결제됨)
 *
 * 합격 기준:
 *   pay + cancel이 동시에 204를 반환하는 경우 없음   ← 둘 다 성공하면 반드시 하나는 버그
 *   최종 주문 상태가 PAID 또는 CANCELED 중 하나만 존재
 *
 * 주의:
 *   - 이 테스트는 경합 조건을 "재현"하는 것이라 실행마다 결과가 다를 수 있음
 *   - 일관성 위반이 발견되면 OrderEntity에 @Version 추가 검토 필요
 *   - 테스트 후 반드시 DB 상태 수동 확인 필요 (아래 SQL 참고)
 *
 * 실행:
 *   docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
 *     run --rm k6 run --out experimental-prometheus-rw /scripts/concurrency-pay-cancel.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ORDER_URL   = __ENV.BASE_URL_ORDER   || 'http://localhost:18115';
const CATALOG_URL = __ENV.BASE_URL_CATALOG || 'http://localhost:18113';

// pay와 cancel 모두 204를 반환한 경우 — 반드시 하나는 불일치 상태
const bothSucceededCount = new Counter('race_both_succeeded_count');
// 예상치 못한 에러 카운터
const unexpectedErrorCount = new Counter('race_unexpected_error_count');

const RACE_PAIRS = 50;  // 동시에 경합시킬 주문 쌍 수

export const options = {
  scenarios: {
    pay_cancel_race: {
      // per-vu-iterations: VU마다 정확히 1번 실행 → 각 VU가 하나의 orderId를 담당
      executor: 'per-vu-iterations',
      vus: RACE_PAIRS,
      iterations: 1,
      maxDuration: '3m',
    },
  },
  thresholds: {
    // pay와 cancel이 동시에 성공하는 경우가 없어야 함
    race_both_succeeded_count: ['count==0'],
  },
};

export function setup() {
  const products = JSON.parse(
    http.get(`${CATALOG_URL}/api/products?status=ACTIVE&size=100`).body
  );
  if (products.length === 0) throw new Error('ACTIVE 상품 없음');

  console.log(`[pay-cancel] ${RACE_PAIRS}개 주문 사전 생성 중...`);

  // RACE_PAIRS개의 주문을 미리 생성
  const orderIds = [];
  for (let i = 0; i < RACE_PAIRS; i++) {
    const product = products[i % products.length];
    const res = http.post(
      `${ORDER_URL}/api/orders`,
      JSON.stringify({
        memberId: 800000 + i,
        items: [{ productId: product.id, quantity: 1 }],
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': uuidv4(),
        },
      }
    );

    if (res.status === 201) {
      const body = JSON.parse(res.body);
      if (body.status === 'STOCK_RESERVED') {
        orderIds.push(body.orderId);
      }
    }
  }

  console.log(`[pay-cancel] 주문 생성 완료: ${orderIds.length}건 (STOCK_RESERVED 상태)`);
  if (orderIds.length < RACE_PAIRS) {
    console.warn(`[pay-cancel] 주문 생성 부족: ${RACE_PAIRS - orderIds.length}건 실패 (재고 확인 필요)`);
  }

  return { orderIds };
}

export default function ({ orderIds }) {
  // 각 VU는 __VU(1-indexed)를 인덱스로 사용하여 전담 orderId를 가짐
  const idx = (__VU - 1) % orderIds.length;
  const orderId = orderIds[idx];

  if (!orderId) return;

  // ─── 핵심: pay와 cancel을 http.batch()로 동시에 발사 ───
  const [payRes, cancelRes] = http.batch([
    {
      method: 'PATCH',
      url: `${ORDER_URL}/api/orders/${orderId}/pay`,
      params: { tags: { name: 'PATCH /api/orders/:id/pay [race]' } },
    },
    {
      method: 'DELETE',
      url: `${ORDER_URL}/api/orders/${orderId}`,
      params: { tags: { name: 'DELETE /api/orders/:id [race]' } },
    },
  ]);

  const payOk    = payRes.status === 204;
  const cancelOk = cancelRes.status === 204;

  if (payOk && cancelOk) {
    // 둘 다 성공 = 경합 발생 → 최종 상태가 불일치일 가능성 높음
    bothSucceededCount.add(1);
    console.warn(
      `[race] orderId=${orderId} → pay(204) + cancel(204) 동시 성공 — 불일치 상태 가능성`
    );
  } else if (!payOk && !cancelOk) {
    // 둘 다 실패 — 예상치 못한 상황
    if (payRes.status >= 500 || cancelRes.status >= 500) {
      unexpectedErrorCount.add(1);
    }
  }

  // 경합 후 최종 상태 확인
  const finalRes = http.get(`${ORDER_URL}/api/orders/${orderId}`, {
    tags: { name: 'GET /api/orders/:id [race-verify]' },
  });

  if (finalRes.status === 200) {
    const finalOrder = JSON.parse(finalRes.body);
    const finalStatus = finalOrder.status;

    // 최종 상태는 PAID 또는 CANCELED 중 하나여야 함
    check(finalRes, {
      '최종 상태가 PAID 또는 CANCELED': _ =>
        finalStatus === 'PAID' || finalStatus === 'CANCELED',
    });

    // 둘 다 204인데 최종 상태가 PAID인 경우: cancel이 catalog release는 했지만 order는 PAID → 심각
    // 둘 다 204인데 최종 상태가 CANCELED인 경우: pay가 catalog commit은 했지만 order는 CANCELED → 심각
    if (payOk && cancelOk) {
      console.warn(
        `[race] orderId=${orderId} 최종상태=${finalStatus} | pay=${payRes.status} cancel=${cancelRes.status}`
      );
    }
  }
}

export function handleSummary(data) {
  const bothSucceeded   = data.metrics['race_both_succeeded_count']?.values?.count ?? 0;
  const unexpectedError = data.metrics['race_unexpected_error_count']?.values?.count ?? 0;
  const totalReqs       = data.metrics['http_reqs']?.values?.count ?? 0;

  let verdict;
  if (bothSucceeded === 0) {
    verdict = '✅ 정상 — pay와 cancel이 동시에 성공한 경우 없음';
  } else {
    verdict = `❌ 경합 감지 — ${bothSucceeded}건에서 pay + cancel 동시 성공 (OrderEntity @Version 추가 검토 필요)`;
  }

  const summary = {
    '총 요청 수': totalReqs,
    '경합 쌍 수 (VU)': RACE_PAIRS,
    'pay+cancel 동시 성공 건수 (기대값: 0)': bothSucceeded,
    '예상치 못한 5xx 건수': unexpectedError,
    '판정': verdict,
    '수동 확인 SQL (order-service DB)': [
      `-- pay된 주문 중 reservation이 RELEASED인 케이스 (재고 환불됐는데 PAID)`,
      `SELECT o.id, o.status, o.reservation_id`,
      `FROM customer_order o`,
      `WHERE o.status = 'PAID';`,
      `-- 그 후 catalog-service DB에서 해당 reservation_id의 status 확인`,
      `SELECT id, status FROM inventory_reservation WHERE id IN (...);`,
      `-- PAID + RELEASED 또는 CANCELED + COMMITTED 조합이 있으면 버그`,
    ],
    '개선 방안': bothSucceeded > 0
      ? 'OrderEntity에 @Version 추가 또는 findWithItemsById에 SELECT FOR UPDATE 적용'
      : '현재 구현 유지',
  };

  console.log('\n========== Pay-Cancel 경합 테스트 결과 ==========');
  console.log(JSON.stringify(summary, null, 2));

  return { stdout: JSON.stringify(summary, null, 2) };
}
