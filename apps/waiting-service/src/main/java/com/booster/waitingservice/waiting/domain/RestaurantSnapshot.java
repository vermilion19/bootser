package com.booster.waitingservice.waiting.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantSnapshot extends BaseEntity {

    @Id
    private Long id; // restaurantId와 동일

    @Column(nullable = false)
    private String name;

    public static RestaurantSnapshot of(Long id, String name) {
        RestaurantSnapshot snapshot = new RestaurantSnapshot();
        snapshot.id = id;
        snapshot.name = name;
        return snapshot;
    }

    public void update(String name) {
        this.name = name;
    }
}
