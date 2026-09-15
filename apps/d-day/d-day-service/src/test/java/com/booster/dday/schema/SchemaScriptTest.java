package com.booster.dday.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code schema.sql} 을 문장으로 끊는 쪽. <b>Docker 없이 돈다.</b>
 *
 * <p>{@link SchemaIndexTest} 는 Testcontainers 를 쓰므로 Docker 가 없는 자리에서는 통째로
 * 건너뛴다. 그러면 스키마를 아무도 안 보게 되는데, 그중 <b>끊는 쪽만은 순수 계산</b>이라
 * 여기서 붙잡아 둔다 — 끊기가 틀리면 그 위의 모든 검사가 엉뚱한 것을 검사한다.
 *
 * <p>특히 <b>작은따옴표 안의 세미콜론</b>이 실제 위험이다. {@code CHECK} 제약에 정규식
 * 리터럴이 여럿 있고, 그 안의 문자를 문장 끝으로 읽으면 DDL 이 조각난다.
 */
class SchemaScriptTest {

    @Test
    @DisplayName("실제 스크립트가 문장으로 끊긴다")
    void splitsTheRealScript() {
        List<String> statements = SchemaScript.split(SchemaScript.read());

        long tables = statements.stream().filter(s -> s.startsWith("CREATE TABLE")).count();
        long indexes = statements.stream().filter(s -> s.contains("CREATE INDEX")
                || s.contains("CREATE UNIQUE INDEX")).count();

        /* 도메인 표 18 + 이력 표 1 (schema_change · §14). 이력은 자료가 아니라
           「무엇이 적용됐는가」라, 18 옆에 19번째로 서지만 성격이 다르다 */
        assertThat(tables).as("표 수 — docs/SCHEMA.md §12").isEqualTo(19);
        assertThat(indexes).as("인덱스 수 (PK 가 만드는 것은 빼고)").isEqualTo(31);

        assertThat(statements)
                .as("주석만 남은 조각이 문장으로 새어 나왔다")
                .noneMatch(s -> s.startsWith("--"));
    }

    @Test
    @DisplayName("작은따옴표 안의 세미콜론에 속지 않는다")
    void ignoresSemicolonsInsideLiterals() {
        List<String> statements = SchemaScript.split(
                "CREATE TABLE a (x text CHECK (x ~ 'a;b'));\nCREATE TABLE b (y int);");

        assertThat(statements).hasSize(2);
        assertThat(statements.getFirst()).contains("'a;b'");
    }

    @Test
    @DisplayName("문자열 안의 두 겹 따옴표를 하나로 센다")
    void handlesEscapedQuotes() {
        List<String> statements = SchemaScript.split(
                "INSERT INTO a VALUES ('it''s; fine');\nSELECT 1;");

        assertThat(statements).hasSize(2);
        assertThat(statements.getFirst()).contains("it''s; fine");
    }

    @Test
    @DisplayName("줄 주석 안의 세미콜론도 문장을 끊지 않는다")
    void ignoresSemicolonsInComments() {
        List<String> statements = SchemaScript.split(
                "-- 이건 주석이다; 끊으면 안 된다\nCREATE TABLE a (x int);");

        assertThat(statements).hasSize(1);
        assertThat(statements.getFirst()).startsWith("CREATE TABLE");
    }

    /**
     * §12 가 정한 결정 몇 개는 SQL 글자에 그대로 남아 있다.
     * Docker 없이도 그것만은 여기서 문다.
     */
    @Test
    @DisplayName("결정이 SQL 에 남아 있다 — 빈 집합은 NULL 이 아니라 ''")
    void keepsKeyDecisionsInTheText() {
        String script = SchemaScript.read();

        assertThat(script)
                .as("§1.3 — NULL 이면 유일 제약이 무력해진다")
                .contains("subdivision_key  varchar(1000) NOT NULL DEFAULT ''");
        assertThat(script)
                .as("§1.2 — LIKE '%Public%' 이 아니라 구분자 앵커여야 한다")
                .contains("',Public,'");
        /* 글자로 견주지 않는다 — 줄바꿈이 CRLF 라 한 번 걸렸다. 끊어 놓고 의미로 본다.
           이렇게 두면 DDL 의 줄 모양이 바뀌어도 이 검사는 살아 있다. */
        for (String name : List.of("uk_holiday_natural", "uk_long_weekend_natural")) {
            String statement = SchemaScript.split(script).stream()
                    .filter(s -> s.contains(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(name + " 인덱스가 없다"));

            assertThat(statement)
                    .as("§3.2 — %s 가 부분 인덱스가 되면 ON CONFLICT 가 소프트 삭제된 행을 "
                            + "못 잡아 같은 자연키가 두 줄이 된다", name)
                    .doesNotContain("WHERE");
        }
    }

    /**
     * 2차 스키마 변경의 규칙을 문다 (SCHEMA.md §14).
     *
     * <p><b>지금 {@code scripts/changes/} 는 비어 있다.</b> 그래서 아래 몇 개는 오늘
     * 아무것도 안 무는 것처럼 보이는데, 그것이 요점이다 — <b>첫 변경 스크립트가
     * 들어오는 순간 곧바로 무는 검사</b>가 미리 서 있어야 한다. 규칙을 문서에만
     * 적어 두면 첫 변경을 쓰는 사람이 그 문서를 읽었을 때만 지켜진다.
     *
     * <p>마이그레이션 도구를 안 쓰기로 했으므로(§11.2) <b>순서와 이력을 지키는 것이
     * 가드와 이 테스트뿐</b>이다. 그리고 이 테스트는 Docker 없이 돈다.
     */
    @Nested
    @DisplayName("2차 스키마 변경 (§14)")
    class SchemaChange {

        @Test
        @DisplayName("이력 표가 스크립트 안에 있다 — 무엇이 적용됐는지를 DB 가 들고 있어야 한다")
        void historyTableExists() {
            String script = SchemaScript.read();

            assertThat(script).contains("CREATE TABLE schema_change");
            assertThat(script)
                    .as("§14.3 — 리비전이 PK 여야 같은 번호가 두 번 안 들어간다")
                    .contains("revision   integer      PRIMARY KEY");
        }

        /**
         * 마지막이어야 하는 까닭은 <b>「위의 것이 전부 섰다」는 뜻</b>이기 때문이다.
         * 가운데 두면 뒤가 실패해도 리비전만 남고, 다음 사람이 적용됐다고 믿는다.
         */
        @Test
        @DisplayName("schema.sql 의 마지막 문장이 자기 리비전을 적는다")
        void revisionIsDeclaredLast() {
            List<String> statements = SchemaScript.split(SchemaScript.read());

            assertThat(statements.getLast())
                    .as("§14.3 — 리비전 선언이 맨 마지막이어야 한다")
                    .startsWith("INSERT INTO schema_change");
            assertThat(SchemaScript.declaredRevision()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("변경 스크립트의 이름이 곧 리비전이다")
        void changeFilesAreNamedByRevision() {
            for (SchemaScript.Change change : SchemaScript.changes()) {
                assertThat(change.revision())
                        .as("%s — 이름이 NNNN-소문자-하이픈.sql 이어야 한다 (§14.3)", change.fileName())
                        .isNotNull();
            }
        }

        /**
         * 가드가 없으면 0003 을 0002 보다 먼저 돌려도 <b>대개 그냥 돌아간다.
         * 틀린 채로.</b> 도구가 주는 것 중 실제로 아쉬운 것은 체크섬이 아니라 순서다.
         */
        @Test
        @DisplayName("변경 스크립트는 가드로 시작하고 이력으로 끝난다")
        void changeFilesGuardAndRecord() {
            for (SchemaScript.Change change : SchemaScript.changes()) {
                assertThat(change.sql())
                        .as("%s — 앞선 리비전을 확인하는 가드가 없다 (§14 R2)", change.fileName())
                        .contains("RAISE EXCEPTION")
                        .contains("max(revision)");

                assertThat(SchemaScript.lastRevisionIn(change.sql()))
                        .as("%s — 자기 리비전을 이력에 안 적는다 (§14 R2)", change.fileName())
                        .contains(change.revision());

                assertThat(SchemaScript.split(change.sql()).getLast())
                        .as("%s — 이력 INSERT 가 마지막 문장이어야 한다", change.fileName())
                        .startsWith("INSERT INTO schema_change");
            }
        }

        @Test
        @DisplayName("되돌리기 스크립트를 두지 않는다 — 자료를 지운 변경은 되돌아오지 않는다")
        void noDownScripts() {
            assertThat(SchemaScript.changes())
                    .as("§14 R3 — 잘못 갔으면 앞으로 가는 변경을 하나 더 쓴다")
                    .noneMatch(change -> change.fileName().contains("down")
                            || change.fileName().contains("rollback")
                            || change.fileName().contains("revert"));
        }

        @Test
        @DisplayName("리비전이 이어진다 — 빠짐도 겹침도 없다")
        void revisionsAreContiguous() {
            List<Integer> revisions = SchemaScript.changes().stream()
                    .map(SchemaScript.Change::revision)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            for (int i = 1; i < revisions.size(); i++) {
                assertThat(revisions.get(i))
                        .as("리비전 %d 다음이 %d 다 — 번호가 건너뛰면 가드가 영영 안 맞는다",
                                revisions.get(i - 1), revisions.get(i))
                        .isEqualTo(revisions.get(i - 1) + 1);
            }
        }

        /**
         * <b>§14 R1 을 기계가 무는 자리다.</b>
         *
         * <p>R1 은 "한 변경은 두 파일을 같이 고친다" 인데, 안 지켜도 <b>새 DB 도 뜨고
         * 고친 DB 도 뜬다.</b> 둘이 갈라졌다는 것은 아무 데서도 안 보인다. 리비전
         * 숫자를 견주는 것이 지금 우리가 가진 유일한 감시다 — 구조까지 견주는
         * 동등성 테스트는 첫 변경이 데려온다 (§14.7).
         */
        @Test
        @DisplayName("schema.sql 의 리비전이 마지막 변경과 같다 — R1 이 지켜졌다는 뜻")
        void schemaSqlKeepsUpWithChanges() {
            List<SchemaScript.Change> changes = SchemaScript.changes();
            if (changes.isEmpty()) {
                assertThat(SchemaScript.declaredRevision())
                        .as("변경이 없으면 schema.sql 은 기준선(1)이다")
                        .isEqualTo(1);
                return;
            }

            Integer last = changes.stream()
                    .map(SchemaScript.Change::revision)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElseThrow();

            assertThat(SchemaScript.declaredRevision())
                    .as("변경 스크립트는 %d 까지 갔는데 schema.sql 은 %d 에 머물러 있다 — "
                            + "새로 세운 DB 와 고친 DB 가 갈라진다 (§14 R1)",
                            last, SchemaScript.declaredRevision())
                    .isEqualTo(last);
        }
    }
}
