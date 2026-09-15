package com.booster.dday.release.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관심 등록 (D-3) — <b>2단 발행의 두 번째 단이 이것을 읽는다.</b>
 *
 * <p>일정이 바뀌면 «사실» 이벤트가 하나 나가고(1단), 우리 컨슈머가 이 표를 읽어
 * 수신자별로 펼친다(2단, ARCHITECTURE §3.4).
 *
 * <p>동기화 트랜잭션에서 바로 펼치지 않는 까닭은 <b>관심자 수에 비례해 동기화
 * 트랜잭션이 길어지기</b> 때문이다 — 인기 팀 하나가 동기화 전체를 붙든다. 그리고
 * 그렇게 하면 재시도 단위도 통째가 된다.
 */
@Entity
@Table(
        name = "watch",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watch", columnNames = {"member_id", "subject_type", "subject_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watch extends BaseEntity {

    @Id
    private Long id;

    /** {@code auth-service} 소유. 논리 참조라 FK 가 없다 */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    public static Watch of(Long memberId, SubjectType subjectType, Long subjectId) {
        if (subjectType == SubjectType.MOVIE_RELEASE) {
            /* ck_watch_subject 가 허용하지 않는다 — 관심은 작품에 걸고,
               개봉 회차에 걸지 않는다 */
            throw new IllegalArgumentException("개봉 회차에는 관심을 걸 수 없다");
        }
        Watch watch = new Watch();
        watch.id = SnowflakeGenerator.nextId();
        watch.memberId = memberId;
        watch.subjectType = subjectType;
        watch.subjectId = subjectId;
        return watch;
    }

    public boolean ownedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
