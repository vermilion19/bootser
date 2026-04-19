package com.booster.queryburstmsa.order.domain.repository;

import com.booster.queryburstmsa.order.domain.entity.OrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    @Query("""
            select o
            from OrderEntity o
            where (:cursor is null or o.id < :cursor)
            order by o.id desc
            """)
    List<OrderEntity> findOrders(@Param("cursor") Long cursor, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findWithItemsByIdempotencyKey(String idempotencyKey);
}
