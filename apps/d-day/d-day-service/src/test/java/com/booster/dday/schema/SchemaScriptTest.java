package com.booster.dday.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        assertThat(tables).as("표 수 — docs/SCHEMA.md §12").isEqualTo(18);
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
}
