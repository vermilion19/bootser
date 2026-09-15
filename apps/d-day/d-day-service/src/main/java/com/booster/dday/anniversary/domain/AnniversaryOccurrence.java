package com.booster.dday.anniversary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 기념일의 <b>발생일 투영</b> (ARCHITECTURE §2.3).
 *
 * <h2>왜 투영을 두나</h2>
 *
 * <p>«D-7 인 기념일을 전부 찾아라» 를 <b>SQL 로 못 쓴다.</b> 음력 기념일의 다음
 * 양력 발생일은 칼럼에 없고 계산에 있기 때문이다. 매일 전 회원의 전 기념일을
 * 계산해 거르면 회원 수에 비례해 매일 전수 계산이고, 그것은 Scale-out 이 안 된다
 * (ShedLock 이 한 인스턴스에만 일을 주므로 그 인스턴스가 혼자 탄다).
 *
 * <p>미리 펼쳐 두면 스케줄러에게 <b>음력이든 양력이든 같은 모양</b>이 된다 —
 * 결합이 등록 시점 한 곳으로 모인다.
 *
 * <h2>§9.9(2) 와 부딪히지 않는 까닭</h2>
 *
 * <p>«D-day 는 캐시에 넣지 않는다» 가 막는 것은 <b>오늘이 바뀌면 틀려지는 값</b>이다.
 * 여기 저장하는 것은 <b>발생일(절대 날짜)</b>이지 D-day 가 아니다 —
 * 2027-03-15 는 내일도 2027-03-15 다.
 *
 * <h2>이 표에서만 Snowflake 규약을 벗어난다</h2>
 *
 * <p>PK 가 자연키 셋이다. <b>집합체가 아니라 투영</b>이라 바깥에서 이 행 하나를
 * id 로 가리킬 일이 없고, 그 셋이 <b>Outbox 멱등키와 정확히 같다</b>
 * (ARCHITECTURE §3.5) — 대리키를 두면 같은 뜻의 키가 둘이 된다.
 *
 * <p>{@code updated_at} 도 없다. 이 행은 만들어지고 한 번 {@code notified_at} 이
 * 찍히고 끝난다.
 */
@Entity
@Table(name = "anniversary_occurrence")
@IdClass(AnniversaryOccurrence.Key.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnniversaryOccurrence {

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long anniversaryId;
        private LocalDate occurrenceDate;
        private Short notifyOffset;
    }

    @Id
    @Column(name = "anniversary_id", nullable = false)
    private Long anniversaryId;

    /** <b>그 기념일이 실제로 오는 날.</b> 알리는 날이 아니다 */
    @Id
    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    /** 며칠 <b>전</b>에 알릴 것인가. 0 이면 그 날이다 */
    @Id
    @Column(name = "notify_offset", nullable = false)
    private Short notifyOffset;

    /** 스케줄러가 조인 없이 수신자를 알아야 한다 — 그래서 여기 한 번 더 적는다 */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static AnniversaryOccurrence of(Long anniversaryId, Long memberId,
                                           LocalDate occurrenceDate, int notifyOffset) {
        if (notifyOffset < 0 || notifyOffset > NotifyOffsets.MAX_OFFSET) {
            throw new IllegalArgumentException(
                    "알림 시점은 0~" + NotifyOffsets.MAX_OFFSET + "일 전이다: " + notifyOffset);
        }
        AnniversaryOccurrence occurrence = new AnniversaryOccurrence();
        occurrence.anniversaryId = anniversaryId;
        occurrence.memberId = memberId;
        occurrence.occurrenceDate = occurrenceDate;
        occurrence.notifyOffset = (short) notifyOffset;
        return occurrence;
    }

    /** 알림을 보냈다. 한 번 찍히고 끝난다 */
    public void notified(Instant when) {
        this.notifiedAt = when;
    }

    /** 이 행이 「알려야 할 날」 — 발생일에서 오프셋만큼 앞이다 */
    public LocalDate notifyOn() {
        return occurrenceDate.minusDays(notifyOffset);
    }
}
