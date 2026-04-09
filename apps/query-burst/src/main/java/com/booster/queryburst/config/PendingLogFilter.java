package com.booster.queryburst.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Outbox 스케줄러의 Hibernate 로그를 필터링하는 Logback Filter.
 *
 * 필터링 조건:
 * 1. 스케줄러 스레드(scheduling-*)에서 발생하고
 * 2. Hibernate 로거(org.hibernate)이거나 outbox_event 테이블 관련 쿼리인 경우
 */
public class PendingLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String threadName = event.getThreadName();
        String loggerName = event.getLoggerName();
        String message = event.getFormattedMessage();

        // 스케줄러 스레드의 Hibernate SQL 로그 필터링
        if (threadName != null && threadName.startsWith("scheduling-")) {
            if (loggerName != null && loggerName.startsWith("org.hibernate")) {
                return FilterReply.DENY;
            }
            // outbox_event 테이블 쿼리 필터링
            if (message != null && message.contains("outbox_event")) {
                return FilterReply.DENY;
            }
        }

        return FilterReply.NEUTRAL;
    }
}
