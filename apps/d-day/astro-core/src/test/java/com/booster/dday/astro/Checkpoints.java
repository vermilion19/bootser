package com.booster.dday.astro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 검산점 픽스처를 읽는다 — {@code /astro/checkpoints.json}.
 *
 * <p>파일의 모양이 성립하는지는 {@link AstroCheckpointsFixtureTest} 가 따로 본다.
 * 여기서는 읽어 주기만 한다.
 */
public final class Checkpoints {

    private static final String PATH = "/astro/checkpoints.json";

    private static final JsonNode ROOT = load();

    private Checkpoints() {
    }

    private static JsonNode load() {
        try (InputStream in = Checkpoints.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException(PATH + " 가 클래스패스에 없다");
            }
            return new ObjectMapper().readTree(in);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(PATH + " 를 읽지 못했다", e);
        }
    }

    /**
     * 분까지만 적힌 것과 날짜만 적힌 것 둘 다 받는다.
     *
     * <p>{@code Instant.parse} 는 초를 요구해서 "14:46Z" 를 못 읽는다. 픽스처에 ":00" 을
     * 붙이면 원천에 없는 정밀도를 지어내는 것이라 파서 쪽을 맞춘다.
     */
    public static Instant parse(String utc) {
        return utc.length() == 10
                ? LocalDate.parse(utc).atStartOfDay(ZoneOffset.UTC).toInstant()
                : OffsetDateTime.parse(utc).toInstant();
    }

    public record SolarTermFixture(int year, String name, String ko, int longitude, Instant utc) {
    }

    public record MoonPhaseFixture(int year, String phase, Instant utc) {
    }

    public static List<SolarTermFixture> solarTerms() {
        List<SolarTermFixture> out = new ArrayList<>();
        for (JsonNode n : ROOT.path("solarTerms")) {
            out.add(new SolarTermFixture(
                    n.path("year").asInt(),
                    n.path("name").asText(),
                    n.path("ko").asText(),
                    n.path("longitude").asInt(),
                    parse(n.path("utc").asText())));
        }
        return out;
    }

    public static List<MoonPhaseFixture> moonPhases() {
        List<MoonPhaseFixture> out = new ArrayList<>();
        for (JsonNode n : ROOT.path("moonPhases")) {
            out.add(new MoonPhaseFixture(
                    n.path("year").asInt(),
                    n.path("phase").asText(),
                    parse(n.path("utc").asText())));
        }
        return out;
    }

    public static JsonNode root() {
        return ROOT;
    }
}
