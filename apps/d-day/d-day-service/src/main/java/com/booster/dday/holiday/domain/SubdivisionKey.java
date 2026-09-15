package com.booster.dday.holiday.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 지역 집합을 <b>키 하나로 굳힌 것</b> — 자연키의 넷째 칸 (SCHEMA §2.2).
 *
 * <pre>
 *   counties: null            → ""                      (전국)
 *   counties: []              → ""                      (전국)
 *   ["CH-SZ","CH-GR"]         → "CH-GR,CH-SZ"           (정렬해 이음)
 * </pre>
 *
 * <h2>왜 집합을 문자열로 접나</h2>
 *
 * <p>SPEC §11.3 이 실측했다 — {@code (국가, 날짜, 영어 이름)} 으로는 <b>11조가 충돌</b>한다.
 * 스위스가 10조인데, 같은 날 같은 이름의 공휴일이 <b>칸톤 집합만 다른 두 건</b>으로 온다.
 * 넷째 칸이 있어야 0조가 된다.
 *
 * <p>자식 테이블로 풀면 DB 가 그 유일성을 강제할 방법이 없다 — <b>{@code ON CONFLICT} 가
 * 겨냥할 인덱스가 없다.</b> 배열로 두면 배열 동등성이 순서를 보므로 결국 정렬해 넣어야
 * 하고, 해시로 접으면 {@code psql} 로 그 행을 봐도 무엇이 키인지 안 보인다 (SCHEMA §2.1).
 *
 * <h2>빈 집합이 {@code NULL} 이 아니라 {@code ''} 인 것이 이 타입의 전부다</h2>
 *
 * <p>PostgreSQL 의 btree 유일 인덱스에서 {@code NULL} 은 서로 같지 않다. 전국 공휴일을
 * {@code NULL} 로 담으면 <b>대한민국 설날이 동기화할 때마다 한 줄씩 늘어난다.</b> 조용히.
 *
 * <h2>정렬이 DB 밖에 있다는 것</h2>
 *
 * <p>{@code CHECK} 로는 <b>정렬됐는지를 검사할 수 없다</b> (SCHEMA §1.2). SQL 식으로
 * 표현할 방법이 없어서, 그 자리를 이 타입과 속성 기반 테스트가 맡는다 — <b>어떤 순서로
 * 넣어도 같은 키가 나오는가.</b> DB 가 못 지키는 자리를 안 적으면 지켜지는 줄 안다.
 */
public record SubdivisionKey(String value) {

    /** 전국. {@code NULL} 이 아니라 빈 문자열이다 */
    public static final SubdivisionKey NATIONWIDE = new SubdivisionKey("");

    /** {@code varchar(1000)} — btree 항목 상한을 손으로 재 둔 값이다 (SCHEMA §2.2) */
    public static final int MAX_LENGTH = 1000;

    private static final String SEPARATOR = ",";

    public SubdivisionKey {
        if (value == null) {
            throw new IllegalArgumentException("지역 집합은 널이 될 수 없다 — 전국이면 빈 문자열이다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "지역 집합이 " + MAX_LENGTH + "자를 넘는다 (" + value.length() + "자)");
        }
        /* ck_holiday_subdiv 가 DB 에서 막는 것과 같은 셋. 여기서 먼저 막는 까닭은
           터지는 자리를 (국가, 연도) 하나로 좁히기 위해서다 (ARCHITECTURE §5.2) */
        if (!value.isEmpty()) {
            if (value.startsWith(SEPARATOR) || value.endsWith(SEPARATOR) || value.contains(",,")) {
                throw new IllegalArgumentException("지역 집합에 빈 원소가 있다: '" + value + "'");
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)) {
                    throw new IllegalArgumentException(
                            "지역 집합에 공백이 섞이면 정렬과 비교가 흔들린다: '" + value + "'");
                }
            }
        }
    }

    /**
     * 원천이 준 {@code counties} 를 키로 접는다.
     *
     * <p>{@code null} 과 빈 목록이 <b>같은 것으로 접힌다</b> — 원천이 전국 공휴일을
     * 두 가지로 표현하는데 우리에게는 한 가지 뜻이기 때문이다.
     */
    public static SubdivisionKey of(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return NATIONWIDE;
        }
        /* TreeSet 이 정렬과 중복 제거를 한 번에 한다. 원천이 같은 코드를 두 번 주는
           것은 본 적 없지만, 그것이 키를 바꾸게 두면 같은 공휴일이 두 줄이 된다 */
        TreeSet<String> sorted = new TreeSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("지역 코드가 비어 있다: " + codes);
            }
            sorted.add(code.trim());
        }
        return new SubdivisionKey(String.join(SEPARATOR, sorted));
    }

    public boolean isNationwide() {
        return value.isEmpty();
    }

    public List<String> codes() {
        return isNationwide() ? List.of() : Arrays.asList(value.split(SEPARATOR));
    }
}
