package com.booster.dday.country.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 204개국 한 벌. {@code country:all} 캐시에 통째로 담기는 값이다 (ARCHITECTURE §4.2).
 *
 * <h2>레코드가 아니라 클래스인 까닭 — 조용히 캐시가 안 듣는다</h2>
 *
 * <p>{@code storage-redis} 의 값 직렬화기는 기본 타이핑이
 * {@code DefaultTyping.NON_FINAL} 이다. 즉 <b>final 타입에는 타입 정보를 안 적는다.</b>
 * 그런데 레코드는 언제나 final 이다.
 *
 * <pre>
 *   레코드로 담으면 → 타입 정보 없이 저장 → **읽을 때 SerializationException**
 * </pre>
 *
 * <p>미스로 끝나는 것이 아니라 <b>터진다.</b> 그리고 이 캐시는 TTL 이 없어서
 * 그런 값이 한 번 들어가면 만료로 풀려날 길이 없다 — 손으로 지우거나 버전을
 * 올리기 전까지 그 키를 읽는 조회가 영영 500 이다.
 *
 * <p>{@code RedisVersionedCache} 가 읽기 실패를 미스로 받아 넘기므로 실제로
 * 500 까지 가지는 않는다. <b>그래도 그 방어에 기대지 않는다</b> — 기대면 캐시가
 * 통째로 안 듣는 상태가 정상처럼 보인다. {@code CountryCatalogSerializationTest}
 * 가 Redis 없이 이 둘을 다 문다.
 *
 * <p>안에 든 {@link CountryView} 는 레코드여도 된다. 필드의 <b>선언된 타입</b>이
 * 있으면 Jackson 이 그것으로 읽으므로 타입 정보가 필요 없다.
 *
 * <h2>목록 옆에 색인을 같이 담는다</h2>
 *
 * <p>{@code find(code)} 가 204개를 매번 훑지 않게 한다. 캐시 한 번 읽고 맵 조회
 * 한 번이면 끝난다 — 코드별로 키를 따로 두면 204개 키가 생기고, 그것을 한 벌로
 * 묶어 둔 §4.2 의 결정이 무너진다.
 */
public class CountryCatalog {

    private List<CountryView> countries;

    /** 코드 색인. 게터가 없으므로 Jackson 이 담지도 읽지도 않는다 */
    private transient volatile Map<String, CountryView> index;

    /** Jackson 이 쓴다. 직접 부르지 말 것 — {@link #of} 가 정문이다 */
    protected CountryCatalog() {
    }

    public static CountryCatalog of(List<CountryView> countries) {
        if (countries == null) {
            throw new IllegalArgumentException("국가 목록이 없다");
        }
        CountryCatalog catalog = new CountryCatalog();
        catalog.countries = List.copyOf(countries);
        return catalog;
    }

    public List<CountryView> getCountries() {
        return countries == null ? List.of() : countries;
    }

    public void setCountries(List<CountryView> countries) {
        this.countries = countries;
    }

    public Optional<CountryView> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode().get(code));
    }

    public int size() {
        return getCountries().size();
    }

    /**
     * 색인은 <b>처음 쓸 때 만든다.</b>
     *
     * <p>생성자에서 미리 만들 수가 없다 — Jackson 이 만든 인스턴스는 그 시점에
     * 목록이 비어 있고, 그러면 <b>캐시에 히트했을 때만</b> 색인이 빈 채로 돌아간다.
     * 미스일 때는 멀쩡하고 히트일 때만 틀리는 고장이라 재현도 어렵다.
     */
    private Map<String, CountryView> byCode() {
        Map<String, CountryView> built = index;
        if (built == null) {
            built = getCountries().stream()
                    .collect(Collectors.toMap(CountryView::code, Function.identity()));
            index = built;
        }
        return built;
    }
}
