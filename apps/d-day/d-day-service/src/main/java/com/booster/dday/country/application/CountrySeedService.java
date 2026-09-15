package com.booster.dday.country.application;

import com.booster.dday.country.application.dto.CountrySeedRow;
import com.booster.dday.country.domain.Country;
import com.booster.dday.country.domain.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 시드를 표에 반영한다. <b>여러 번 돌려도 같다.</b>
 *
 * <p>인스턴스가 둘이면 부팅할 때 둘 다 이것을 돈다. 그래서 「없으면 넣고 다르면
 * 고친다」 로 짠다 — 같은 값을 두 번 써도 두 번째는 아무 일도 안 한다.
 * 204행이라 통째로 읽어 맵으로 견주는 것이 upsert 질의를 쓰는 것보다 싸고,
 * <b>무엇이 실제로 바뀌었는지</b>를 셀 수 있다.
 *
 * <h2>지우지 않는다</h2>
 *
 * <p>시드에서 사라진 나라를 표에서 지우지 않는다. {@code holiday} 가 FK 로
 * 물고 있어서 지우려면 그 나라 공휴일을 먼저 지워야 하는데, <b>그것은 시드
 * 반영이 할 일이 아니다.</b> 원천에서 나라가 빠지는 것은 사람이 볼 사건이고
 * (SPEC §9.2), 생성 스크립트가 204 가 아니면 이미 멈춘다.
 *
 * <h2>{@code @Transactional} 이 여기 있고 로더에 없는 까닭</h2>
 *
 * <p>캐시 버전을 올리는 것은 <b>커밋된 뒤</b>여야 한다. 한 메서드 안에서 쓰고
 * 올리면 롤백됐을 때 버전만 올라가 있고, 그러면 캐시는 비었는데 DB 는 옛것인
 * 상태가 된다. 그래서 쓰기는 이 빈이 트랜잭션으로 감싸고, 올리는 것은
 * {@code CountrySeedLoader} 가 <b>이 메서드가 끝난 다음</b> 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountrySeedService {

    private final CountryRepository repository;

    /**
     * @return 새로 넣거나 고친 나라 수. <b>0 이면 캐시를 건드릴 이유가 없다</b>
     */
    @Transactional
    public int apply(List<CountrySeedRow> seed) {
        Map<String, Country> existing = repository.findAll().stream()
                .collect(Collectors.toMap(Country::getCode, Function.identity()));

        int inserted = 0;
        int updated = 0;

        for (CountrySeedRow row : seed) {
            Country country = existing.get(row.code());

            if (country == null) {
                repository.save(Country.of(row.code(), row.nameEn(), row.nameKo(),
                        row.zoneId(), row.zoneAmbiguous(), row.weekend()));
                inserted++;
                continue;
            }
            if (country.refresh(row.nameEn(), row.nameKo(),
                    row.zoneId(), row.zoneAmbiguous(), row.weekend())) {
                updated++;
            }
        }

        if (inserted + updated > 0) {
            log.info("[CountrySeed] 새로 {}개 · 고친 것 {}개 (시드 {}줄)", inserted, updated, seed.size());
        }
        return inserted + updated;
    }
}
