package com.booster.dday.release.event;

import com.booster.dday.release.domain.ChangedField;
import com.booster.dday.release.domain.SubjectType;

import java.time.Instant;
import java.util.List;

/**
 * 1단 — <b>사실</b>. {@code dday.release.changed} 를 탄다 (ARCHITECTURE §3.4).
 *
 * <p><b>수신자가 여기 없다.</b> 그것이 2단으로 나눈 이유다 — 동기화 트랜잭션에서
 * 관심자를 펼치면 인기 팀 하나가 동기화 전체를 붙든다.
 *
 * <h2>{@code relatedTeamIds} 가 왜 필요한가</h2>
 *
 * <p>관심은 경기에도 걸고 <b>팀에도 걸 수 있다</b> ({@code ck_watch_subject}).
 * 한화 팬은 «2026-09-11 한화 vs NC» 를 등록한 것이 아니라 «한화» 를 등록해 두었다.
 * 그 팬에게 알리려면 <b>이 경기가 어느 팀의 경기인지</b>를 2단이 알아야 하고,
 * 2단은 Kafka 컨슈머라 DB 를 한 번 더 읽는 대신 여기 실어 보내는 편이 낫다.
 *
 * <p>팀 id 를 안 실으면 2단이 경기를 다시 조회해야 하는데, 그러면 <b>경기가 그
 * 사이에 지워졌을 때 알림이 통째로 사라진다</b> — 사실은 이미 일어났는데.
 */
public record ReleaseChangedEvent(
        SubjectType subjectType,
        Long subjectId,
        String externalId,
        String subjectName,
        ChangedField field,
        String oldValue,
        String newValue,
        Instant startsAt,
        /** 경기 시각을 그리는 시간대. 리그가 정한다 — 받는 쪽이 자기 시간대로
            그리면 날짜가 하루 어긋날 수 있다 */
        String zoneId,
        Instant detectedAt,
        List<Long> relatedTeamIds
) {
    public ReleaseChangedEvent {
        relatedTeamIds = relatedTeamIds == null ? List.of() : List.copyOf(relatedTeamIds);
    }
}
