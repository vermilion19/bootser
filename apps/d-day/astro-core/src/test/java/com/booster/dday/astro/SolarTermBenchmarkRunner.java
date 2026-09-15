package com.booster.dday.astro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 벤치마크를 돌리고 <b>통과 조건을 그 자리에서 단언한다</b>.
 *
 * <pre>
 *   ./gradlew :apps:d-day:astro-core:benchmark
 * </pre>
 *
 * <p>평소 {@code test} 에서는 빠진다 ({@code @Tag("bench")}). 한 번 도는 데 분 단위라
 * CI 에 섞으면 아무도 테스트를 안 돌리게 된다.
 *
 * <h2>재기만 하고 끝내지 않는 까닭</h2>
 *
 * <p>ARCHITECTURE §4.4 의 20ms 는 <b>결론이 걸린 수</b>다. 재기만 하고 사람이 눈으로
 * 보고 넘어가면, 나중에 계산이 느려졌을 때 그 사실이 «캐시 정책의 전제가 무너졌다» 로
 * 읽히지 않는다.
 *
 * <h2>두 값의 성격이 다르다 — 그래서 상한도 다르다</h2>
 *
 * <table>
 *   <caption>2026-09-15 실측 (Windows · JDK 25.0.4 · JMH 1.37)</caption>
 *   <tr><th></th><th>p50</th><th>p99</th><th>상한</th><th>무엇인가</th></tr>
 *   <tr><td>절기 한 해</td><td>1.70 ms</td><td><b>2.07 ms</b></td><td>20 ms</td>
 *       <td><b>설계가 건 게이트.</b> 넘으면 캐시 정책이 바뀐다</td></tr>
 *   <tr><td>음력 변환 1건</td><td>24.9 ms</td><td><b>31.8 ms</b></td><td>60 ms</td>
 *       <td><b>회귀 상한.</b> 설계 목표가 아니라 오늘 잰 값의 두 배다</td></tr>
 * </table>
 *
 * <p>음력에 20ms 를 걸지 않는다. <b>그 수는 절기에 걸린 값이고, 음력은 애초에
 * 재라고 한 적이 없다.</b> 재 보니 12배 느렸을 뿐이다 — 그 사실을 게이트로 바꾸려면
 * 먼저 «얼마면 되는가» 를 정해야 하고, 그것은 이 파일이 할 일이 아니다
 * (ARCHITECTURE §10-14). 대신 <b>10배 느려지는 것</b>은 여기서 막는다.
 */
@Tag("bench")
class SolarTermBenchmarkRunner {

    /**
     * 벤치마크 이름 → 상한(ms).
     *
     * <p>이름을 적어 두므로 <b>벤치마크를 더하면 여기도 더해야 한다.</b> 상한 없이
     * 도는 벤치마크는 재기만 하고 아무것도 정하지 않는다.
     */
    private static final Map<String, Double> CEILINGS = new LinkedHashMap<>(Map.of(
            "solarTermsOfOneYear", 20.0,
            "lunarConversionOfOneDate", 60.0));

    @Test
    @DisplayName("천문 계산이 상한 안에 있다 — 넘으면 캐시 정책부터 다시 본다")
    void staysUnderTheCeilings() throws Exception {
        Options options = new OptionsBuilder()
                .include(SolarTermBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();

        Collection<RunResult> results = new Runner(options).run();
        assertThat(results).as("벤치마크가 하나도 안 돌았다").isNotEmpty();

        for (RunResult result : results) {
            String label = result.getPrimaryResult().getLabel();
            double p50 = result.getPrimaryResult().getStatistics().getPercentile(50);
            double p99 = result.getPrimaryResult().getStatistics().getPercentile(99);

            Double ceiling = CEILINGS.get(label);
            assertThat(ceiling)
                    .as("%s 에 상한이 없다 — 재기만 하는 벤치마크는 아무것도 정하지 않는다", label)
                    .isNotNull();

            System.out.printf("%n  [실측] %-26s p50 %6.2f ms · p99 %6.2f ms  (상한 %.0f ms)%n",
                    label, p50, p99, ceiling);

            assertThat(p99)
                    .as("%s 의 p99 가 %.0f ms 를 넘었다", label, ceiling)
                    .isLessThanOrEqualTo(ceiling);
        }
    }
}
