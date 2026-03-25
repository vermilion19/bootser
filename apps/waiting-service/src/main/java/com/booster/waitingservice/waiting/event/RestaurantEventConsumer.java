package com.booster.waitingservice.waiting.event;

import com.booster.common.JsonUtils;
import com.booster.waitingservice.waiting.application.RestaurantCacheService;
import com.booster.waitingservice.waiting.domain.RestaurantSnapshot;
import com.booster.waitingservice.waiting.domain.RestaurantSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantEventConsumer {

    private final RestaurantCacheService restaurantCacheService;
    private final RestaurantSnapshotRepository snapshotRepository;
    private final JsonMapper jsonMapper = JsonUtils.MAPPER;

    @KafkaListener(topics = "booster.restaurant.events", groupId = "waiting-service-group")
    @Transactional
    public void consume(String message) {
        RestaurantEvent event = jsonMapper.readValue(message, RestaurantEvent.class);
        log.info("식당 이벤트 수신: type={}, id={}, name={}", event.eventType(), event.id(), event.name());

        switch (event.eventType()) {
            case "RESTAURANT_CREATED", "RESTAURANT_UPDATED" -> upsert(event.id(), event.name());
            case "RESTAURANT_DELETED" -> delete(event.id());
            default -> log.warn("알 수 없는 이벤트 타입: {}", event.eventType());
        }
    }

    private void upsert(Long id, String name) {
        snapshotRepository.findById(id).ifPresentOrElse(
                snapshot -> snapshot.update(name),
                () -> snapshotRepository.save(RestaurantSnapshot.of(id, name))
        );
        restaurantCacheService.updateCache(id, name);
        log.info("식당 정보 동기화 완료: id={}, name={}", id, name);
    }

    private void delete(Long id) {
        snapshotRepository.deleteById(id);
        restaurantCacheService.evictCache(id);
        log.info("식당 정보 삭제 완료: id={}", id);
    }

    // Restaurant Service의 RestaurantEvent와 동일한 구조
    record RestaurantEvent(String eventType, Long id, String name) {}
}
