package com.booster.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.datatype.jsr310.JavaTimeModule;
import tools.jackson.databind.ObjectMapper;

public class JsonUtils {

    // 1. 공통 설정을 가진 ObjectMapper를 외부에서도 꺼낼 수 있게 public으로 둡니다.
    public static final ObjectMapper COMMON_MAPPER = createBaseMapper();

    private static ObjectMapper createBaseMapper() {
                return JsonMapper.builder()
                .addModule(new JavaTimeModule()) // 날짜 모듈 등록
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build(),
                        DefaultTyping.NON_FINAL
                )
                .build();
    }

//    // 1. 전역적으로 공유할 ObjectMapper (성능을 위해 static final로 관리)
//    private static final ObjectMapper MAPPER = new ObjectMapper()
//            // Java 8 날짜/시간 모듈 등록 (Java 25와 호환)
//            .registerModule(new JavaTimeModule())
//            // 날짜를 타임스탬프(숫자)가 아닌 ISO-8601 문자열로 저장
//            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
//            // DTO에 없는 필드가 JSON에 있어도 에러 내지 않음 (유연한 확장성)
//            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//
//    private JsonUtils() {
//        // 유틸리티 클래스이므로 인스턴스화 방지
//    }
//
//    // 객체를 JSON 문자열로 변환
//    public static String toJson(Object obj) {
//        try {
//            return MAPPER.writeValueAsString(obj);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException("JSON serialization error", e);
//        }
//    }
//
//    // JSON 문자열을 객체로 변환
//    public static <T> T fromJson(String json, Class<T> clazz) {
//        try {
//            return MAPPER.readValue(json, clazz);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException("JSON deserialization error", e);
//        }
//    }
}
