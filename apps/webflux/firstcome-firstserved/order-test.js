import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
// k6 run order-test.js 로 실행
// 1. 커스텀 지표 설정 (성공 횟수, 품절 횟수 카운팅)
const successCount = new Counter('order_success_count');
const soldOutCount = new Counter('order_sold_out_count');

// 2. 부하 테스트 옵션 설정
export const options = {
    // 3단계 스테이지 구성
    stages: [
        { duration: '5s', target: 500 },  // 5초 동안 유저 500명까지 서서히 증가 (Ramp-up)
        { duration: '10s', target: 1000 }, // 10초 동안 유저 1000명 유지 (Peak)
        { duration: '5s', target: 0 },    // 5초 동안 0명으로 감소 (Ramp-down)
    ],
    // 임계치 설정: 에러율이 1% 미만이어야 테스트 통과 (여기선 품절도 에러로 치지 않게 로직 분리 필요)
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95%의 요청이 500ms 안에 처리되어야 함
    },
};

export default function () {
    // 3. 요청 데이터 생성
    // 유저 ID를 랜덤으로 생성해 실제 여러 사람이 누르는 것처럼 시뮬레이션
    const userId = Math.floor(Math.random() * 10000) + 1;
    const payload = JSON.stringify({
        userId: userId,
        itemId: 1,      // 테스트할 아이템 ID
        quantity: 1
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 4. API 호출 (로컬 서버 주소)
    const res = http.post('http://localhost:8080/api/v1/orders', payload, params);

    // 5. 응답 검증
    // 우리 로직상 성공은 202(Accepted), 재고 부족은 400(Bad Request) 입니다.
    const isSuccess = check(res, {
        'Status is 202 (Accepted)': (r) => r.status === 202,
        'Status is 400 (Sold Out)': (r) => r.status === 400,
    });

    // 카운터 증가
    if (res.status === 202) {
        successCount.add(1);
    } else if (res.status === 400) {
        soldOutCount.add(1);
    }

    // 너무 빨리 요청하면 OS 포트가 고갈될 수 있으므로 아주 약간의 텀을 줌
    sleep(0.1);
}