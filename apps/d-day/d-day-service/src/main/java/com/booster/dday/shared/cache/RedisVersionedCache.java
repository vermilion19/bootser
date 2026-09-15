package com.booster.dday.shared.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * {@link VersionedCache} 의 Redis 구현.
 *
 * <h2>템플릿을 둘 쓴다 — 실수하기 쉬운 자리다</h2>
 *
 * <p>값은 {@code RedisTemplate<String, Object>} 로 넣는다. 그 템플릿의 값 직렬화기는
 * 타입 정보를 함께 적는 JSON 이라(storage-redis {@code RedisConfig}) 목록도 레코드도
 * 원래 타입으로 돌아온다.
 *
 * <p><b>버전 카운터만은 {@link StringRedisTemplate} 로 다룬다.</b> 같은 템플릿으로
 * {@code 1} 을 넣으면 Redis 에는 {@code ["java.lang.Long",1]} 같은 문자열이 들어가고,
 * 거기에 {@code INCR} 을 걸면 터진다. 무효화의 심장이 조용히 안 도는 고장이라
 * 템플릿을 아예 나눠 둔다.
 *
 * <h2>{@code sky} 는 카운터를 안 쓴다</h2>
 *
 * <p>{@link CacheNamespace#SKY} 의 버전은 알고리즘 버전 {@code A} 이고 설정값이다.
 * 동기화가 올릴 수 있는 것이 아니라 배포가 올리는 것이므로, 런타임에 올리려 하면
 * 거절한다 (ARCHITECTURE §4.2).
 */
@Slf4j
@Component
public class RedisVersionedCache implements VersionedCache {

    private static final long FIRST_VERSION = 1L;

    private final RedisTemplate<String, Object> values;
    private final StringRedisTemplate versions;
    private final long skyAlgorithmVersion;

    public RedisVersionedCache(RedisTemplate<String, Object> values,
                               StringRedisTemplate versions,
                               @Value("${dday.cache.sky.algorithm-version:1}") long skyAlgorithmVersion) {
        if (skyAlgorithmVersion < FIRST_VERSION) {
            throw new IllegalArgumentException("sky 알고리즘 버전은 1부터다: " + skyAlgorithmVersion);
        }
        this.values = values;
        this.versions = versions;
        this.skyAlgorithmVersion = skyAlgorithmVersion;
    }

    @Override
    public <T> T getOrLoad(CacheName name, String suffix, Class<T> type, Supplier<T> loader) {
        String key = CacheKey.of(name, suffix, version(name.namespace()));

        Object cached = readQuietly(key);
        if (type.isInstance(cached)) {
            return type.cast(cached);
        }
        if (cached != null) {
            /* 담을 때와 읽을 때의 타입이 다르다. 배포 사이에 모양이 바뀐 옛 값이
               버전은 그대로인 채 살아 있는 경우다. 터뜨리지 않고 미스로 친다 —
               캐시가 응답을 못 주는 것과 응답을 못 나가게 하는 것은 다르다 */
            log.warn("[Cache] 담긴 타입이 다르다. 미스로 친다. key={}, 기대={}, 실제={}",
                    key, type.getSimpleName(), cached.getClass().getSimpleName());
        }

        T loaded = loader.get();
        if (loaded == null) {
            return null;
        }
        putQuietly(key, loaded, name.ttl());
        return loaded;
    }

    /**
     * 캐시에서 읽는다. <b>읽다 터지면 미스로 친다.</b>
     *
     * <p>터지는 경우가 실제로 둘 있다.
     *
     * <ol>
     *   <li><b>담긴 값을 되살릴 수 없다.</b> 값 직렬화기의 기본 타이핑이
     *       {@code NON_FINAL} 이라 final 타입은 타입 정보 없이 저장되고, 읽을 때
     *       {@code SerializationException} 이 난다. 옛 배포가 그런 값을 남겼거나
     *       클래스 이름이 바뀐 경우다.</li>
     *   <li><b>Redis 가 없다.</b></li>
     * </ol>
     *
     * <p>둘 다 <b>캐시의 사정</b>이지 응답을 못 줄 이유가 아니다. 그대로 두면
     * 조회가 500 이 되는데, {@code country:all} 처럼 <b>TTL 이 없는 키</b>에서는
     * 그 500 이 저절로 풀리지도 않는다 — 만료로 사라질 길이 없기 때문이다.
     *
     * <p>대신 <b>조용하지 않게</b> 삼킨다. 캐시가 통째로 안 듣는 상태는 부하가
     * 걸려야 보이는 종류의 고장이라, 여기 WARN 이 관측의 유일한 실마리다
     * (§8.2 에 메트릭을 붙일 자리).
     */
    private Object readQuietly(String key) {
        try {
            return values.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("[Cache] 읽지 못했다. 미스로 친다. key={}, 까닭={}", key, e.toString());
            return null;
        }
    }

    /** 못 담아도 응답은 나간다. 다음 요청이 다시 담아 볼 뿐이다 */
    private void putQuietly(String key, Object value, Duration ttl) {
        try {
            put(key, value, ttl);
        } catch (RuntimeException e) {
            log.warn("[Cache] 담지 못했다. key={}, 까닭={}", key, e.toString());
        }
    }

    @Override
    public long version(CacheNamespace namespace) {
        if (namespace == CacheNamespace.SKY) {
            return skyAlgorithmVersion;
        }
        String raw = versions.opsForValue().get(namespace.versionKey());
        if (raw == null) {
            return FIRST_VERSION;
        }
        return Long.parseLong(raw);
    }

    @Override
    public long nextVersion(CacheNamespace namespace) {
        refuseSky(namespace, "다음 버전을 정할");
        return version(namespace) + 1;
    }

    @Override
    public <T> void putAt(CacheName name, long version, String suffix, T value) {
        if (value == null) {
            throw new IllegalArgumentException("널을 심을 수 없다. 워밍이 빈손이면 플립을 막아야 한다");
        }
        put(CacheKey.of(name, suffix, version), value, name.ttl());
    }

    @Override
    public void flipTo(CacheNamespace namespace, long version) {
        refuseSky(namespace, "플립할");

        long current = version(namespace);
        if (version <= current) {
            /* 뒤로 가면 옛 키가 되살아난다. TTL 이 남아 있는 동안 낡은 값이
               다시 나가고, 아무 에러도 안 난다 */
            throw new IllegalArgumentException(
                    "캐시 버전은 앞으로만 간다: " + namespace + " " + current + " -> " + version);
        }
        versions.opsForValue().set(namespace.versionKey(), String.valueOf(version));
        log.info("[Cache] 버전 플립. namespace={}, {} -> {}", namespace, current, version);
    }

    @Override
    public long bump(CacheNamespace namespace) {
        refuseSky(namespace, "올릴");

        String key = namespace.versionKey();
        /* 카운터가 없을 때 그냥 INCR 하면 1 이 되는데, 없을 때의 뜻도 1 이다.
           즉 첫 무효화가 아무것도 무효화하지 못한다 — 조용히. 먼저 1 을 못 박는다 */
        versions.opsForValue().setIfAbsent(key, String.valueOf(FIRST_VERSION));

        Long bumped = versions.opsForValue().increment(key);
        if (bumped == null) {
            throw new IllegalStateException("캐시 버전을 올리지 못했다: " + namespace);
        }
        log.info("[Cache] 버전 올림. namespace={}, -> {}", namespace, bumped);
        return bumped;
    }

    private void put(String key, Object value, Duration ttl) {
        if (ttl == null) {
            values.opsForValue().set(key, value);
            return;
        }
        values.opsForValue().set(key, value, ttl);
    }

    private static void refuseSky(CacheNamespace namespace, String what) {
        if (namespace == CacheNamespace.SKY) {
            throw new UnsupportedOperationException(
                    "sky 의 버전은 알고리즘 버전이고 배포가 올린다. 런타임에 " + what + " 수 없다 (ARCHITECTURE §4.2)");
        }
    }
}
