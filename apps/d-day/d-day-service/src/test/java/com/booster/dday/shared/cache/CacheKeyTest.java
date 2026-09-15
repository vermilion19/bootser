package com.booster.dday.shared.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 캐시 키의 모양. ARCHITECTURE §4.2 의 표가 여기 그대로 들어와 있다.
 *
 * <p><b>키 모양은 공표된 약속이다.</b> 운영에서 {@code redis-cli} 로 들여다볼 때
 * 문서의 표를 보고 치기 때문이고, 그래서 한 글자만 달라져도 여기가 깨져야 한다.
 */
class CacheKeyTest {

    @Nested
    @DisplayName("레지스트리 열 줄의 키 모양")
    class Shape {

        /**
         * 문서의 표를 그대로 옮긴 것이다. 왼쪽이 {@link CacheName}, 가운데가 꼬리,
         * 오른쪽이 ARCHITECTURE §4.2 가 적어 둔 키다.
         */
        @ParameterizedTest(name = "{0} + ''{1}'' -> {2}")
        @CsvSource(delimiter = '|', value = {
                "COUNTRY_ALL          |                | c:all:v7",
                "HOLIDAY_YEAR         | KR:2026        | h:KR:2026:v7",
                "HOLIDAY_LONG_WEEKEND | KR:2026        | lw:KR:2026:v7",
                "HOLIDAY_ON_DATE      | 2026-03-01     | hd:2026-03-01:v7",
                "AXIS_NAME_HUB        | 2026           | ax:names:2026:v7",
                "AXIS_NAME            | christmas:2026 | ax:name:christmas:2026:v7",
                "AXIS_RANK            | 2026           | ax:rank:2026:v7",
                "AXIS_WEEKDAY         | 2026           | ax:weekday:2026:v7",
                "SKY                  | terms:2026     | s:terms:2026:v7",
                "LABEL_HOLIDAY_NAME   | ko             | lbl:ko:v7",
        })
        @DisplayName("§4.2 의 표와 글자 하나까지 같다")
        void matchesRegistryTable(String name, String suffix, String expected) {
            assertThat(CacheKey.of(CacheName.valueOf(name.trim()), suffix == null ? "" : suffix.trim(), 7))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("꼬리가 없으면 빈 마디를 남기지 않는다")
        void noEmptySegmentWhenSuffixAbsent() {
            assertThat(CacheKey.of(CacheName.COUNTRY_ALL, "", 1)).isEqualTo("c:all:v1");
            assertThat(CacheKey.of(CacheName.COUNTRY_ALL, null, 1)).isEqualTo("c:all:v1");
        }

        /**
         * 버전이 <b>뒤</b>에 있어야 한다는 것이 §4.3 의 요구다. 앞에 두면
         * {@code SCAN v7:*} 으로 옛 버전을 쓸어 담고 싶어지고, 그 순간
         * 「개별 삭제를 하지 않는다」가 무너진다.
         */
        @ParameterizedTest
        @EnumSource(CacheName.class)
        @DisplayName("어느 캐시든 버전은 맨 뒤에 붙는다")
        void versionIsAlwaysLast(CacheName name) {
            assertThat(CacheKey.of(name, "x", 42)).endsWith(":v42");
        }

        @ParameterizedTest
        @EnumSource(CacheName.class)
        @DisplayName("접두사가 서로를 잡아먹지 않는다")
        void prefixesDoNotShadowEachOther(CacheName name) {
            /* ax:name 과 ax:names 처럼 한쪽이 다른 쪽의 접두사인 쌍이 실제로 있다.
               접두사만으로는 구별이 안 되고, 마디 경계(:)까지 봐야 구별된다 */
            String key = CacheKey.of(name, "x", 1);

            long ambiguous = Arrays.stream(CacheName.values())
                    .filter(other -> other != name)
                    .map(other -> other.prefix() + ":")
                    .filter(key::startsWith)
                    .count();

            assertThat(ambiguous)
                    .as("%s 의 키 '%s' 가 다른 캐시의 마디 경계로도 읽힌다", name, key)
                    .isZero();
        }
    }

    @Nested
    @DisplayName("막는 것")
    class Guards {

        /**
         * 꼬리는 요청에서 온 값으로 만들어진다 (§4.1). 공백이 섞이면 같은 뜻의
         * 요청이 Redis 에서 서로 다른 키가 되고, 아무 에러도 안 난다.
         */
        @ParameterizedTest
        @ValueSource(strings = {"KR 2026", "KR\t2026", "KR\n2026", "KR 2026"})
        @DisplayName("공백이 든 꼬리를 거절한다")
        void refusesWhitespace(String suffix) {
            assertThatThrownBy(() -> CacheKey.of(CacheName.HOLIDAY_YEAR, suffix, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {":KR", "KR:", "KR::2026", ":"})
        @DisplayName("빈 마디가 생기는 꼬리를 거절한다")
        void refusesEmptySegment(String suffix) {
            assertThatThrownBy(() -> CacheKey.of(CacheName.HOLIDAY_YEAR, suffix, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("버전 0 이나 음수는 없다 — 첫 버전이 1이다")
        void refusesNonPositiveVersion() {
            assertThatThrownBy(() -> CacheKey.of(CacheName.AXIS_RANK, "2026", 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CacheKey.of(CacheName.AXIS_RANK, "2026", -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이름이 없으면 키가 없다")
        void refusesMissingName() {
            assertThatThrownBy(() -> CacheKey.of(null, "2026", 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("레지스트리 자체")
    class Registry {

        @Test
        @DisplayName("TTL 이 없는 것은 country 하나뿐이다 (§4.2)")
        void onlyCountryHasNoTtl() {
            assertThat(Arrays.stream(CacheName.values()).filter(n -> n.ttl() == null).toList())
                    .containsExactly(CacheName.COUNTRY_ALL);
        }

        @Test
        @DisplayName("sky 만 SKY 네임스페이스를 쓴다 — 버전의 뜻이 다르기 때문이다")
        void skyStandsAlone() {
            assertThat(Arrays.stream(CacheName.values())
                    .filter(n -> n.namespace() == CacheNamespace.SKY)
                    .toList())
                    .containsExactly(CacheName.SKY);
        }

        @Test
        @DisplayName("버전 키에는 서비스 이름이 붙는다 — 남의 Redis 와 섞이면 무효화가 통째로 어긋난다")
        void versionKeysAreNamespaced() {
            for (CacheNamespace ns : CacheNamespace.values()) {
                assertThat(ns.versionKey()).startsWith("dday:cache:ver:");
            }
            assertThat(CacheNamespace.AXIS.versionKey()).isEqualTo("dday:cache:ver:axis");
        }
    }
}
