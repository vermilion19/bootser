package com.booster.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUtilsTest {

    @Test
    @DisplayName("LocalDateTime 직렬화 및 역직렬화 테스트")
    void localDateTimeTest() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 20, 30, 0);
        TestUser user = new TestUser("Juhong Kim", now);

        // when
        String json = JsonUtils.toJson(user);
        TestUser deserializedUser = JsonUtils.fromJson(json, TestUser.class);

        // then
        // 1. JSON 문자열에 날짜가 ISO-8601 형식으로 포함되어 있는지 확인
        assertThat(json).contains("2026-01-02T20:30:00");

        // 2. 역직렬화된 객체의 날짜가 원본과 일치하는지 확인
        assertThat(deserializedUser.getCreatedAt()).isEqualTo(now);
        assertThat(deserializedUser.getName()).isEqualTo("Juhong Kim");
    }

    @Test
    @DisplayName("DTO에 없는 필드가 JSON에 포함되어 있어도 에러 없이 무시해야 한다")
    void unknownPropertiesTest() {
        // given
        // DTO에는 없는 'gender' 필드가 포함된 JSON 문자열
        String jsonWithExtraField = "{\"name\":\"Juhong\",\"createdAt\":\"2026-01-02T20:30:00\",\"gender\":\"M\"}";

        // when
        TestUser user = JsonUtils.fromJson(jsonWithExtraField, TestUser.class);

        // then
        assertThat(user.getName()).isEqualTo("Juhong");
        assertThat(user.getCreatedAt()).isNotNull();
        // gender 필드는 무시되고 에러가 발생하지 않아야 함
    }

    // 테스트용 내부 클래스
    static class TestUser {
        private String name;
        private LocalDateTime createdAt;

        public TestUser() {} // Jackson 역직렬화용 기본 생성자

        public TestUser(String name, LocalDateTime createdAt) {
            this.name = name;
            this.createdAt = createdAt;
        }

        public String getName() { return name; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
