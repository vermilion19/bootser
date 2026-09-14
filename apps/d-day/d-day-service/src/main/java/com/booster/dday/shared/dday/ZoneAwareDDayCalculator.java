package com.booster.dday.shared.dday;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link DDayCalculator} 의 구현. 상태가 없고 Spring 도 거의 모른다.
 *
 * <p>{@code @Component} 하나만 붙는다. 캐시도 트랜잭션도 여기 붙지 않는다 —
 * 이 클래스가 만드는 값은 <b>전부 오늘에 의존하므로 캐시하면 안 되는 값</b>이다
 * (SPEC §9.9(2)). 누가 여기에 {@code @Cacheable} 을 붙이면 그 순간 그 결정이 무너진다.
 */
@Component
public class ZoneAwareDDayCalculator implements DDayCalculator {

    @Override
    public LocalDate today(ZoneId zone, Instant now) {
        Objects.requireNonNull(zone, "시간대 없이는 오늘이 정해지지 않는다 (E-1)");
        Objects.requireNonNull(now, "지금 없이는 오늘이 정해지지 않는다");
        return now.atZone(zone).toLocalDate();
    }

    @Override
    public int daysUntil(LocalDate target, ZoneId zone, Instant now) {
        Objects.requireNonNull(target, "target");
        return days(today(zone, now), target);
    }

    @Override
    public int daysSince(LocalDate origin, ZoneId zone, Instant now) {
        Objects.requireNonNull(origin, "origin");
        return days(origin, today(zone, now));
    }

    @Override
    public LongWeekendPhase phaseOf(LocalDate start, LocalDate end, ZoneId zone, Instant now) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("연휴가 뒤집혔다: " + start + " ~ " + end);
        }

        LocalDate today = today(zone, now);
        if (today.isBefore(start)) {
            return LongWeekendPhase.BEFORE;
        }
        /* 마지막날은 연휴 안이다. isAfter(end) 여야 끝난 것이고,
           여기서 등호를 놓치면 연휴 마지막날에 「끝난 뒤」가 나온다 */
        if (today.isAfter(end)) {
            return LongWeekendPhase.AFTER;
        }
        return LongWeekendPhase.DURING;
    }

    @Override
    public <T extends HasDate> Optional<T> pickNext(List<T> candidates, ZoneId zone, Instant now) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        LocalDate today = today(zone, now);

        /* 오늘 이후(오늘 포함) 중 가장 이른 것. 정렬을 기대하지 않고 최솟값을 고른다 */
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !candidate.date().isBefore(today))
                .min(Comparator.comparing(HasDate::date));
    }

    /**
     * 두 날짜 사이의 날수.
     *
     * <p>{@code ChronoUnit.DAYS} 를 {@link LocalDate} 에 쓴다 — {@link Instant} 에 쓰면
     * 24시간 단위로 세어서 서머타임이 든 날에 하루가 사라지거나 두 번 센다.
     * 날짜끼리 세면 그 문제가 없다.
     */
    private static int days(LocalDate from, LocalDate to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to));
    }
}
