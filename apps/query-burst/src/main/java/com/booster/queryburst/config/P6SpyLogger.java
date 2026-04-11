package com.booster.queryburst.config;

import com.p6spy.engine.spy.appender.Slf4JLogger;

public class P6SpyLogger extends Slf4JLogger {

    @Override
    public void logText(String text) {
        if (text != null && !text.isBlank()) {
            super.logText(text);
        }
    }
}
