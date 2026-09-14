package com.booster.dday.astro.meteor;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.frame.GeneralPrecession;
import com.booster.dday.astro.frame.J2000EclipticLongitude;
import com.booster.dday.astro.frame.PrecessionModel;
import com.booster.dday.astro.solar.SolarTermSolver;
import com.booster.dday.astro.time.JulianDay;
import com.booster.dday.astro.time.TimeScale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 유성우 극대. 공표된 해는 그대로 내고, 없는 해는 셈해서 낸다.
 *
 * <p>자료는 {@code /meteor/imo-YYYY.txt} 이고 {@code tools/fetch-astro-checkpoints.mjs} 가
 * 검산점 픽스처와 <b>함께</b> 낸다. 둘이 다른 경로로 들어오면 둘을 견주는 테스트가
 * 아무것도 검산하지 않게 된다.
 *
 * <p>DB 를 쓰지 않고 자원 파일로 들고 있는 까닭 — 갱신이 연 1회이고 사람이 확인해야
 * 하는 자료다. DB 에 넣으면 동기화 · 마이그레이션 · SyncRun 이 따라오고, 그러면
 * {@code sky} 가 「DB 없는 도메인」이라는 성격을 잃는다 (docs/ARCHITECTURE.md §6.4).
 */
public final class MeteorShowerCatalog {

    private static final String INDEX = "/meteor/index.txt";

    private static final PrecessionModel PRECESSION = new GeneralPrecession();

    /** 해 → (코드 → 공표값). 넣은 차례를 지킨다 — 원천의 차례가 날짜 차례다 */
    private static final Map<Integer, Map<String, MeteorMaximum.Published>> PUBLISHED = load();

    private MeteorShowerCatalog() {
    }

    /** 공표 자료가 있는 해 */
    public static Set<Integer> publishedYears() {
        return Collections.unmodifiableSet(new TreeSet<>(PUBLISHED.keySet()));
    }

    /**
     * 그 해의 극대 전부. 날짜 차례다.
     *
     * <p>공표된 해면 전부 {@link MeteorMaximum.Published} 이고, 아니면 전부
     * {@link MeteorMaximum.Estimated} 다. 섞이지 않는다 — 한 해 안에서 어떤 갈래는
     * 공표값이고 어떤 갈래는 추정값이면 그 목록을 읽는 사람이 무엇을 보고 있는지 모른다.
     */
    public static List<MeteorMaximum> of(int year) {
        Map<String, MeteorMaximum.Published> published = PUBLISHED.get(year);
        if (published != null) {
            return new ArrayList<>(published.values());
        }
        return estimate(year);
    }

    /**
     * 공표값이 없는 해를 센다.
     *
     * <p><b>여기가 ASTRO-CHECKPOINTS §4 의 함정을 지나는 유일한 길이다.</b>
     * IMO 의 황경은 equinox 2000.0 기준이므로 그대로 절기 풀이에 넣으면 2026년 기준
     * 아홉 시간이 이르게 나온다. {@link PrecessionModel} 을 지나야만 들어갈 수 있게
     * 타입을 나눠 두었고, 그 호출이 아래 한 줄로 보인다.
     *
     * <p>황경을 안 준 갈래(사분의자리 · 작은곰자리)는 <b>뺀다.</b> 날짜를 지어내느니
     * 없는 편이 낫다 — 그 갈래는 IMO 가 관측으로만 극대를 말한 것이다.
     */
    private static List<MeteorMaximum> estimate(int year) {
        List<MeteorMaximum> out = new ArrayList<>();
        for (MeteorMaximum.Published seed : latestPublished().values()) {
            J2000EclipticLongitude from = seed.solarLongitude();
            if (from == null) {
                continue;
            }

            /* 세차는 시각의 함수인데 그 시각을 구하려고 세차가 필요하다. 한 번 돈다 —
               반년을 어긋나도 세차는 25초각(시간으로 10분)이라 두 번째 걸음이면 멎는다 */
            double epoch = TimeScale.utToTt(JulianDay.ofGregorian(year, 1, 1.0));
            ApparentEclipticLongitude at = PRECESSION.toApparent(from, epoch);
            Instant when = SolarTermSolver.timeOf(at, year);

            at = PRECESSION.toApparent(from, TimeScale.utToTt(JulianDay.ofUt(when)));
            when = SolarTermSolver.timeOf(at, year);

            out.add(new MeteorMaximum.Estimated(
                    seed.code(), seed.ko(), seed.name(), when, from, at));
        }
        out.sort(java.util.Comparator.comparing(MeteorMaximum::utc));
        return out;
    }

    /** 갈래 목록의 밑바탕. 어느 해를 셈하든 이름과 황경은 여기서 온다 */
    private static Map<String, MeteorMaximum.Published> latestPublished() {
        int latest = Collections.max(PUBLISHED.keySet());
        return PUBLISHED.get(latest);
    }

    /* ------------------------------------------------------------------ 읽기 */

    private static Map<Integer, Map<String, MeteorMaximum.Published>> load() {
        Map<Integer, Map<String, MeteorMaximum.Published>> out = new LinkedHashMap<>();
        for (int year : readIndex()) {
            out.put(year, readYear(year));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException(
                    "공표 자료가 하나도 없다 — node apps/d-day/tools/fetch-astro-checkpoints.mjs 를 돌릴 것");
        }
        return out;
    }

    private static List<Integer> readIndex() {
        List<Integer> years = new ArrayList<>();
        for (String line : lines(INDEX)) {
            years.add(Integer.parseInt(line.trim()));
        }
        return years;
    }

    private static Map<String, MeteorMaximum.Published> readYear(int year) {
        Map<String, MeteorMaximum.Published> out = new LinkedHashMap<>();
        String path = "/meteor/imo-" + year + ".txt";

        for (String line : lines(path)) {
            String[] f = line.split("\\|", -1);
            if (f.length != 7) {
                throw new IllegalStateException(path + " 의 줄 모양이 다르다: " + line);
            }
            out.put(f[0], new MeteorMaximum.Published(
                    f[0],
                    f[5],
                    f[6],
                    parse(f[2]),
                    "-".equals(f[3]) ? null : parse(f[3]),
                    Precision.of(f[1]),
                    "-".equals(f[4]) ? null : J2000EclipticLongitude.of(Double.parseDouble(f[4])),
                    "IMO Meteor Shower Calendar " + year));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException(path + " 가 비어 있다");
        }
        return out;
    }

    private static List<String> lines(String resource) {
        List<String> out = new ArrayList<>();
        try (InputStream in = MeteorShowerCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " 가 클래스패스에 없다");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && line.charAt(0) != '#') {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " 를 읽지 못했다", e);
        }
        return out;
    }

    /** 날짜만 적힌 것과 분까지 적힌 것 둘 다 받는다 — 원천이 준 만큼만 적혀 있다 */
    private static Instant parse(String utc) {
        return utc.length() == 10
                ? LocalDate.parse(utc).atStartOfDay(ZoneOffset.UTC).toInstant()
                : OffsetDateTime.parse(utc).toInstant();
    }
}
