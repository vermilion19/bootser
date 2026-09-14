package com.booster.dday.astro.lunar;

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
 * ELP2000-82B 달 급수. 지심 직교좌표를 J2000 황도면 기준으로 준다.
 *
 * <p>계수는 {@code /astro/elp2000-moon.txt} 에 있고, 그 파일은
 * {@code tools/gen-elp2000.mjs} 가 CDS 의 원본 36개와 참조 구현 {@code elp82b.f} 에서
 * 만든 것이다. <b>손으로 고치지 않는다.</b>
 *
 * <p>원본은 자료를 세 무리로 나누고 무리마다 인자를 다르게 짜는데, 생성기가 그것을
 * <b>t 의 다항식 하나로 펴 두었다.</b> 그래서 여기에는 갈래가 없다 — 진폭 · t 차수 ·
 * 다항식 계수 다섯이면 항 하나가 선다. 상수(W1 · 눈금 · 회전)도 같은 파일에 있다.
 */
public final class Elp2000Moon {

    private static final String RESOURCE = "/astro/elp2000-moon.txt";

    private static final double ARCSEC_TO_RAD = Math.PI / 648000.0;

    /** 진폭에 곱하는 t 의 차수는 0 · 1 · 2 뿐이다 */
    private static final int MAX_T_POWER = 3;

    /** [변수][t차수] → {진폭, a0, a1, a2, a3, a4} 의 배열 */
    private static final double[][][][] TERMS = new double[3][MAX_T_POWER][][];

    private static double[] w1;
    private static double scale;
    private static double[] pCoefficients;
    private static double[] qCoefficients;

    static {
        load();
    }

    private Elp2000Moon() {
    }

    private static void load() {
        List<List<List<double[]>>> gathered = new ArrayList<>();
        for (int v = 0; v < 3; v++) {
            List<List<double[]>> byPower = new ArrayList<>();
            for (int p = 0; p < MAX_T_POWER; p++) {
                byPower.add(new ArrayList<>());
            }
            gathered.add(byPower);
        }

        try (InputStream in = Elp2000Moon.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        RESOURCE + " 가 없다 — node apps/d-day/tools/gen-elp2000.mjs 를 돌릴 것");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                switch (parts[0]) {
                    case "W1" -> w1 = doubles(parts, 1, 5);
                    case "SCALE" -> scale = Double.parseDouble(parts[1]);
                    case "P" -> pCoefficients = doubles(parts, 1, 5);
                    case "Q" -> qCoefficients = doubles(parts, 1, 5);
                    case "V", "U", "R" -> {
                        int variable = switch (parts[0]) {
                            case "V" -> 0;
                            case "U" -> 1;
                            default -> 2;
                        };
                        int power = Integer.parseInt(parts[1]);
                        double[] term = new double[6];
                        for (int i = 0; i < 6; i++) {
                            term[i] = Double.parseDouble(parts[2 + i]);
                        }
                        gathered.get(variable).get(power).add(term);
                    }
                    default -> throw new IllegalStateException("모르는 줄: " + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(RESOURCE + " 를 읽지 못했다", e);
        }

        if (w1 == null || pCoefficients == null || qCoefficients == null || scale == 0.0) {
            throw new IllegalStateException("상수 줄(W1 · SCALE · P · Q)이 빠졌다");
        }
        for (int v = 0; v < 3; v++) {
            for (int p = 0; p < MAX_T_POWER; p++) {
                TERMS[v][p] = gathered.get(v).get(p).toArray(new double[0][]);
            }
        }
        if (TERMS[0][0].length == 0) {
            throw new IllegalStateException("황경 급수가 비어 있다");
        }
    }

    private static double[] doubles(String[] parts, int from, int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = Double.parseDouble(parts[from + i]);
        }
        return out;
    }

    /** 한 변수의 급수 합. 단위는 파일이 정한 대로 — 황경·황위는 초각, 거리는 km */
    private static double series(int variable, double t) {
        double total = 0.0;
        for (int power = MAX_T_POWER - 1; power >= 0; power--) {
            double partial = 0.0;
            for (double[] term : TERMS[variable][power]) {
                double argument = term[5];
                argument = argument * t + term[4];
                argument = argument * t + term[3];
                argument = argument * t + term[2];
                argument = argument * t + term[1];
                partial += term[0] * Math.sin(argument);
            }
            total = total * t + partial;
        }
        return total;
    }

    private static double polynomial(double[] coefficients, double t) {
        double out = 0.0;
        for (int i = coefficients.length - 1; i >= 0; i--) {
            out = out * t + coefficients[i];
        }
        return out;
    }

    /**
     * 달의 지심 직교좌표, km. 기준틀은 <b>J2000 의 평균 동역학 황도면과 관성 분점</b>이다
     * — {@code elp82b.f} 가 내는 것과 같다.
     *
     * @param julianDayTt TT 기준 율리우스일
     */
    public static double[] rectangularJ2000(double julianDayTt) {
        double t = JulianDay.centuriesSinceJ2000(julianDayTt);

        /* 급수를 더하고 평균황경을 얹는다 — 여기까지가 그 시점 황도면 기준의 구면좌표다 */
        double longitude = series(0, t) * ARCSEC_TO_RAD + polynomial(w1, t);
        double latitude = series(1, t) * ARCSEC_TO_RAD;
        double distance = series(2, t) * scale;

        double x1 = distance * Math.cos(latitude);
        double x2 = x1 * Math.sin(longitude);
        x1 = x1 * Math.cos(longitude);
        double x3 = distance * Math.sin(latitude);

        /* 그 시점의 황도면을 J2000 황도면으로 돌린다.
           pw · qw 는 황도의 기울기를 두 성분으로 적은 것이고, 아래 조합이 회전행렬이다 */
        double pw = polynomial(pCoefficients, t) * t;
        double qw = polynomial(qCoefficients, t) * t;
        double ra = 2.0 * Math.sqrt(1 - pw * pw - qw * qw);
        double pwqw = 2.0 * pw * qw;
        double pw2 = 1 - 2.0 * pw * pw;
        double qw2 = 1 - 2.0 * qw * qw;
        pw = pw * ra;
        qw = qw * ra;

        return new double[] {
                pw2 * x1 + pwqw * x2 + pw * x3,
                pwqw * x1 + qw2 * x2 - qw * x3,
                -pw * x1 + qw * x2 + (pw2 + qw2 - 1) * x3,
        };
    }

    /** 지구-달 거리, km */
    public static double distanceKm(double julianDayTt) {
        double[] r = rectangularJ2000(julianDayTt);
        return Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
    }
}
