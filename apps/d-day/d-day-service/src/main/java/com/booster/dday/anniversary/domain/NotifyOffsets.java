package com.booster.dday.anniversary.domain;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 며칠 전에 알릴 것인가 (C-5 · C-10).
 *
 * <pre>
 *   []          → ""          알림 안 받음
 *   [7, 0]      → "0,7"       그 날과 이레 전
 * </pre>
 *
 * <p>집합값 넷과 같은 모양으로 굳힌다 (SCHEMA §1.3) — 정렬해 쉼표로 잇고, 빈 집합은
 * {@code NULL} 이 아니라 {@code ''}. 여기는 유일 제약에 안 들어가지만, <b>규칙이
 * 칼럼마다 다르면 읽는 쪽이 칼럼마다 외워야 한다.</b>
 *
 * <h2>0 은 「그 날」이다</h2>
 *
 * <p>{@code notify_offset} 이 발생일에서 며칠 <b>전</b>인지이므로 0 은 당일이다.
 * 투영은 <b>0 을 언제나 담는다</b> — 목록의 D-day 가 그것으로 계산되기 때문이다.
 * 알림을 받을지 말지는 이 값이 정하고, 담을지 말지는 정하지 않는다.
 */
public record NotifyOffsets(String value) {

    public static final NotifyOffsets NONE = new NotifyOffsets("");

    /** {@code ck_occ_offset CHECK (notify_offset BETWEEN 0 AND 365)} */
    public static final int MAX_OFFSET = 365;

    /** {@code varchar(50)} */
    public static final int MAX_LENGTH = 50;

    private static final String SEPARATOR = ",";

    public NotifyOffsets {
        if (value == null) {
            throw new IllegalArgumentException("알림 시점은 널이 될 수 없다 — 없으면 빈 문자열이다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("알림 시점이 " + MAX_LENGTH + "자를 넘는다: " + value);
        }
        if (!value.isEmpty() && !value.matches("^[0-9]+(,[0-9]+)*$")) {
            throw new IllegalArgumentException("알림 시점은 숫자를 쉼표로 이은 모양이다: '" + value + "'");
        }
    }

    public static NotifyOffsets of(Collection<Integer> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return NONE;
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer offset : offsets) {
            if (offset == null || offset < 0 || offset > MAX_OFFSET) {
                throw new IllegalArgumentException(
                        "알림 시점은 0~" + MAX_OFFSET + "일 전이다: " + offset);
            }
            sorted.add(offset);
        }
        return new NotifyOffsets(sorted.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + SEPARATOR + b)
                .orElseThrow());
    }

    public List<Integer> days() {
        if (value.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(SEPARATOR))
                .map(Integer::parseInt)
                .toList();
    }

    /**
     * 투영에 담을 오프셋들 — <b>선언한 것 + 언제나 0</b>.
     *
     * <p>0 을 늘 담는 까닭은 목록의 D-day 가 그것으로 계산되기 때문이다.
     * 알림을 안 받기로 한 사람도 «다음이 언제인가» 는 봐야 한다.
     */
    public List<Integer> projectionOffsets() {
        TreeSet<Integer> all = new TreeSet<>(days());
        all.add(0);
        return List.copyOf(all);
    }

    public boolean notifiesAt(int offset) {
        return days().contains(offset);
    }
}
