package com.booster.dday.holiday.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 원천이 준 {@code types} 배열을 정렬해 이은 것 (SPEC §11.2 · A-10).
 *
 * <pre>
 *   ["Public"]            → "Public"
 *   ["Bank","Optional"]   → "Bank,Optional"
 *   ["Public","Bank"]     → "Bank,Public"      ← 정렬한다
 * </pre>
 *
 * <h2>{@code types} 는 배열이고 실제로 복수가 온다</h2>
 *
 * <p>SPEC §11.2 의 실측이다. 45개국 · 2026 · 612건에서 {@code Bank+Optional} 이 2건,
 * 미국에서 <b>{@code Public+Bank} 가 10건</b> 나왔다.
 *
 * <p><b>그래서 A-10 의 필터는 같음 비교가 아니라 포함 비교다.</b>
 * {@code types == "Public"} 으로 짜면 미국 공휴일 10건이 통째로 사라진다 —
 * 조용히 사라지므로 눈에 띄지도 않는다.
 *
 * <h2>{@link #isPublic()} 이 구분자로 감싸 보는 까닭</h2>
 *
 * <p>{@code raw.contains("Public")} 으로 짜면 원천이 언젠가 {@code PublicSector} 를
 * 내보낼 때 그것도 공휴일이 된다. {@code ",Public,"} 는 그럴 수 없다.
 * {@code ck_holiday_public} 이 DB 에서 같은 모양으로 한 번 더 막는다 (SCHEMA §1.2).
 *
 * <h2>비-{@code Public} 도 버리지 않고 담는다</h2>
 *
 * <p>A-10 이 그렇게 정했다. 노출만 막는다 — 버리면 나중에 {@code Bank} 축을 열 때
 * <b>204개국 전체 재동기화</b>가 필요하고, 그 재동기화는 원천이 그때도 옛 자료를
 * 준다는 보장 위에 서 있다.
 */
public record HolidayTypes(String raw) {

    /** {@code varchar(200)} */
    public static final int MAX_LENGTH = 200;

    private static final String SEPARATOR = ",";
    private static final String PUBLIC = "Public";

    public HolidayTypes {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("타입이 하나도 없는 공휴일은 담지 않는다");
        }
        if (raw.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("타입 문자열이 " + MAX_LENGTH + "자를 넘는다: " + raw);
        }
        /* ck_holiday_types 와 같은 모양 — 영문자와 쉼표만. 원천이 이상한 것을 주면
           그 (국가, 연도) 하나만 FAILED 로 격리된다 */
        if (!raw.matches("^[A-Za-z]+(,[A-Za-z]+)*$")) {
            throw new IllegalArgumentException("타입은 영문자를 쉼표로 이은 모양이어야 한다: '" + raw + "'");
        }
    }

    public static HolidayTypes of(Collection<String> types) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("원천이 타입을 하나도 안 줬다");
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("타입이 비어 있다: " + types);
            }
            sorted.add(type.trim());
        }
        return new HolidayTypes(String.join(SEPARATOR, sorted));
    }

    /**
     * 공개 대상인가 (A-10).
     *
     * <p>「{@code Public} 인가」가 아니라 <b>「{@code Public} 을 포함하는가」</b>다.
     */
    public boolean isPublic() {
        return (SEPARATOR + raw + SEPARATOR).contains(SEPARATOR + PUBLIC + SEPARATOR);
    }

    public List<String> values() {
        return Arrays.asList(raw.split(SEPARATOR));
    }
}
