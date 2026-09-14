package com.booster.dday.astro.solar;

import com.booster.dday.astro.time.JulianDay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * VSOP87D 지구 급수. 태양의 위치는 지구의 위치를 뒤집은 것이다.
 *
 * <p>계수는 {@code /astro/vsop87-earth.txt} 에 있고, 그 파일은
 * {@code tools/gen-vsop87.mjs} 가 CDS 의 원본에서 받아 잘라 낸 것이다.
 * <b>손으로 고치지 않는다</b> — 계수 한 자리를 잘못 적으면 계산이 틀린 것인지
 * 옮기다 틀린 것인지 구별할 수 없다.
 *
 * <p>D 판을 쓰는 것이 중요하다. 다섯 판 중 D 만이 「그 시점의 황도와 분점」 기준이라
 * 절기의 정의와 같은 눈금이고, 그래서 세차 변환이 끼어들 자리가 없다.
 */
public final class Vsop87Earth {

    private static final String RESOURCE = "/astro/vsop87-earth.txt";

    /** 차수는 0..5 다. 원본에 그 이상은 없다 */
    private static final int MAX_POWER = 6;

    private static final double[][][] L = new double[MAX_POWER][][];
    private static final double[][][] B = new double[MAX_POWER][][];
    private static final double[][][] R = new double[MAX_POWER][][];

    static {
        load();
    }

    private Vsop87Earth() {
    }

    private static void load() {
        List<List<double[]>> l = emptyPowers();
        List<List<double[]>> b = emptyPowers();
        List<List<double[]>> r = emptyPowers();

        try (InputStream in = Vsop87Earth.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        RESOURCE + " 가 없다 — node apps/d-day/tools/gen-vsop87.mjs 를 돌릴 것");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 5) {
                    throw new IllegalStateException("줄 모양이 다르다: " + line);
                }
                int power = Integer.parseInt(parts[1]);
                double[] term = {
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]),
                };
                switch (parts[0]) {
                    case "L" -> l.get(power).add(term);
                    case "B" -> b.get(power).add(term);
                    case "R" -> r.get(power).add(term);
                    default -> throw new IllegalStateException("모르는 변수: " + parts[0]);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(RESOURCE + " 를 읽지 못했다", e);
        }

        freeze(l, L);
        freeze(b, B);
        freeze(r, R);

        if (L[0].length == 0) {
            throw new IllegalStateException("L0 가 비어 있다 — 급수 파일이 잘못됐다");
        }
    }

    private static List<List<double[]>> emptyPowers() {
        List<List<double[]>> out = new ArrayList<>(MAX_POWER);
        for (int i = 0; i < MAX_POWER; i++) {
            out.add(new ArrayList<>());
        }
        return out;
    }

    private static void freeze(List<List<double[]>> from, double[][][] into) {
        for (int power = 0; power < MAX_POWER; power++) {
            into[power] = from.get(power).toArray(new double[0][]);
        }
    }

    /** 지구의 일심 황경, 라디안. tau 는 J2000 기준 율리우스 천년(TT) */
    public static double longitude(double tau) {
        return sum(L, tau);
    }

    /** 지구의 일심 황위, 라디안 */
    public static double latitude(double tau) {
        return sum(B, tau);
    }

    /** 지구-태양 거리, AU */
    public static double radius(double tau) {
        return sum(R, tau);
    }

    public static double tauOf(double julianDayTt) {
        return JulianDay.millenniaSinceJ2000(julianDayTt);
    }

    /**
     * 차수별 합을 호너법으로 묶는다.
     *
     * <p>{@code Math.pow(tau, power)} 를 차수마다 부르지 않는 것은 절기 하나를 푸는 데
     * 이 함수가 대여섯 번 불리고, 그때마다 1100 항을 훑기 때문이다.
     */
    private static double sum(double[][][] series, double tau) {
        double total = 0.0;
        for (int power = MAX_POWER - 1; power >= 0; power--) {
            double partial = 0.0;
            for (double[] term : series[power]) {
                partial += term[0] * Math.cos(term[1] + term[2] * tau);
            }
            total = total * tau + partial;
        }
        return total;
    }
}
