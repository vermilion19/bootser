package com.booster.dday.country.infrastructure;

import com.booster.dday.country.application.CountrySeedService;
import com.booster.dday.country.application.dto.CountrySeedRow;
import com.booster.dday.shared.cache.CacheNamespace;
import com.booster.dday.shared.cache.VersionedCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 부팅할 때 {@code seed/country.tsv} 를 표에 넣는다 (ARCHITECTURE §1.4 · 착수 3).
 *
 * <p>{@code country} 는 동기화 대상이 아니다 (SPEC §9.2). 스케줄도 수동 트리거도
 * 없고, <b>배포가 곧 갱신</b>이다 — 시드 파일이 바뀐 채로 뜨면 그때 반영된다.
 *
 * <h2>자기호출 함정이 없는 모양으로 나눠 둔다</h2>
 *
 * <p>{@code CLAUDE.md} 의 AOP 체크리스트가 경고하는 자리다. 여기에
 * {@code @Transactional} 을 붙이고 같은 클래스 안에서 쓰기와 bump 를 같이 하면,
 * 프록시를 안 거치는 것과 별개로 <b>커밋 전에 캐시를 올리는</b> 순서 문제가 생긴다.
 * 쓰기는 {@link CountrySeedService}(다른 빈)가 트랜잭션으로 하고, 캐시는
 * <b>그것이 끝난 뒤</b> 여기서 올린다.
 *
 * <h2>캐시를 못 올려도 부팅은 막지 않는다</h2>
 *
 * <p>{@code country:all} 은 TTL 이 없어서 <b>bump 가 유일한 무효화</b>다
 * (ARCHITECTURE §4.2). 그러니 실패를 삼키면 낡은 목록이 계속 나간다 — 그래서
 * ERROR 로 남긴다. 그렇다고 부팅을 막지는 않는다. Redis 가 잠깐 없다고 공휴일
 * 조회까지 통째로 멈추는 것은 나라 이름 하나가 옛것인 것보다 나쁘다.
 * <b>둘 다 나쁜 선택이라 덜 나쁜 쪽을 고르고, 고른 사실을 여기 적어 둔다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dday.country.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CountrySeedLoader implements ApplicationRunner {

    private final CountrySeedService seedService;
    private final VersionedCache cache;

    @Override
    public void run(ApplicationArguments args) {
        List<CountrySeedRow> seed = CountrySeedFile.read();

        int changed = seedService.apply(seed);
        if (changed == 0) {
            log.info("[CountrySeed] {}개국 — 바뀐 것 없음", seed.size());
            return;
        }

        try {
            long version = cache.bump(CacheNamespace.COUNTRY);
            log.info("[CountrySeed] {}개국 바뀌어 country 캐시를 v{} 로 올렸다", changed, version);
        } catch (Exception e) {
            log.error("[CountrySeed] 시드는 {}개국 바뀌었는데 캐시 버전을 못 올렸다. "
                    + "country:all 은 TTL 이 없으므로 **낡은 목록이 계속 나간다** — "
                    + "Redis 가 돌아오면 손으로 올릴 것", changed, e);
        }
    }
}
