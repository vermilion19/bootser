package com.booster.dday.sync.domain;

import com.booster.common.SnowflakeGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * (국가, 연도) 한 건의 결과 (ARCHITECTURE §5.2).
 *
 * <p><b>부분 실패를 담는 그릇이다.</b> 1,020 호출을 한 트랜잭션에 담지 않기로 했으므로
 * 어느 것이 되고 어느 것이 안 됐는지를 이 표가 들고 있어야 한다. 없으면 재시도
 * 단위가 통째가 되고, 999개가 성공해도 하나 때문에 전부 다시 돌게 된다.
 *
 * <h2>{@link #createdAt} 만 있고 {@code updatedAt} 이 없다</h2>
 *
 * <p>항목은 한 번 적히고 안 고쳐진다 — 회차가 다시 돌면 <b>새 항목</b>이 생긴다.
 * 그래서 {@code BaseEntity} 를 상속하지 않고 생성 시각만 둔다. DDL 에도
 * {@code updated_at} 이 없다 (SCHEMA §8).
 */
@Entity
@Table(name = "sync_run_item")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncRunItem {

    @Id
    private Long id;

    @Column(name = "sync_run_id", nullable = false)
    private Long syncRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncTarget target;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "holiday_year")
    private Short holidayYear;

    @Column(name = "league_id")
    private Long leagueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SyncItemStatus status;

    /** 원천이 준 건수 */
    @Column(name = "source_count")
    private Integer sourceCount;

    /** 우리가 담은 건수. {@code sourceCount} 와의 차이가 A-10 이 거른 것이다 */
    @Column(name = "stored_count")
    private Integer storedCount;

    @Column(name = "dropped_count", nullable = false)
    private int droppedCount;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static SyncRunItem ok(long runId, String countryCode, int year,
                                 int sourceCount, int storedCount, int droppedCount, int durationMs) {
        SyncRunItem item = base(runId, countryCode, year, SyncItemStatus.OK, durationMs);
        item.sourceCount = sourceCount;
        item.storedCount = storedCount;
        item.droppedCount = droppedCount;
        return item;
    }

    /** 못 받았다 — 네트워크 · 5xx · 타임아웃 */
    public static SyncRunItem failed(long runId, String countryCode, int year,
                                     String errorCode, String errorMessage, int durationMs) {
        SyncRunItem item = base(runId, countryCode, year, SyncItemStatus.FAILED, durationMs);
        item.errorCode = errorCode;
        item.errorMessage = trim(errorMessage);
        return item;
    }

    /**
     * 받았는데 안 믿는다 — 급감 가드가 막았다 (ARCHITECTURE §5.3).
     *
     * <p>{@link SyncItemStatus#FAILED} 와 가르는 것이 요점이다. 원천이 200 으로 빈
     * 배열을 준 회차를 네트워크 오류와 같은 줄에 묻으면, <b>사람이 봐야 할 신호가
     * 흔한 실패 속에 사라진다.</b>
     */
    public static SyncRunItem aborted(long runId, String countryCode, int year,
                                      int sourceCount, int previousCount, int durationMs) {
        SyncRunItem item = base(runId, countryCode, year, SyncItemStatus.ABORTED, durationMs);
        item.sourceCount = sourceCount;
        item.errorCode = "SOURCE_COUNT_COLLAPSED";
        item.errorMessage = trim("원천이 " + sourceCount + "건을 줬다. 직전 성공 회차는 "
                + previousCount + "건이었다 — 절반 미만이라 반영하지 않는다");
        return item;
    }

    /** 이번 회차에서 건너뛴다 — 아직 정의역 밖이거나 대상이 아니다 */
    public static SyncRunItem skipped(long runId, String countryCode, int year, String reason) {
        SyncRunItem item = base(runId, countryCode, year, SyncItemStatus.SKIPPED, 0);
        item.errorMessage = trim(reason);
        return item;
    }

    /**
     * 리그 한 건의 결과 (SPEC §12).
     *
     * <p>공휴일은 (국가, 연도)가 단위였고 경기는 <b>리그</b>가 단위다. 같은 표에
     * 담는 까닭은 회차 · 락 · 보고가 전부 같은 모양이기 때문이고, 그래서
     * {@code country_code} 와 {@code league_id} 가 한 표에 같이 있다 — 대상에 따라
     * 한쪽만 채워진다.
     *
     * @param received 원천이 준 건수. <b>무료 키에서는 2~3건이 정상이다</b> (§12.2)
     * @param stored   담은 건수. 안 바뀐 건은 여기 안 센다
     */
    public static SyncRunItem leagueOk(long runId, Long leagueId, int received,
                                       int stored, int durationMs) {
        SyncRunItem item = leagueBase(runId, leagueId, SyncItemStatus.OK, durationMs);
        item.sourceCount = received;
        item.storedCount = stored;
        return item;
    }

    public static SyncRunItem leagueFailed(long runId, Long leagueId, String errorCode,
                                           String errorMessage, int durationMs) {
        SyncRunItem item = leagueBase(runId, leagueId, SyncItemStatus.FAILED, durationMs);
        item.errorCode = errorCode;
        item.errorMessage = trim(errorMessage);
        return item;
    }

    /** 켤 수 없어 안 돌았다 — 키가 없거나 리그가 꺼져 있다 */
    public static SyncRunItem leagueSkipped(long runId, Long leagueId, String reason) {
        SyncRunItem item = leagueBase(runId, leagueId, SyncItemStatus.SKIPPED, 0);
        item.errorMessage = trim(reason);
        return item;
    }

    private static SyncRunItem leagueBase(long runId, Long leagueId,
                                          SyncItemStatus status, int durationMs) {
        SyncRunItem item = new SyncRunItem();
        item.id = SnowflakeGenerator.nextId();
        item.syncRunId = runId;
        item.target = SyncTarget.SPORT_EVENT;
        item.leagueId = leagueId;
        item.status = status;
        item.durationMs = durationMs;
        return item;
    }

    private static SyncRunItem base(long runId, String countryCode, int year,
                                    SyncItemStatus status, int durationMs) {
        SyncRunItem item = new SyncRunItem();
        item.id = SnowflakeGenerator.nextId();
        item.syncRunId = runId;
        item.target = SyncTarget.HOLIDAY;
        item.countryCode = countryCode;
        item.holidayYear = (short) year;
        item.status = status;
        item.durationMs = durationMs;
        return item;
    }

    /** {@code varchar(500)} 을 넘으면 그 항목이 통째로 안 적힌다 — 잘라서라도 남긴다 */
    private static String trim(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
