package com.booster.dday.shared.locale;

import java.util.Locale;

/**
 * 응답 언어. 한국어와 영어 둘뿐이다 (SPEC §10.8).
 *
 * <p><b>언어는 캐시 키에 안 들어간다.</b> 라벨을 통째로 따로 캐시하고 조립에서
 * 합치기로 했다 (ARCHITECTURE §4.2) — 키를 언어로 가르면 라벨 하나가 바뀔 때
 * 204×5 개 키가 식는다. 그래서 이 타입은 <b>응답을 만드는 자리에서만</b> 쓰인다.
 */
public enum Lang {

    KO,
    EN;

    /** 모르는 값이면 한국어다. 국내 서비스이므로 기본을 한국어로 둔다 */
    public static final Lang DEFAULT = KO;

    /**
     * {@code ?lang=} 이 {@code Accept-Language} 를 <b>덮어쓴다</b> (SPEC §10.8).
     *
     * <p>둘 다 없으면 기본값이다. 여기서 예외를 던지지 않는다 — 언어를 못 알아들었다고
     * 조회를 거절하면, 브라우저가 보낸 낯선 {@code Accept-Language} 하나로
     * 멀쩡한 요청이 400 이 된다.
     */
    public static Lang resolve(String queryParam, String acceptLanguage) {
        Lang fromQuery = parse(queryParam);
        if (fromQuery != null) {
            return fromQuery;
        }
        Lang fromHeader = parseHeader(acceptLanguage);
        return fromHeader != null ? fromHeader : DEFAULT;
    }

    private static Lang parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ko")) {
            return KO;
        }
        return normalized.startsWith("en") ? EN : null;
    }

    /**
     * {@code Accept-Language: ko-KR,ko;q=0.9,en;q=0.8} 처럼 온다.
     *
     * <p>q 값을 제대로 보지 않는다 — <b>앞에서부터 아는 언어를 찾는다.</b> 브라우저가
     * 대개 선호 순으로 보내므로 이 정도면 맞고, 안 맞아도 {@code ?lang=} 으로 덮을 수 있다.
     */
    private static Lang parseHeader(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        for (String part : acceptLanguage.split(",")) {
            Lang found = parse(part.split(";")[0]);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
