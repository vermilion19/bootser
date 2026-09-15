package com.booster.dday.shared.cache;

/**
 * 캐시 키를 만드는 <b>유일한</b> 자리. 순수 계산이라 Redis 를 모른다.
 *
 * <pre>
 *   {접두사}[:{꼬리}]:v{버전}
 *
 *   c:all:v7            COUNTRY_ALL          (꼬리 없음)
 *   h:KR:2026:v7        HOLIDAY_YEAR
 *   ax:name:xmas:2026:v7 AXIS_NAME
 *   s:terms:2026:v1     SKY                  (여기만 알고리즘 버전)
 * </pre>
 *
 * <p>버전을 <b>맨 뒤</b>에 두는 것이 ARCHITECTURE §4.3 의 요구다. 앞에 두면
 * {@code SCAN v7:*} 으로 옛 버전을 쓸어 담고 싶어지고, 그 순간 「개별 삭제를 하지
 * 않는다」는 결정이 무너진다. 옛 키는 <b>지우는 게 아니라 TTL 로 사라진다.</b>
 *
 * <h2>꼬리를 검사하는 까닭</h2>
 *
 * <p>꼬리는 {@code cc} · {@code year} · {@code slug} 처럼 <b>요청에서 온 값</b>으로
 * 만들어진다. §4.1 이 "캐시 키를 만드는 값은 우리가 아는 유한 집합에서 와야 한다"
 * 고 못 박았고, 그 검사는 도메인 쪽에서 먼저 한다. 여기서는 그 검사를 통과한 값이라도
 * <b>키의 모양을 깨뜨릴 수 있는 글자</b>만 막는다 — 공백이 섞이면 Redis 키가
 * 조용히 두 개로 갈라지고, 가장자리에 {@code :} 가 붙으면 {@code h::2026} 처럼
 * 빈 마디가 생겨 다른 키와 겹칠 수 있다.
 */
public final class CacheKey {

    private CacheKey() {
    }

    public static String of(CacheName name, String suffix, long version) {
        if (name == null) {
            throw new IllegalArgumentException("캐시 이름이 없으면 키를 만들 수 없다");
        }
        if (version < 1) {
            throw new IllegalArgumentException("캐시 버전은 1부터다: " + version);
        }
        String tail = normalize(suffix);

        StringBuilder key = new StringBuilder(name.prefix());
        if (!tail.isEmpty()) {
            key.append(':').append(tail);
        }
        return key.append(":v").append(version).toString();
    }

    private static String normalize(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return "";
        }
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            /* isWhitespace 만으로는 NBSP(U+00A0) 를 못 잡는다. 그것이 실제 위험이다 —
               번역된 화면에서 복사한 slug 에 섞여 들어오고, 눈으로는 안 보인다 */
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException("캐시 키 꼬리에 공백이나 제어문자를 넣을 수 없다: '" + suffix + "'");
            }
        }
        if (suffix.startsWith(":") || suffix.endsWith(":") || suffix.contains("::")) {
            throw new IllegalArgumentException("캐시 키 꼬리에 빈 마디가 생긴다: '" + suffix + "'");
        }
        return suffix;
    }
}
