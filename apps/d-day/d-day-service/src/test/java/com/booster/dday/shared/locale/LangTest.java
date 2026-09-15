package com.booster.dday.shared.locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 언어 고르기 (SPEC §10.8).
 *
 * <p>여기서 무는 것은 <b>못 알아들었을 때 어떻게 하는가</b>이다. 언어를 못 알아들었다고
 * 조회를 거절하면, 브라우저가 보낸 낯선 {@code Accept-Language} 하나로 멀쩡한 요청이
 * 400 이 된다.
 */
class LangTest {

    @ParameterizedTest
    @CsvSource({
            "ko,    KO",
            "ko-KR, KO",
            "KO,    KO",
            "en,    EN",
            "en-US, EN",
    })
    @DisplayName("?lang= 을 알아듣는다")
    void readsQueryParam(String value, Lang expected) {
        assertThat(Lang.resolve(value, null)).isEqualTo(expected);
    }

    @Test
    @DisplayName("?lang= 이 Accept-Language 를 덮는다")
    void queryParamOverridesHeader() {
        assertThat(Lang.resolve("ko", "en-US,en;q=0.9")).isEqualTo(Lang.KO);
        assertThat(Lang.resolve("en", "ko-KR,ko;q=0.9")).isEqualTo(Lang.EN);
    }

    @Test
    @DisplayName("헤더에서 앞에서부터 아는 언어를 찾는다")
    void readsHeaderInOrder() {
        assertThat(Lang.resolve(null, "ko-KR,ko;q=0.9,en;q=0.8")).isEqualTo(Lang.KO);
        assertThat(Lang.resolve(null, "en-US,en;q=0.9,ko;q=0.8")).isEqualTo(Lang.EN);
        assertThat(Lang.resolve(null, "fr-FR,fr;q=0.9,en;q=0.8"))
                .as("모르는 것은 건너뛰고 아는 것을 찾는다")
                .isEqualTo(Lang.EN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "zh-CN", "무엇", "", "   "})
    @DisplayName("못 알아들으면 기본값이다 — 거절하지 않는다")
    void unknownFallsBack(String value) {
        assertThat(Lang.resolve(value, null)).isEqualTo(Lang.DEFAULT);
    }

    @Test
    @DisplayName("둘 다 없으면 한국어다")
    void nothingGivenIsKorean() {
        assertThat(Lang.resolve(null, null)).isEqualTo(Lang.KO);
    }
}
