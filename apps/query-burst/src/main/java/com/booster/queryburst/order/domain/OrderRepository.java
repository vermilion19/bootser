package com.booster.queryburst.order.domain;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Orders, Long> {

    // OFFSET 기반 회원별 주문 목록 (v1 — COUNT 포함)
    Slice<Orders> findSliceByMemberId(Long memberId, Pageable pageable);
}
