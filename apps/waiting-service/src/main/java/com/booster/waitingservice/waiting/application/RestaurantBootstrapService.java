package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.domain.RestaurantSnapshot;
import com.booster.waitingservice.waiting.domain.RestaurantSnapshotRepository;
import com.booster.waitingservice.waiting.infastructure.RestaurantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 서비스 기동 시 Restaurant Service로부터 식당 정보를 일괄 동기화.
 * 런타임에는 Kafka 이벤트로 증분 갱신되므로, 이 클래스는 Bootstrap 전용으로만 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantBootstrapService {

    private final RestaurantClient restaurantClient;
    private final RestaurantSnapshotRepository snapshotRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "restaurant:name:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncOnStartup() {
        log.info("식당 정보 Bootstrap 동기화 시작");
        try {
            List<RestaurantClient.RestaurantResponse> restaurants =
                    restaurantClient.getAllRestaurants().data();

            if (restaurants == null || restaurants.isEmpty()) {
                log.info("동기화할 식당 정보가 없습니다.");
                return;
            }

            for (RestaurantClient.RestaurantResponse r : restaurants) {
                upsert(r.id(), r.name());
            }

            log.info("식당 정보 Bootstrap 동기화 완료: {}건", restaurants.size());
        } catch (Exception e) {
            // 부트스트랩 실패는 치명적이지 않음
            // Kafka 이벤트가 들어오면 점진적으로 채워짐
            log.warn("Bootstrap 동기화 실패 (Kafka 이벤트로 점진 복구됨): {}", e.getMessage());
        }
    }

    private void upsert(Long id, String name) {
        snapshotRepository.findById(id).ifPresentOrElse(
                snapshot -> snapshot.update(name),
                () -> snapshotRepository.save(RestaurantSnapshot.of(id, name))
        );
        redisTemplate.opsForValue().set(KEY_PREFIX + id, name, CACHE_TTL);
    }
}
