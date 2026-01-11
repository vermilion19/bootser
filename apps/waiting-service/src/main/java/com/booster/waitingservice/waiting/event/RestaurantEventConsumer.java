package com.booster.waitingservice.waiting.event;

import com.booster.common.JsonUtils;
import com.booster.waitingservice.waiting.application.RestaurantCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantEventConsumer {

    private final RestaurantCacheService restaurantCacheService;
    private final JsonMapper jsonMapper = JsonUtils.MAPPER;

    @KafkaListener(topics = "booster.restaurant.events", groupId = "waiting-service-group")
    public void consume(String message) {
        // 1. 역직렬화
        RestaurantUpdatedEvent event = jsonMapper.readValue(message, RestaurantUpdatedEvent.class);

        // 2. 비즈니스 로직 (캐시 갱신)
        log.info("식당 정보 변경 감지: {} -> {}", event.id(), event.newName());
        restaurantCacheService.updateCache(event.id(), event.newName());

    }

    // DTO (Producer와 동일한 구조)
    record RestaurantUpdatedEvent(Long id, String newName) {}
}
