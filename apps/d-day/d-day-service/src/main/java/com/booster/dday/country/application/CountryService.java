package com.booster.dday.country.application;

import com.booster.dday.country.api.CountryCatalog;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.country.domain.Country;
import com.booster.dday.country.domain.CountryRepository;
import com.booster.dday.shared.cache.CacheName;
import com.booster.dday.shared.cache.VersionedCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@link CountryReader} 의 구현. <b>읽기 전용이다</b> — 쓰는 것은 시드뿐이다.
 *
 * <h2>키를 204개로 쪼개지 않는다</h2>
 *
 * <p>{@code country:all} 한 키에 204개국을 통째로 담는다 (ARCHITECTURE §4.2).
 * 코드마다 키를 두면 무효화 때 204번 손대야 하고, 그것은 §4.3 이 금지한
 * 개별 삭제로 가는 길이다. 낱개 조회는 캐시 한 번 읽고 맵에서 꺼낸다.
 *
 * <h2>TTL 이 없는 유일한 캐시다</h2>
 *
 * <p>시드는 동기화로 변하지 않는다. 바뀌는 계기는 <b>시드를 갈아 끼우는 것</b>뿐이고,
 * 그때 {@code CountrySeedLoader} 가 버전을 올린다. 만료로 자연히 새로고쳐지지
 * 않으므로 <b>그 bump 를 빠뜨리면 낡은 값이 영원히 나간다</b> — 로더가 그것을
 * 손에 쥐고 있는 까닭이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountryService implements CountryReader {

    private final CountryRepository repository;
    private final VersionedCache cache;

    @Override
    public List<CountryView> findAll() {
        return catalog().getCountries();
    }

    @Override
    public Optional<CountryView> find(String code) {
        return catalog().find(code);
    }

    /**
     * 표가 비어 있으면 <b>빈 것을 캐시에 담지 않는다.</b>
     *
     * <p>{@code country:all} 은 TTL 이 없다. 시드가 들어오기 전에 한 번이라도
     * 빈 목록이 담기면 <b>그 빈 목록이 영원히 산다</b> — 만료로 풀려날 길이 없다.
     * 로더가 {@code bump} 할 때까지 모든 나라가 없는 것이 되고, 에러는 안 난다.
     */
    private CountryCatalog catalog() {
        CountryCatalog cached =
                cache.getOrLoad(CacheName.COUNTRY_ALL, "", CountryCatalog.class, this::loadFromDb);

        return cached != null ? cached : CountryCatalog.of(List.of());
    }

    /**
     * 코드순으로 담는다.
     *
     * <p>차례를 DB 가 정하게 두면 캐시에 담긴 목록의 순서가 <b>회차마다 달라질 수
     * 있고</b>, 그러면 응답도 따라 흔들린다. 목록 API 에 정렬 규약이 없는 자리라
     * (SPEC §10.8 — 204 이하는 안 나눈다) 여기서 못 박는다.
     */
    private CountryCatalog loadFromDb() {
        List<CountryView> countries = repository.findAll().stream()
                .sorted(Comparator.comparing(Country::getCode))
                .map(CountryService::toView)
                .toList();

        return countries.isEmpty() ? null : CountryCatalog.of(countries);
    }

    private static CountryView toView(Country country) {
        return new CountryView(
                country.getCode(),
                country.getNameEn(),
                country.getNameKo(),
                country.getPrimaryZoneId(),
                country.isZoneIsAmbiguous(),
                country.getWeekend().mask());
    }
}
