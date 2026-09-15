package com.booster.dday.shared.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 버전 플립. Redis 를 띄우지 않는다 — 여기서 무는 것은 <b>키를 어느 버전으로
 * 만드는가</b>와 <b>언제 올리기를 거절하는가</b>이고, 둘 다 Redis 가 없어도 답이 정해진다.
 *
 * <p>Docker 가 없는 자리에서도 이 규칙들이 지켜지는지 물을 수 있어야 한다. 실제
 * Redis 와의 대화(직렬화 · TTL 적용)는 착수 6·7 의 통합 테스트가 문다.
 */
class RedisVersionedCacheTest {

    private RedisTemplate<String, Object> values;
    private StringRedisTemplate versions;
    private ValueOperations<String, Object> valueOps;
    private ValueOperations<String, String> versionOps;

    /** Redis 대신 쓰는 아주 작은 맵. 버전 카운터의 실제 상태를 흉내 낸다 */
    private Map<String, String> versionStore;

    private VersionedCache cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        values = mock(RedisTemplate.class);
        versions = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        versionOps = mock(ValueOperations.class);
        versionStore = new HashMap<>();

        when(values.opsForValue()).thenReturn(valueOps);
        when(versions.opsForValue()).thenReturn(versionOps);
        when(versionOps.get(anyString())).thenAnswer(inv -> versionStore.get(inv.getArgument(0, String.class)));
        when(versionOps.setIfAbsent(anyString(), anyString())).thenAnswer(inv ->
                versionStore.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        doAnswer(inv -> {
            versionStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(versionOps).set(anyString(), anyString());
        when(versionOps.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = Long.parseLong(versionStore.getOrDefault(key, "0")) + 1;
            versionStore.put(key, String.valueOf(next));
            return next;
        });

        cache = new RedisVersionedCache(values, versions, 1);
    }

    @Nested
    @DisplayName("버전")
    class Version {

        @Test
        @DisplayName("카운터가 없으면 1 이다")
        void startsAtOne() {
            assertThat(cache.version(CacheNamespace.AXIS)).isEqualTo(1);
        }

        /**
         * <b>이 테스트가 이 클래스에서 제일 중요하다.</b>
         *
         * <p>카운터가 없을 때 그냥 {@code INCR} 하면 0 에서 1 이 되는데, 없을 때의
         * 뜻도 1 이다. 즉 <b>첫 무효화가 아무것도 무효화하지 못한다.</b> 키가 그대로라
         * 옛 값이 그대로 나가고, 에러도 로그도 없다. 동기화가 처음 도는 날에
         * 정확히 한 번 일어나는 고장이다.
         */
        @Test
        @DisplayName("첫 bump 도 실제로 버전을 바꾼다")
        void firstBumpActuallyInvalidates() {
            long before = cache.version(CacheNamespace.HOLIDAY);

            long after = cache.bump(CacheNamespace.HOLIDAY);

            assertThat(before).isEqualTo(1);
            assertThat(after).isEqualTo(2);
        }

        @Test
        @DisplayName("네임스페이스마다 따로 센다")
        void countedPerNamespace() {
            cache.bump(CacheNamespace.AXIS);
            cache.bump(CacheNamespace.AXIS);

            assertThat(cache.version(CacheNamespace.AXIS)).isEqualTo(3);
            assertThat(cache.version(CacheNamespace.HOLIDAY)).isEqualTo(1);
        }

        @Test
        @DisplayName("버전 카운터에는 TTL 을 걸지 않는다")
        void versionKeyNeverExpires() {
            cache.bump(CacheNamespace.AXIS);

            /* 카운터가 만료되면 버전이 1 로 돌아가고, TTL 이 남은 옛 키가 통째로
               되살아난다. set(key, value, ttl) 로 가는 길이 없어야 한다 */
            verify(versionOps, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("먼저 심고 나서 뒤집는다 (§4.3)")
    class WriteNewThenFlip {

        @Test
        @DisplayName("nextVersion 은 정하기만 하고 공표하지 않는다")
        void nextVersionDoesNotPublish() {
            long next = cache.nextVersion(CacheNamespace.AXIS);

            assertThat(next).isEqualTo(2);
            assertThat(cache.version(CacheNamespace.AXIS))
                    .as("아직 뒤집지 않았으므로 사용자는 옛 버전을 봐야 한다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("심는 것은 다음 버전의 키에 들어간다")
        void plantsIntoFutureKey() {
            long next = cache.nextVersion(CacheNamespace.AXIS);

            cache.putAt(CacheName.AXIS_RANK, next, "2026", List.of("KR"));

            verify(valueOps).set(eq("ax:rank:2026:v2"), any(), eq(Duration.ofHours(24)));
        }

        @Test
        @DisplayName("뒤집고 나면 읽기가 새 키를 본다 — 그래서 미스가 0이다")
        void afterFlipReadersSeeTheWarmedKey() {
            long next = cache.nextVersion(CacheNamespace.AXIS);
            cache.putAt(CacheName.AXIS_RANK, next, "2026", "warmed");
            when(valueOps.get("ax:rank:2026:v2")).thenReturn("warmed");

            cache.flipTo(CacheNamespace.AXIS, next);

            AtomicInteger loaderCalls = new AtomicInteger();
            String read = cache.getOrLoad(CacheName.AXIS_RANK, "2026", String.class, () -> {
                loaderCalls.incrementAndGet();
                return "computed";
            });

            assertThat(read).isEqualTo("warmed");
            assertThat(loaderCalls).hasValue(0);
        }

        @Test
        @DisplayName("뒤로는 못 간다 — 옛 키가 TTL 안에 되살아난다")
        void refusesFlipBackwards() {
            cache.bump(CacheNamespace.AXIS);   // 2

            assertThatThrownBy(() -> cache.flipTo(CacheNamespace.AXIS, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cache.flipTo(CacheNamespace.AXIS, 2))
                    .as("같은 버전으로 뒤집는 것은 아무 일도 안 하는 것이라 뜻이 없다")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈손으로 심지 않는다 — 워밍이 실패했으면 플립을 막아야 한다")
        void refusesPlantingNull() {
            assertThatThrownBy(() -> cache.putAt(CacheName.AXIS_RANK, 2, "2026", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("sky 는 런타임에 안 올라간다 (§4.2)")
    class SkyIsDeployVersioned {

        @Test
        @DisplayName("설정값이 그대로 버전이 된다")
        void versionComesFromConfiguration() {
            VersionedCache deployed = new RedisVersionedCache(values, versions, 7);

            assertThat(deployed.version(CacheNamespace.SKY)).isEqualTo(7);
            verify(versionOps, never()).get(CacheNamespace.SKY.versionKey());
        }

        @Test
        @DisplayName("키에 그 버전이 실린다")
        void versionRidesTheKey() {
            VersionedCache deployed = new RedisVersionedCache(values, versions, 7);

            deployed.getOrLoad(CacheName.SKY, "terms:2026", String.class, () -> "x");

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(valueOps).set(key.capture(), any(), any(Duration.class));
            assertThat(key.getValue()).isEqualTo("s:terms:2026:v7");
        }

        @Test
        @DisplayName("올리려 하면 거절한다 — 동기화가 올릴 수 있는 값이 아니다")
        void refusesRuntimeBump() {
            assertThatThrownBy(() -> cache.bump(CacheNamespace.SKY))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> cache.flipTo(CacheNamespace.SKY, 2))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> cache.nextVersion(CacheNamespace.SKY))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("알고리즘 버전 0 으로는 뜨지 않는다")
        void refusesZeroAlgorithmVersion() {
            assertThatThrownBy(() -> new RedisVersionedCache(values, versions, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("읽기")
    class Read {

        @Test
        @DisplayName("있으면 로더를 안 부른다")
        void hitSkipsLoader() {
            when(valueOps.get("h:KR:2026:v1")).thenReturn("cached");
            AtomicInteger calls = new AtomicInteger();

            String got = cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> {
                calls.incrementAndGet();
                return "loaded";
            });

            assertThat(got).isEqualTo("cached");
            assertThat(calls).hasValue(0);
        }

        @Test
        @DisplayName("없으면 로더를 부르고 TTL 과 함께 담는다")
        void missLoadsAndStores() {
            String got = cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> "loaded");

            assertThat(got).isEqualTo("loaded");
            verify(valueOps).set("h:KR:2026:v1", "loaded", Duration.ofDays(7));
        }

        @Test
        @DisplayName("TTL 이 없는 캐시는 만료 없이 담는다")
        void ttlLessCacheStoresForever() {
            cache.getOrLoad(CacheName.COUNTRY_ALL, "", String.class, () -> "seed");

            verify(valueOps).set("c:all:v1", "seed");
            verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
        }

        /**
         * 여기까지 온 {@code null} 은 정상 값이 아니라 사고다 — 없는 값으로 오는
         * 요청은 §4.1 이 캐시에 닿기 전에 잘라내기로 했다. 담으면 사고가 TTL 만큼 굳는다.
         */
        @Test
        @DisplayName("로더가 빈손이면 담지 않는다")
        void neverCachesNull() {
            String got = cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> null);

            assertThat(got).isNull();
            verify(valueOps, never()).set(anyString(), any());
            verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
        }

        @Test
        @DisplayName("담긴 타입이 다르면 터뜨리지 않고 다시 계산한다")
        void typeMismatchIsTreatedAsMiss() {
            when(valueOps.get("h:KR:2026:v1")).thenReturn(42);   // 배포 사이에 모양이 바뀐 옛 값

            String got = cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> "loaded");

            assertThat(got).isEqualTo("loaded");
            verify(valueOps).set("h:KR:2026:v1", "loaded", Duration.ofDays(7));
        }

        /**
         * 읽다 터지는 경우가 실제로 둘 있다. <b>담긴 값을 되살릴 수 없거나</b>
         * (final 타입이 타입 정보 없이 저장된 옛 값 — {@code CountryCatalog} 주석),
         * <b>Redis 가 없거나.</b>
         *
         * <p>둘 다 캐시의 사정이지 응답을 못 줄 이유가 아니다. 그대로 두면 조회가
         * 500 이 되는데, TTL 이 없는 키에서는 <b>그 500 이 저절로 풀리지도 않는다.</b>
         */
        @Test
        @DisplayName("읽다 터지면 미스로 친다 — 캐시가 응답을 막아서는 안 된다")
        void readFailureFallsBackToLoader() {
            when(valueOps.get("h:KR:2026:v1")).thenThrow(new IllegalStateException("직렬화 실패"));

            String got = cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> "loaded");

            assertThat(got).isEqualTo("loaded");
        }

        @Test
        @DisplayName("담다 터져도 응답은 나간다")
        void writeFailureDoesNotBreakTheResponse() {
            doThrow(new IllegalStateException("Redis 없음"))
                    .when(valueOps).set(anyString(), any(), any(Duration.class));

            String got = cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> "loaded");

            assertThat(got).isEqualTo("loaded");
        }

        @Test
        @DisplayName("버전이 올라가면 다른 키를 본다 — 그것이 무효화의 전부다")
        void bumpChangesTheKey() {
            when(valueOps.get("ax:rank:2026:v1")).thenReturn("stale");

            assertThat(cache.getOrLoad(CacheName.AXIS_RANK, "2026", String.class, () -> "fresh"))
                    .isEqualTo("stale");

            cache.bump(CacheNamespace.AXIS);

            assertThat(cache.getOrLoad(CacheName.AXIS_RANK, "2026", String.class, () -> "fresh"))
                    .isEqualTo("fresh");
        }

        @Test
        @DisplayName("한 네임스페이스를 올리면 그 안의 캐시가 다 같이 식는다")
        void oneBumpCoolsTheWholeNamespace() {
            when(valueOps.get("ax:rank:2026:v1")).thenReturn("stale-rank");
            when(valueOps.get("ax:weekday:2026:v1")).thenReturn("stale-weekday");

            cache.bump(CacheNamespace.AXIS);

            /* 스위스 공휴일 하나가 바뀌면 순위 축과 요일 축이 같이 틀려진다.
               어느 키가 더러운지 계산할 수 없으므로 통째로 식히는 것이 §4.3 이다 */
            assertThat(cache.getOrLoad(CacheName.AXIS_RANK, "2026", String.class, () -> "fresh"))
                    .isEqualTo("fresh");
            assertThat(cache.getOrLoad(CacheName.AXIS_WEEKDAY, "2026", String.class, () -> "fresh"))
                    .isEqualTo("fresh");
        }

        @Test
        @DisplayName("다른 네임스페이스는 안 식는다")
        void otherNamespacesAreUntouched() {
            when(valueOps.get("h:KR:2026:v1")).thenReturn("kept");

            cache.bump(CacheNamespace.AXIS);

            assertThat(cache.getOrLoad(CacheName.HOLIDAY_YEAR, "KR:2026", String.class, () -> "fresh"))
                    .isEqualTo("kept");
        }
    }
}
