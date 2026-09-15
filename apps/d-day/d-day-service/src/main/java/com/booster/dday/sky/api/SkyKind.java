package com.booster.dday.sky.api;

/**
 * 하늘 갈래 셋. 캐시 키의 {@code {kind}} 자리가 이것이다 —
 * {@code s:{kind}:{year}:v{A}} (ARCHITECTURE §4.2).
 */
public enum SkyKind {

    /** 절기 스물넷 (B-1) */
    TERMS("terms"),

    /** 삭 · 망 (B-2) */
    MOONS("moons"),

    /** 유성우 극대 (B-3) */
    METEORS("meteors");

    private final String key;

    SkyKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
