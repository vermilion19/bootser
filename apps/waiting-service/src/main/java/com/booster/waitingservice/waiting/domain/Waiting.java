package com.booster.waitingservice.waiting.domain;

import com.booster.storage.db.core.BaseEntity;
import com.booster.waitingservice.waiting.exception.InvalidWaitingStatusException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "waiting") // 테이블명 명시
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waiting extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long restaurantId; // 식당 ID (논리적 참조)

    @Column(nullable = false, length = 20)
    private String guestPhone; // 손님 전화번호 (식별자)

    @Column(nullable = false)
    private Integer partySize; // 일행 수

    @Column(nullable = false)
    private Integer waitingNumber; // 식당별 대기 순번 (1번, 2번...)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingStatus status; // 상태 (WAITING, ENTERED, CANCELED)

    private Waiting(Long id,Long restaurantId, String guestPhone, Integer partySize, Integer waitingNumber) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.guestPhone = guestPhone;
        this.partySize = partySize;
        this.waitingNumber = waitingNumber;
        this.status = WaitingStatus.WAITING; // 초기 상태는 무조건 WAITING
    }

    public static Waiting create(Long id,Long restaurantId, String guestPhone, int partySize, int waitingNumber) {
        // 도메인 유효성 검증
        if (partySize <= 0) {
            throw new IllegalArgumentException("일행 수는 1명 이상이어야 합니다.");
        }
        return new Waiting(id,restaurantId, guestPhone, partySize, waitingNumber);
    }

    public void call() {
        if (this.status != WaitingStatus.WAITING) {
            throw new InvalidWaitingStatusException(); // 대기 중인 사람만 호출 가능
        }
        this.status = WaitingStatus.CALLED; // 상태 변경 -> updatedAt 갱신됨
    }

    // 입장 처리
    public void enter() {
        if (this.status != WaitingStatus.CALLED) {
            throw new InvalidWaitingStatusException();
        }
        this.status = WaitingStatus.ENTERED;
    }

    // 취소 처리 (손님 취소 or 점주 취소)
    public void cancel() {
        if (this.status == WaitingStatus.ENTERED) {
            throw new IllegalStateException("이미 입장한 손님은 취소할 수 없습니다.");
        }
        this.status = WaitingStatus.CANCELED;
    }
}
