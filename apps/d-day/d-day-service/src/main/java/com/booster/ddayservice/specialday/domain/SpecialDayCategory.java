package com.booster.ddayservice.specialday.domain;

import java.util.List;

public enum SpecialDayCategory {
    PUBLIC_HOLIDAY,
    MEMORIAL_DAY,
    SPORTS,
    ENTERTAINMENT,
    MOVIE,
    CUSTOM;

    // 캐시 그룹 정의
    public static final List<SpecialDayCategory> HOLIDAY_GROUP = List.of(PUBLIC_HOLIDAY, MEMORIAL_DAY);
    public static final List<SpecialDayCategory> ENTERTAINMENT_GROUP = List.of(MOVIE, SPORTS, ENTERTAINMENT);
    public static final List<SpecialDayCategory> CUSTOM_GROUP = List.of(CUSTOM);

    public String getCacheGroup() {
        if (HOLIDAY_GROUP.contains(this)) {
            return "holidays";
        } else if (ENTERTAINMENT_GROUP.contains(this)) {
            return "entertainment";
        } else {
            return "others";
        }
    }
}
