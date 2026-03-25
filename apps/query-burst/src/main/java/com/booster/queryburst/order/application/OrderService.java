package com.booster.queryburst.order.application;

import com.booster.queryburst.order.domain.OrderItemQueryRepository;
import com.booster.queryburst.order.domain.OrderItemRepository;
import com.booster.queryburst.order.domain.OrderQueryRepository;
import com.booster.queryburst.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemQueryRepository orderItemQueryRepository;

}
