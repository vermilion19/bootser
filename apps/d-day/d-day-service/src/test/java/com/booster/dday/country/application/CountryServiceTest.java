package com.booster.dday.country.application;

import com.booster.dday.country.api.CountryCatalog;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.country.domain.Country;
import com.booster.dday.country.domain.CountryRepository;
import com.booster.dday.country.domain.Weekend;
import com.booster.dday.shared.cache.CacheName;
import com.booster.dday.shared.cache.VersionedCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 읽기 쪽. DB 도 Redis 도 띄우지 않는다 — 여기서 무는 것은 <b>어느 캐시를 어떻게
 * 쓰는가</b>이고, 그것은 둘 다 없어도 답이 정해진다.
 */
class CountryServiceTest {

    private CountryRepository repository;
    private VersionedCache cache;
    private CountryService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repository = mock(CountryRepository.class);
        cache = mock(VersionedCache.class);
        service = new CountryService(repository, cache);

        /* 캐시는 「없으면 로더를 부른다」 만 흉내 낸다. 진짜 캐시의 행동은
           RedisVersionedCacheTest 가 따로 문다 */
        when(cache.getOrLoad(any(), anyString(), any(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(3)).get());
    }

    private static Country country(String code, String nameKo, String zone, int mask) {
        return Country.of(code, "Name " + code, nameKo, zone, false, Weekend.of(mask));
    }

    @Test
    @DisplayName("country:all 한 키를 쓴다 — 코드마다 키를 두지 않는다")
    void usesTheSingleCatalogKey() {
        when(repository.findAll()).thenReturn(List.of(country("KR", "대한민국", "Asia/Seoul", 96)));

        service.find("KR");

        verify(cache).getOrLoad(eq(CacheName.COUNTRY_ALL), eq(""), any(), any());
    }

    @Test
    @DisplayName("코드순으로 담는다 — 차례를 DB 가 정하게 두지 않는다")
    void sortsByCode() {
        when(repository.findAll()).thenReturn(List.of(
                country("US", "미국", "America/New_York", 96),
                country("BH", "바레인", "Asia/Qatar", 48),
                country("KR", "대한민국", "Asia/Seoul", 96)));

        assertThat(service.findAll()).extracting(CountryView::code)
                .containsExactly("BH", "KR", "US");
    }

    @Test
    @DisplayName("값이 그대로 나간다 — 주말 마스크와 「우리가 골랐다」 표시까지")
    void mapsEveryField() {
        when(repository.findAll()).thenReturn(List.of(
                Country.of("US", "United States", "미국", "America/New_York", true, Weekend.of(96))));

        CountryView view = service.find("US").orElseThrow();

        assertThat(view.nameKo()).isEqualTo("미국");
        assertThat(view.zoneId()).isEqualTo("America/New_York");
        assertThat(view.zoneAmbiguous()).isTrue();
        assertThat(view.weekendMask()).isEqualTo((short) 96);
    }

    @Test
    @DisplayName("없는 코드는 빈 값이다 — 404 로 자르는 것은 부르는 쪽 몫이다")
    void unknownCodeIsEmpty() {
        when(repository.findAll()).thenReturn(List.of(country("KR", "대한민국", "Asia/Seoul", 96)));

        assertThat(service.find("ZZ")).isEmpty();
        assertThat(service.find(null)).isEmpty();
    }

    /**
     * <b>이 테스트가 제일 중요하다.</b>
     *
     * <p>{@code country:all} 은 TTL 이 없다. 시드가 들어오기 전에 빈 목록이 한 번
     * 담기면 <b>만료로 풀려날 길이 없어 영원히 산다</b> — 모든 나라가 없는 것이
     * 되고, 에러는 안 난다. 로더가 {@code null} 을 주면 캐시는 담지 않는다.
     */
    @Test
    @DisplayName("표가 비어 있으면 빈 목록을 캐시에 담지 않는다")
    void neverCachesAnEmptyCatalog() {
        when(repository.findAll()).thenReturn(List.of());

        ArgumentCaptor<Supplier<CountryCatalog>> loader = ArgumentCaptor.forClass(Supplier.class);

        assertThat(service.findAll()).isEmpty();

        verify(cache).getOrLoad(eq(CacheName.COUNTRY_ALL), eq(""), any(), loader.capture());
        assertThat(loader.getValue().get())
                .as("빈 목록 대신 null 을 줘야 캐시가 담지 않는다")
                .isNull();
    }

    @Test
    @DisplayName("한 번 읽은 목록으로 낱개를 찾는다 — 두 번 묻지 않는다")
    void singleLookupPerCall() {
        when(repository.findAll()).thenReturn(List.of(
                country("KR", "대한민국", "Asia/Seoul", 96),
                country("BH", "바레인", "Asia/Qatar", 48)));

        service.find("KR");

        verify(cache, times(1)).getOrLoad(any(), anyString(), any(), any());
        verify(cache, never()).bump(any());
    }
}
