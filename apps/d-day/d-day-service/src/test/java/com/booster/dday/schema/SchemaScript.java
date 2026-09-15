package com.booster.dday.schema;

import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code scripts/schema.sql} 을 컨테이너에 넣는다.
 *
 * <p>마이그레이션 도구를 쓰지 않기로 했으므로(docs/SCHEMA.md §11.2) 운영에서는 사람이
 * {@code psql} 로 넣는다. 테스트에서는 그 사람 자리를 이 클래스가 대신한다 —
 * <b>운영과 테스트가 같은 파일을 쓴다는 것이 요점</b>이고, 그래야 아래 두 테스트가
 * 실제로 운영 스키마를 검사하는 것이 된다.
 */
final class SchemaScript {

    static final String PATH = "/scripts/schema.sql";
    static final String CHANGES_DIR = "/scripts/changes/";

    /** {@code INSERT INTO schema_change (revision, name) VALUES (2, ...)} 에서 2 를 뽑는다 */
    private static final Pattern REVISION = Pattern.compile(
            "INSERT\\s+INTO\\s+schema_change\\s*\\([^)]*\\)\\s*VALUES\\s*\\(\\s*(\\d+)");

    /** {@code 0002-add-holiday-name-ko.sql} — 번호는 이름이 아니라 리비전이다 (SCHEMA.md §14.3) */
    private static final Pattern CHANGE_FILE = Pattern.compile("^(\\d{4})-[a-z0-9-]+\\.sql$");

    private SchemaScript() {
    }

    static String read() {
        try (InputStream in = SchemaScript.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        PATH + " 가 없다 — docs/SCHEMA.md §12 에서 뽑아 두는 파일이다");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(PATH + " 를 읽지 못했다", e);
        }
    }

    /**
     * 세미콜론으로 끊어 한 문장씩 돌린다.
     *
     * <p>{@code psql} 은 통째로 받지만 JDBC 는 한 문장씩이라 끊어야 한다.
     * 작은따옴표 안의 세미콜론에 속지 않게 따옴표를 센다 — {@code CHECK} 안에
     * 정규식 리터럴이 여럿 있어서 실제로 걸린다.
     */
    static void applyTo(JdbcDatabaseContainer<?> container) {
        try (Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement()) {

            for (String sql : split(read())) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("schema.sql 을 적용하지 못했다: " + e.getMessage(), e);
        }
    }

    static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);

            if (c == '\'') {
                /* 두 번 이어진 따옴표는 문자열 안의 따옴표 하나다 */
                if (inQuote && i + 1 < script.length() && script.charAt(i + 1) == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                inQuote = !inQuote;
            }

            if (c == '-' && !inQuote && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                /* 줄 끝까지 주석이다 */
                int newline = script.indexOf('\n', i);
                if (newline < 0) {
                    break;
                }
                i = newline;
                current.append('\n');
                continue;
            }

            if (c == ';' && !inQuote) {
                addIfMeaningful(out, current);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        addIfMeaningful(out, current);

        if (out.isEmpty()) {
            throw new IllegalStateException("schema.sql 에서 문장을 하나도 못 읽었다");
        }
        return out;
    }

    private static void addIfMeaningful(List<String> out, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty()) {
            out.add(sql);
        }
    }

    /**
     * {@code schema.sql} 이 스스로 선언하는 리비전.
     *
     * <p>파일의 <b>마지막 문장</b>이 그것을 적는다 — 위의 것이 전부 섰다는 뜻이기
     * 때문이다 (SCHEMA.md §14.3).
     */
    static int declaredRevision() {
        return lastRevisionIn(read())
                .orElseThrow(() -> new IllegalStateException(
                        "schema.sql 이 리비전을 안 적는다 — docs/SCHEMA.md §14.3"));
    }

    static java.util.Optional<Integer> lastRevisionIn(String sql) {
        Matcher matcher = REVISION.matcher(sql);
        Integer last = null;
        while (matcher.find()) {
            last = Integer.parseInt(matcher.group(1));
        }
        return java.util.Optional.ofNullable(last);
    }

    /**
     * {@code scripts/changes/} 의 변경 스크립트들. 이름순 = 리비전순이다.
     *
     * <p>디렉터리가 없거나 비어 있으면 빈 목록이다 — <b>그것이 지금의 상태</b>이고,
     * 아래 테스트들은 그 상태에서 참말을 한다 (비어 있는 동안은 R1 이 깨질 수 없다).
     * 재기준 뒤의 {@code archive/} 는 하위 디렉터리라 여기 안 걸린다 (§14.8).
     */
    static List<Change> changes() {
        URL dir = SchemaScript.class.getResource(CHANGES_DIR);
        if (dir == null) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .map(SchemaScript::readChange)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(CHANGES_DIR + " 를 읽지 못했다", e);
        }
    }

    private static Change readChange(Path file) {
        try {
            String name = file.getFileName().toString();
            Matcher matcher = CHANGE_FILE.matcher(name);
            Integer revision = matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
            return new Change(name, revision, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @param revision 이름이 규칙을 안 지키면 {@code null} */
    record Change(String fileName, Integer revision, String sql) {
    }
}
