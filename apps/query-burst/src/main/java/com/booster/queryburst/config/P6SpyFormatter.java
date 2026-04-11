package com.booster.queryburst.config;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

public class P6SpyFormatter implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                 String category, String prepared, String sql, String url) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        return "%dms | %s".formatted(elapsed, sql.trim());
    }
}