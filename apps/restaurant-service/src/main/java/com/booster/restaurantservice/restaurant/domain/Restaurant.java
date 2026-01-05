package com.booster.restaurantservice.restaurant.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "restaurant")
public class Restaurant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false) // 물리적 최대 수용 인원
    private int capacity;

    @Column(nullable = false) // 현재 입장 인원 (동시성 제어 중요!)
    private int currentOccupancy;

    @Column(nullable = false) // 대기열 제한 수
    private int maxWaitingLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RestaurantStatus status;

    // 생성자 (Factory Method 패턴 사용 권장)
    public static Restaurant create(String name, int capacity, int maxWaitingLimit) {
        Restaurant restaurant = new Restaurant();
        restaurant.name = name;
        restaurant.capacity = capacity;
        restaurant.maxWaitingLimit = maxWaitingLimit;
        restaurant.currentOccupancy = 0; // 초기값 0
        restaurant.status = RestaurantStatus.CLOSED; // 생성 시 기본은 닫힘
        return restaurant;
    }

    public void updateInfo(String name, Integer capacity, Integer maxWaitingLimit) {
        if (name != null) this.name = name;
        // 수용 인원을 줄일 때 현재 인원보다 작으면 안 된다는 로직 등이 들어갈 수 있음
        if (capacity != null) this.capacity = capacity;
        if (maxWaitingLimit != null) this.maxWaitingLimit = maxWaitingLimit;
    }

    // 영업 시작
    public void open() {
        this.status = RestaurantStatus.OPEN;
    }

    // 영업 종료
    public void close() {
        this.status = RestaurantStatus.CLOSED;
    }
}
