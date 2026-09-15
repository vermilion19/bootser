package com.booster.dday.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code scripts/schema.sql} 이 실제 PostgreSQL 에서 서는가, 그리고 정한 인덱스가 다 있는가.
 *
 * <p><b>마이그레이션 도구를 안 쓰기로 했으므로 이 테스트가 유일한 방어다</b>
 * (docs/SCHEMA.md §11.4). Hibernate 의 {@code validate} 는 인덱스도 유일 제약도
 * {@code CHECK} 도 안 본다 — 누가 {@code ddl-auto: update} 로 바꾸면 부분 인덱스와
 * {@code INCLUDE} 와 {@code CHECK} 가 전부 사라지는데 <b>앱은 멀쩡히 뜬다.</b>
 *
 * <p>이름 목록을 테스트가 들고 있는 것이 요점이다. 누가 인덱스를 지우거나 이름을 바꾸면
 * 여기가 깨지고, 그러면 <b>왜 지웠는지를 적게 강제된다.</b>
 *
 * <p>Docker 가 없으면 통째로 건너뛴다 — {@code disabledWithoutDocker} 는 이 저장소의
 * 기존 관례다(`storage-redis` 의 테스트들).
 */
@Tag("schema")
@Testcontainers(disabledWithoutDocker = true)
class SchemaIndexTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    /** docs/SCHEMA.md 가 정한 인덱스. PK 가 자동으로 만드는 것은 여기 없다 */
    private static final Set<String> EXPECTED = new TreeSet<>(List.of(
            // holiday (§4.1)
            "uk_holiday_natural",
            "ix_holiday_country_year",
            "ix_holiday_on_date",
            "ix_holiday_axis_name",
            "ix_holiday_axis_country",
            // long_weekend
            "uk_long_weekend_natural",
            "ix_long_weekend_country_year",
            "ix_long_weekend_rank",
            // anniversary (§5)
            "ix_anniversary_member",
            "ix_anniversary_expand",
            "ix_occurrence_due",
            "ix_occurrence_purge",
            // release (§6)
            "uk_league_source",
            "uk_team_source",
            "ix_team_league",
            "uk_sport_event_source",
            "ix_sport_event_league_time",
            "ix_sport_event_home",
            "ix_sport_event_away",
            "uk_movie_source",
            "ix_movie_release_upcoming",
            "uk_watch",
            "ix_watch_fanout",
            "ix_date_change_subject",
            "ix_date_change_detected",
            // outbox (§7)
            "uk_outbox_idem",
            "ix_outbox_claim",
            "ix_outbox_purge",
            // sync (§8)
            "ix_sync_run_target_time",
            "ix_sync_run_item_run",
            "ix_sync_run_item_bad"));

    @BeforeAll
    static void applySchema() {
        POSTGRES.start();
        SchemaScript.applyTo(POSTGRES);
    }

    private static List<String> query(String sql) {
        List<String> out = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
        return out;
    }

    @Test
    @DisplayName("문서가 정한 표 열아홉 개가 선다 — 도메인 18 + 이력 1")
    void createsEveryTable() {
        List<String> tables = query("""
                SELECT tablename FROM pg_tables
                 WHERE schemaname = 'public' ORDER BY tablename
                """);

        assertThat(tables).hasSize(19);
        assertThat(tables).contains("country", "holiday", "long_weekend", "holiday_coverage",
                "anniversary", "anniversary_occurrence", "outbox_event",
                "sync_run", "sync_run_item");

        // 도구를 안 쓰기로 했으므로 「무엇이 적용됐는가」를 DB 가 들고 있어야 한다 (§14)
        assertThat(tables).contains("schema_change");

        // sky 와 axis 는 표가 없다. 그것이 SPEC §9.4 · §9.5 가 지켜졌다는 증거다
        assertThat(tables).noneMatch(t -> t.startsWith("sky") || t.startsWith("axis"));
    }

    @Test
    @DisplayName("문서가 정한 인덱스가 하나도 빠지지 않는다")
    void createsEveryIndex() {
        Set<String> actual = new TreeSet<>(query("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'public' ORDER BY indexname
                """));

        assertThat(actual)
                .as("docs/SCHEMA.md 가 정한 인덱스 중 없는 것")
                .containsAll(EXPECTED);

        List<String> unexpected = actual.stream()
                .filter(name -> !EXPECTED.contains(name))
                .filter(name -> !name.endsWith("_pkey"))     // PK 가 만드는 것
                .toList();
        assertThat(unexpected)
                .as("문서에 없는 인덱스가 생겼다 — SCHEMA.md 와 이 목록에 함께 적을 것")
                .isEmpty();
    }

    /**
     * 부분 인덱스가 정말 <b>부분</b>인가.
     *
     * <p>이름만 맞고 술어가 빠지면 §3.2·§5.4·§7.2 의 결정이 통째로 사라지는데,
     * 이름만 보는 검사는 그것을 못 잡는다.
     */
    @Test
    @DisplayName("부분 인덱스는 술어를 들고 있고, 유일 인덱스는 안 들고 있다")
    void partialIndexesKeepTheirPredicate() {
        List<String> partial = query("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'public' AND indexdef LIKE '%WHERE%'
                 ORDER BY indexname
                """);

        assertThat(partial).contains(
                "ix_holiday_country_year", "ix_holiday_on_date",
                "ix_occurrence_due", "ix_occurrence_purge",
                "ix_outbox_claim", "ix_outbox_purge",
                "ix_sync_run_item_bad", "ix_anniversary_expand");

        // §3.2 — 유일 인덱스가 부분이 되면 ON CONFLICT 가 소프트 삭제된 행을 못 잡아
        // 같은 자연키가 두 줄이 된다. 무덤까지 덮어야 한다
        assertThat(partial)
                .as("자연키가 부분 인덱스가 됐다 — SCHEMA.md §3.2 를 읽을 것")
                .doesNotContain("uk_holiday_natural", "uk_long_weekend_natural");
    }

    @Test
    @DisplayName("CHECK 제약이 살아 있다 — validate 가 못 보는 자리다")
    void keepsCheckConstraints() {
        List<String> checks = query("""
                SELECT conname FROM pg_constraint
                 WHERE contype = 'c' AND connamespace = 'public'::regnamespace
                 ORDER BY conname
                """);

        assertThat(checks).contains(
                "ck_holiday_year",      // 파생 칼럼이 갈라지면 INSERT 가 실패한다 (§1.2)
                "ck_holiday_public",    // ',Public,' 앵커 — LIKE '%Public%' 의 오염 방어
                "ck_holiday_global",
                "ck_holiday_subdiv",
                "ck_country_weekend",
                "ck_anniv_leap_dom",
                "ck_occ_offset",
                "ck_outbox_status");
    }
}
