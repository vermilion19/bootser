package com.booster.restaurantservice.restaurant.domain;

public enum RestaurantStatus {
    OPEN("영업 중"),
    WAITING_CLOSED("대기 마감"), // 대기열만 닫힘 (안에 손님은 있음)
    CLOSED("영업 종료");

    private final String description;

    RestaurantStatus(String description) {
        this.description = description;
    }
}
