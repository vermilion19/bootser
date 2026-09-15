package com.booster.dday.holiday.domain;

import java.text.Normalizer;

/**
 * 이름 축(A-5)의 <b>정체성</b> — `name_en` 이 아니라 이것이다 (SCHEMA §4.2).
 *
 * <pre>
 *   "New Year's Day"          → "new-years-day"
 *   "New Years Day"           → "new-years-day"      ← 같은 slug. 그것이 목적이다
 *   "Día de la Constitución"  → "dia-de-la-constitucion"
 * </pre>
 *
 * <h2>허브와 낱장이 같은 키로 묶여야 한다</h2>
 *
 * <p>{@code GET /holiday-names}(허브)와 {@code GET /holiday-names/{slug}}(낱장)가
 * 다른 키로 묶이면 <b>허브의 숫자와 낱장의 목록이 안 맞고, 안 맞는 이유가 안 보인다.</b>
 * 그래서 둘 다 slug 로 묶고, 표시용 영어 이름은 그 묶음에서 가장 흔한 것을 고른다.
 *
 * <p>{@code HolidayNameLabel} 도 slug 를 키로 잡는다 — 라벨이 {@code name_en} 을
 * 키로 잡으면 세 번째 키가 생기고, 셋이 갈라지는 것은 시간 문제다.
 *
 * <p><b>{@code name_en} 의 유일 제약은 그대로 둔다.</b> 원천 충실도는 {@code name_en} 이,
 * 축의 정체성은 slug 가 맡는다. 둘은 다른 일이다.
 *
 * <h2>⚠ 접는 규칙은 아직 열린 결정이다 (SCHEMA §13.3 S-1)</h2>
 *
 * <p>아포스트로피를 <b>지우고</b>(하이픈으로 바꾸지 않고) 악센트를 ASCII 로 <b>편다.</b>
 * 그런데 그 둘이 옳은지는 <b>코퍼스를 봐야 안다</b> — 실제로 몇 조가 한 slug 로 합쳐지는지
 * 세고 나서 정하기로 했다. 지금은 규칙을 정해 두고 첫 동기화 직후에 잰다.
 *
 * <p>재 본 결과가 다르면 그때는 <b>자료가 이미 찬 뒤</b>이므로 §14 의 변경 스크립트가 된다.
 * 착수 3 앞에서 S-6 을 닫아 둔 것이 여기서 쓰인다.
 */
public record NameSlug(String value) {

    /** {@code varchar(120)} */
    public static final int MAX_LENGTH = 120;

    private static final char SEPARATOR = '-';

    public NameSlug {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("slug 가 비어 있다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("slug 가 " + MAX_LENGTH + "자를 넘는다: " + value);
        }
        if (!value.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            throw new IllegalArgumentException("slug 는 소문자·숫자를 하이픈으로 이은 모양이다: '" + value + "'");
        }
    }

    public static NameSlug of(String nameEn) {
        if (nameEn == null || nameEn.isBlank()) {
            throw new IllegalArgumentException("영어 이름 없이는 slug 를 만들 수 없다");
        }

        /* NFD 로 풀면 é 가 e + 결합기호가 된다. 결합기호만 버리면 ASCII 가 남는다.
           NFC 로 두면 é 가 한 글자라 아래 필터에서 통째로 사라진다 */
        String folded = Normalizer.normalize(nameEn, Normalizer.Form.NFD);

        StringBuilder slug = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); i++) {
            char c = Character.toLowerCase(folded.charAt(i));

            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                slug.append(c);
                continue;
            }
            /* 아포스트로피는 하이픈이 아니라 **없는 것**으로 친다.
               "New Year's Day" 와 "New Years Day" 가 같은 slug 여야 하기 때문이다 */
            if (c == '\'' || c == '’' || Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (slug.length() > 0 && slug.charAt(slug.length() - 1) != SEPARATOR) {
                slug.append(SEPARATOR);
            }
        }

        String trimmed = trimSeparators(slug.toString());
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("slug 로 남는 글자가 없다: '" + nameEn + "'");
        }
        return new NameSlug(truncate(trimmed));
    }

    /**
     * {@code varchar(120)} 을 넘으면 <b>마디 경계에서</b> 자른다.
     *
     * <p>자르면 서로 다른 이름이 한 slug 로 합쳐질 수 있다. 그래도 자르는 까닭은,
     * <b>자연키는 {@code name_en} 이 맡고 있어서 행이 합쳐지지는 않기</b> 때문이다 —
     * 합쳐지는 것은 축의 묶음뿐이고, 120자를 넘는 공휴일 이름은 축에서 만날 일이 없다.
     * 반대로 여기서 예외를 던지면 그 나라가 통째로 동기화에서 빠진다.
     */
    private static String truncate(String slug) {
        if (slug.length() <= MAX_LENGTH) {
            return slug;
        }
        String cut = slug.substring(0, MAX_LENGTH);
        int lastSeparator = cut.lastIndexOf(SEPARATOR);
        return trimSeparators(lastSeparator > 0 ? cut.substring(0, lastSeparator) : cut);
    }

    private static String trimSeparators(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == SEPARATOR) {
            start++;
        }
        while (end > start && value.charAt(end - 1) == SEPARATOR) {
            end--;
        }
        return value.substring(start, end);
    }
}
