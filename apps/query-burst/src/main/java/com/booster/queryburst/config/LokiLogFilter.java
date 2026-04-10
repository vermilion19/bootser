package com.booster.queryburst.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Loki로 전송할 로그를 선별하는 필터.
 *
 * 허용 조건:
 * - WARN/ERROR: 항상 전송
 * - INFO: 비즈니스 키워드가 포함된 경우만 전송
 *
 * 제외 조건:
 * - com.booster 패키지 외 로그 (프레임워크 로그)
 * - INFO 로그 중 비즈니스 키워드 없는 경우
 */
public class LokiLogFilter extends Filter<ILoggingEvent> {

    private static final String BUSINESS_PACKAGE = "com.booster";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        Level level = event.getLevel();

        // com.booster 패키지 외 로그 제외 (Spring, Hibernate 등 프레임워크 로그)
        if (loggerName == null || !loggerName.startsWith(BUSINESS_PACKAGE)) {
            return FilterReply.DENY;
        }

        // WARN/ERROR는 항상 Loki 전송
        if (level.isGreaterOrEqual(Level.WARN)) {
            return FilterReply.ACCEPT;
        }

        // INFO는 비즈니스 키워드가 포함된 경우만 전송
        if (level.equals(Level.INFO)) {
            String message = event.getFormattedMessage();
            if (isBusinessCritical(message)) {
                return FilterReply.ACCEPT;
            }
            return FilterReply.DENY;
        }

        return FilterReply.DENY;
    }

    private boolean isBusinessCritical(String message) {
        if (message == null) return false;
        return message.contains("rate-limit")
            || message.contains("circuit-breaker")
            || message.contains("order")
            || message.contains("query-burst");
    }
}
