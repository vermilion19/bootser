package com.booster.dday.holiday.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 황금연휴의 징검다리 날짜. <b>원천이 준다 — 계산하지 않는다</b> (SPEC §9.3 · §11.1).
 *
 * <pre>
 *   bridgeDays: []                → ""
 *   ["2026-05-04"]                → "2026-05-04"
 *   ["2026-05-06","2026-05-04"]   → "2026-05-04,2026-05-06"
 * </pre>
 *
 * <p>{@link SubdivisionKey} 와 같은 모양으로 굳힌다 (SCHEMA §1.3) — 정렬해 쉼표로 잇고,
 * 빈 집합은 {@code NULL} 이 아니라 {@code ''}. 여기는 유일 제약에 안 들어가므로
 * {@code NULL} 이어도 당장 탈은 없지만, <b>집합값 넷이 한 모양이어야</b> 읽는 쪽이
 * 칼럼마다 다른 규칙을 외우지 않는다.
 *
 * <p>{@code dayCount} 는 담지 않는다. 시작과 끝에서 나오는 값이라 따로 담으면
 * 둘이 갈라질 수 있고, 그러면 그것까지 검증해야 한다 (SPEC §9.3).
 */
public record BridgeDays(String value) {

    public static final BridgeDays NONE = new BridgeDays("");

    /** {@code varchar(200)} — 11자짜리 날짜 16개가 들어간다 */
    public static final int MAX_LENGTH = 200;

    private static final String SEPARATOR = ",";

    public BridgeDays {
        if (value == null) {
            throw new IllegalArgumentException("징검다리는 널이 될 수 없다 — 없으면 빈 문자열이다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("징검다리가 " + MAX_LENGTH + "자를 넘는다: " + value);
        }
        if (!value.isEmpty()) {
            for (String each : value.split(SEPARATOR, -1)) {
                try {
                    LocalDate.parse(each);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("징검다리가 날짜가 아니다: '" + each + "'", e);
                }
            }
        }
    }

    public static BridgeDays of(Collection<LocalDate> days) {
        if (days == null || days.isEmpty()) {
            return NONE;
        }
        TreeSet<LocalDate> sorted = new TreeSet<>();
        for (LocalDate day : days) {
            if (day == null) {
                throw new IllegalArgumentException("징검다리에 빈 날짜가 있다: " + days);
            }
            sorted.add(day);
        }
        return new BridgeDays(sorted.stream().map(LocalDate::toString)
                .reduce((a, b) -> a + SEPARATOR + b).orElseThrow());
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public List<LocalDate> days() {
        return isEmpty() ? List.of() : java.util.Arrays.stream(value.split(SEPARATOR))
                .map(LocalDate::parse)
                .toList();
    }
}
