package com.booster.minizuulservice.server;

import io.netty.util.AttributeKey;

public class ProxyAttributes {
    public static final AttributeKey<Long> START_TIME = AttributeKey.valueOf("startTime");

    // 요청 URL을 저장할 키 (나중에 로그 찍을 때 쓰려고)
    public static final AttributeKey<String> REQUEST_URI = AttributeKey.valueOf("requestUri");

    // [P2] HTTP Method (Access Log 개선용)
    public static final AttributeKey<String> HTTP_METHOD = AttributeKey.valueOf("httpMethod");

    // [P2] HTTP Status Code (응답 후 기록)
    public static final AttributeKey<Integer> HTTP_STATUS = AttributeKey.valueOf("httpStatus");
}
