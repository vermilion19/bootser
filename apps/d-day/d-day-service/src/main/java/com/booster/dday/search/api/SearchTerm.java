package com.booster.dday.search.api;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.shared.web.DDayErrorCode;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 찾는 말 — <b>정규화는 여기서 한 번만 한다.</b>
 *
 * <p>소스가 넷이고 저마다 자기 방식으로 소문자를 만들거나 악센트를 펴면, 언젠가
 * 둘이 갈라져 <b>같은 질의가 나라에서는 걸리고 공휴일에서는 안 걸린다.</b> 그러면
 * 찾는 사람은 그것이 자료가 없는 것인지 우리가 못 찾는 것인지 알 수 없다.
 *
 * <h2>왜 DB 의 {@code lower()} 에 맡기지 않나</h2>
 *
 * <p>맡기면 <b>DB 마다 다르게 돈다.</b> 악센트 펴기는 PostgreSQL 에서
 * {@code unaccent} 확장이 있어야 하고 H2 에는 없다 — 테스트에서는 되는데 운영에서
 * 안 되거나 그 반대가 된다. {@code anniversary} 의 {@code CASCADE} 에서 이미 한 번
 * 겪은 종류의 함정이다 (SCHEMA §1.2).
 *
 * <p>그래서 <b>비교하는 양쪽을 우리가 같은 규칙으로 편다.</b> 질의어는 여기서,
 * 자료 쪽은 소스가 {@link #normalize} 로.
 *
 * <h2>한 글자도 받는다</h2>
 *
 * <p>영어라면 두 글자 미만을 막는 것이 흔하지만 <b>한국어는 한 글자가 낱말이다</b> —
 * 「설」 · 「추」. 대신 결과 수를 소스마다 잘라 한 갈래가 응답을 독식하지 못하게 한다.
 */
public record SearchTerm(String raw, String normalized) {

    /** 너무 길면 자른다. 비교 비용이 아니라 <b>로그와 에러 메시지</b> 때문이다 */
    public static final int MAX_LENGTH = 100;

    public static SearchTerm of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "찾을 말(q)이 없다");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }

        String normalized = normalize(trimmed);
        if (normalized.isEmpty()) {
            /* 기호만 넣은 경우다. 「빈 결과」로 돌려주면 자료가 없는 것처럼 보인다 */
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "찾을 말에 글자가 없다: " + trimmed);
        }
        return new SearchTerm(trimmed, normalized);
    }

    /**
     * 비교에 쓰는 꼴로 편다.
     *
     * <ol>
     *   <li><b>악센트를 뗀다</b> — {@code Día de Muertos} 를 {@code dia} 로도 찾을 수
     *       있어야 한다. 한글 자모는 {@code NFD} 로 풀렸다가 결합 문자가 아니라
     *       <b>한글 자모 영역</b>이라 살아남는다</li>
     *   <li><b>소문자로</b> — {@link Locale#ROOT} 다. 터키어 로캘에서 {@code I} 가
     *       점 없는 {@code ı} 가 되는 것을 피한다</li>
     *   <li><b>글자와 숫자만 남긴다</b> — {@code New Year's Day} 와 {@code new year day}
     *       가 같은 말이 되게. {@code NameSlug} 가 아포스트로피를 지우는 것과 같은 규칙이다</li>
     * </ol>
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);

        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        /* 다시 합친다 — 한글은 NFD 로 자모가 갈라진 채 두면 「설날」이 여섯 글자가 되고,
           그러면 「설」로 시작하는지 보는 검사가 자모 단위로 어긋난다 */
        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC);
    }

    /** 이 말이 그 값 안에 있나. <b>양쪽 다 같은 규칙으로 편 뒤</b> 본다 */
    public boolean matches(String value) {
        String normalizedValue = normalize(value);
        return !normalizedValue.isEmpty() && normalizedValue.contains(normalized);
    }
}
