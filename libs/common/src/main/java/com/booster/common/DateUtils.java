package com.booster.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 인스턴스화 방지 (Utility Class)
    private DateUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String toString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DEFAULT_FORMATTER);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}