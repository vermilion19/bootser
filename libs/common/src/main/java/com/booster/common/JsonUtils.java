package com.booster.common;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonUtils {

    // 1. 공통 설정을 담은 빌더 팩토리 (내부용)
    private static JsonMapper.Builder baseBuilder() {

        // [핵심 수정] Jackson 3.0.0-rc2 버그 우회
        // 포맷터를 명시적으로 지정하여, 내부적으로 사라진 SerializationFeature 필드를 참조하지 않도록 강제합니다.
        SimpleModule fixModule = new SimpleModule();
        fixModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));


        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(fixModule)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // 2. 기본 빌더에 Redis 전용 설정만 추가해서 생성
    public static final ObjectMapper MAPPER_FOR_REDIS = baseBuilder()
            .activateDefaultTyping(
                    BasicPolymorphicTypeValidator.builder()
                            .allowIfBaseType(Object.class)
                            .build(),
                    DefaultTyping.NON_FINAL
            )
            .build();

    // 3. 기본 빌더 그대로 생성
    public static final ObjectMapper MAPPER = baseBuilder().build();


    private JsonUtils() {
        // 유틸리티 클래스이므로 인스턴스화 방지
    }

    // 객체를 JSON 문자열로 변환
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization error", e);
        }
    }

    // JSON 문자열을 객체로 변환
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization error", e);
        }
    }
}
