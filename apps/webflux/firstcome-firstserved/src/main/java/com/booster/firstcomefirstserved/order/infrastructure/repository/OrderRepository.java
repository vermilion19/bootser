package com.booster.firstcomefirstserved.order.infrastructure.repository;

import com.booster.firstcomefirstserved.order.infrastructure.entity.OrderEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, Long> {

}
