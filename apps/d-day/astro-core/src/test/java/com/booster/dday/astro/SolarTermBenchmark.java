package com.booster.dday.astro;

import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.astro.lunar.LunisolarCalendar;
import com.booster.dday.astro.solar.SolarTermSolver;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 캐시 정책을 정하는 실측 (ARCHITECTURE §4.4).
 *
 * <p>설계가 결론을 <b>수에 걸어 두었다.</b>
 *
 * <blockquote>
 * {@code SolarTermSolver.termsOf(year)} 의 p99 를 JMH 로 잰다.
 * <b>20ms 이하면 「핫 윈도우만 캐시」를 확정한다.</b> 넘으면 콜드에도 짧은 TTL 을
 * 주되 별도 Redis 논리 DB 에 둔다.
 * </blockquote>
 *
 * <p>무엇을 정하는 값인지가 요점이다. 사용자가 아무 연도나 넣을 수 있고
 * ({@code [1583, 2999]}) 그것을 전부 캐시하면 <b>콜드 키가 핫 키를 LRU 로 밀어낸다.</b>
 * 계산이 충분히 빠르면 <b>콜드는 캐시하지 않는다</b> — 오염될 자리를 없애는 쪽이
 * 오염을 막는 쪽보다 낫다.
 *
 * <h2>{@link Mode#SampleTime} 을 쓰는 까닭</h2>
 *
 * <p>통과 조건이 평균이 아니라 <b>p99</b> 다. {@code AverageTime} 은 백분위를 안 준다.
 * 평균만 보면 «가끔 200ms» 를 못 잡는데, 캐시 정책이 막으려는 것이 바로 그 «가끔» 이다.
 *
 * <h2>해를 돌려 가며 잰다</h2>
 *
 * <p>같은 해를 반복해 재면 JIT 가 그 해의 분기만 달구고 내부 캐시가 있다면 그것도
 * 탄다. <b>콜드 연도가 들어오는 상황</b>을 재는 것이 목적이므로 매번 다른 해를 준다.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class SolarTermBenchmark {

    /** 한국 음력의 기준 자오선 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * {@code [1583, 2999]} 안을 돈다.
     *
     * <p>이 범위가 §4.1 이 정한 정의역이고, <b>그 밖은 캐시에 닿기 전에 400 으로
     * 잘린다.</b> 그러므로 재야 할 최악도 이 안에 있다.
     */
    private final AtomicInteger year = new AtomicInteger(1583);

    private int nextYear() {
        return year.updateAndGet(current -> current >= 2999 ? 1583 : current + 1);
    }

    /**
     * <b>이 값이 §10-1 을 닫는다.</b> 절기 한 해 = 뉴턴법 24회, 회당 VSOP87 절단
     * 급수 평가 몇 번이다.
     */
    @Benchmark
    public void solarTermsOfOneYear(Blackhole blackhole) {
        blackhole.consume(SolarTermSolver.termsOf(nextYear()));
    }

    /**
     * 같은 물음을 음력에도 던진다. C-7(음력 기념일)이 등록할 때마다 향후 3년치를
     * 전개하므로, <b>이것이 느리면 캐시가 아니라 등록 경로가 막힌다.</b>
     */
    @Benchmark
    public void lunarConversionOfOneDate(Blackhole blackhole) {
        int target = nextYear();
        blackhole.consume(LunisolarCalendar.toSolar(LunarDate.of(target, 8, 15), KST));
    }
}
