package com.booster.dday.country.infrastructure;

import com.booster.dday.country.application.dto.CountrySeedRow;
import com.booster.dday.country.domain.Weekend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code seed/country.tsv} 를 읽는다. <b>Spring 을 모르는 순수 계산이다.</b>
 *
 * <p>파일은 {@code tools/gen-country-seed.mjs} 가 원천 넷에서 받아 적은 것이고,
 * 이 클래스는 그것을 줄 단위로 푸는 일만 한다. <b>값을 고치거나 채워 넣지 않는다</b> —
 * 여기서 기본값을 하나라도 넣기 시작하면 "원천이 그렇게 말했다" 와 "우리가
 * 그렇게 정했다" 가 한 파일 안에서 섞인다.
 *
 * <p>대신 <b>모양이 틀리면 멈춘다.</b> 시드가 반쯤 들어간 채로 뜨는 것이
 * 안 뜨는 것보다 나쁘다 — 없는 나라는 404 로 보이지만, 빠진 나라는
 * "그 나라엔 공휴일이 없다" 로 보인다.
 */
final class CountrySeedFile {

    static final String PATH = "/seed/country.tsv";

    /** {@code code|nameEn|nameKo|zoneId|ambiguous|mask} */
    private static final int FIELDS = 6;

    private CountrySeedFile() {
    }

    static List<CountrySeedRow> read() {
        try (InputStream in = CountrySeedFile.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        PATH + " 가 없다 — tools/gen-country-seed.mjs 로 낸다");
            }
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<CountrySeedRow> parse(BufferedReader reader) throws IOException {
        List<CountrySeedRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            /* -1 을 준다. 빈 꼬리 칸을 버리면 칸이 모자란 줄이 「짧은 줄」이 아니라
               「다른 줄」로 보여서, 무엇이 빠졌는지 알 수 없게 된다 */
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length != FIELDS) {
                throw new IllegalStateException(
                        PATH + " " + lineNumber + "줄: 칸이 " + parts.length + "개다 (" + FIELDS + "개여야 한다): " + trimmed);
            }

            CountrySeedRow row = new CountrySeedRow(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parseBoolean(parts[4], lineNumber),
                    Weekend.of(parseMask(parts[5], lineNumber)));

            if (!seen.add(row.code())) {
                throw new IllegalStateException(
                        PATH + " " + lineNumber + "줄: 같은 나라가 두 번 나온다: " + row.code());
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException(PATH + " 에서 한 줄도 못 읽었다");
        }
        return rows;
    }

    private static boolean parseBoolean(String value, int lineNumber) {
        /* Boolean.parseBoolean 을 안 쓴다 — 그것은 "yes" 도 오타도 전부 false 로
           읽는다. 시간대를 우리가 골랐다는 표시가 오타 하나로 꺼지면 SPEC §9.9(4)
           가 조용히 무너진다 */
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalStateException(
                PATH + " " + lineNumber + "줄: true/false 가 아니다: '" + value + "'");
    }

    private static int parseMask(String value, int lineNumber) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    PATH + " " + lineNumber + "줄: 주말 비트마스크가 숫자가 아니다: '" + value + "'", e);
        }
    }
}
