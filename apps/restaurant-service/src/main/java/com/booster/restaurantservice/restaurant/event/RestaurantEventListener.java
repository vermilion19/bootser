package com.booster.restaurantservice.restaurant.event;

import com.booster.core.web.event.WaitingEvent;
import com.booster.restaurantservice.restaurant.application.RestaurantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestaurantEventListener {
    private final RestaurantService restaurantService;

    @KafkaListener(topics = "${app.kafka.topics.waiting-events}", groupId = "restaurant-service-group")
    public void handleWaitingEvent(WaitingEvent event) {
        log.info("📨 Kafka 이벤트 수신: type={}, restaurantId={}, partySize={}",
                event.type(), event.restaurantId(), event.partySize());

        // '입장(ENTER)' 이벤트인 경우에만 처리
        if (event.type() == WaitingEvent.EventType.ENTER) {
            try {
                // 식당 서비스의 핵심 로직 호출 (식당ID, 인원수)
                restaurantService.enter(event.restaurantId(), event.partySize());

                log.info("식당 입장 처리 완료 (Current Occupancy Updated)");
            } catch (Exception e) {
                log.error("입장 처리 중 오류 발생: {}", e.getMessage());
                throw e;
            }
        }
    }
}
