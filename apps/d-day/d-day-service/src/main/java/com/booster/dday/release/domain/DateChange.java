package com.booster.dday.release.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 일정이 바뀐 이력 (D-4).
 *
 * <p><b>이 표와 Outbox 가 같은 트랜잭션에 있는 것이 D-4 의 전부다</b>
 * (ARCHITECTURE §3.5). 둘이 갈라지면 "이력에는 있는데 알림은 안 갔다" 또는 그 반대가
 * 생기고, 그것은 이 표가 존재하는 이유를 무너뜨린다.
 *
 * <h2>유일 제약을 걸지 않는다</h2>
 *
 * <p>같은 경기가 같은 값으로 두 번 바뀔 수 있다 — 순연됐다가 풀렸다가 또 순연되는
 * 일이 야구에서는 일상이다. <b>중복 제거는 이 표의 일이 아니라
 * {@code outbox_event.idempotency_key} 의 일이다</b> (SCHEMA §6.3).
 */
@Entity
@Table(name = "date_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DateChange extends BaseEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChangedField field;

    @Column(name = "old_value", length = 64)
    private String oldValue;

    @Column(name = "new_value", length = 64)
    private String newValue;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** 어느 회차가 찾았나. 회차를 지운 뒤에도 이력은 남으므로 FK 가 아니다 */
    @Column(name = "sync_run_id")
    private Long syncRunId;

    public static DateChange of(SubjectType subjectType, Long subjectId, ChangedField field,
                                String oldValue, String newValue, Instant detectedAt, Long syncRunId) {
        DateChange change = new DateChange();
        change.id = SnowflakeGenerator.nextId();
        change.subjectType = subjectType;
        change.subjectId = subjectId;
        change.field = field;
        change.oldValue = trim(oldValue);
        change.newValue = trim(newValue);
        change.detectedAt = detectedAt;
        change.syncRunId = syncRunId;
        return change;
    }

    /** {@code varchar(64)} 를 넘으면 그 이력이 통째로 안 적힌다 — 잘라서라도 남긴다 */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
