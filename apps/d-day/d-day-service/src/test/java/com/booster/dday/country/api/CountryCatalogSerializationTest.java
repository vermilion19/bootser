package com.booster.dday.country.api;

import com.booster.common.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 캐시에 담았다 꺼내면 <b>같은 타입으로 돌아오는가.</b>
 *
 * <p>Redis 를 띄우지 않는다. 문제가 생기는 자리는 연결이 아니라 <b>직렬화기</b>이고,
 * 그것은 {@code storage-redis} 의 {@code RedisTemplate} 이 쓰는 것과 똑같은 것을
 * 여기서 그대로 만들어 쓸 수 있다.
 *
 * <h2>무엇을 잡으려는 테스트인가</h2>
 *
 * <p>값 직렬화기의 기본 타이핑이 {@code DefaultTyping.NON_FINAL} 이다.
 * <b>final 타입에는 타입 정보를 안 적는다.</b> 그런데 레코드는 언제나 final 이라,
 * 레코드를 캐시의 뿌리에 놓으면 이렇게 된다.
 *
 * <pre>
 *   담을 때  → 타입 정보 없이 {…}
 *   꺼낼 때  → SerializationException
 * </pre>
 *
 * <p><b>미스가 아니라 예외다.</b> 그리고 {@code country:all} 은 TTL 이 없어서 한 번
 * 그런 값이 들어가면 만료로 풀려날 길이 없다 — 손으로 지우거나 버전을 올리기
 * 전까지 그 키를 읽는 조회가 영영 터진다.
 *
 * <p>{@code RedisVersionedCache} 가 읽기 실패를 미스로 받아 넘기므로 실제로 500 까지
 * 가지는 않는다. <b>그래도 그 방어에 기대지 않는다</b> — 기대면 캐시가 통째로 안 듣는
 * 상태가 정상처럼 보인다.
 */
class CountryCatalogSerializationTest {

    private final GenericJacksonJsonRedisSerializer serializer =
            new GenericJacksonJsonRedisSerializer(JsonUtils.MAPPER_FOR_REDIS);

    private static CountryCatalog sample() {
        return CountryCatalog.of(List.of(
                new CountryView("KR", "South Korea", "대한민국", "Asia/Seoul", false, (short) 96),
                new CountryView("US", "United States", "미국", "America/New_York", true, (short) 96),
                new CountryView("BH", "Bahrain", "바레인", "Asia/Qatar", false, (short) 48)));
    }

    @Test
    @DisplayName("담았다 꺼내면 CountryCatalog 그대로다 — 맵으로 풀리지 않는다")
    void roundTripsAsItsOwnType() {
        Object restored = serializer.deserialize(serializer.serialize(sample()));

        assertThat(restored)
                .as("레코드로 바꾸면 여기서 SerializationException 이 난다 (아래 테스트가 그것을 보인다)")
                .isInstanceOf(CountryCatalog.class);
    }

    /**
     * 위 테스트가 <b>무엇을 막고 있는지</b>를 실제로 보인다.
     *
     * <p>{@link CountryView} 는 레코드이므로 final 이다. 그것을 뿌리에 놓으면
     * 타입 정보 없이 저장되고, <b>읽을 때 터진다.</b> {@link CountryCatalog} 를
     * 레코드로 썼다면 정확히 이 일이 일어났을 것이다.
     *
     * <p>처음엔 "조용히 맵으로 풀려서 캐시가 안 듣는다" 고 짐작했는데, 재 보니
     * 그보다 나빴다. <b>짐작과 실제가 달랐던 자리라 테스트로 붙잡아 둔다.</b>
     *
     * <p>이 단언이 깨지는 날은 직렬화 설정이 바뀐 날이다. 그때
     * {@link CountryCatalog} 가 클래스인 까닭도 같이 사라지므로, 여기가 깨지면
     * 그 주석부터 다시 읽어야 한다.
     */
    @Test
    @DisplayName("레코드를 뿌리에 놓으면 **읽을 때 터진다** — 그래서 CountryCatalog 가 클래스다")
    void recordAtTheRootBlowsUpOnRead() {
        CountryView record = new CountryView("KR", "South Korea", "대한민국", "Asia/Seoul", false, (short) 96);

        byte[] stored = serializer.serialize(record);

        assertThat(new String(stored, StandardCharsets.UTF_8))
                .as("final 타입이라 타입 정보가 안 적힌다 (DefaultTyping.NON_FINAL)")
                .startsWith("{")
                .doesNotContain("CountryView");

        assertThatThrownBy(() -> serializer.deserialize(stored))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    @DisplayName("안에 든 나라들도 CountryView 로 돌아온다")
    void innerViewsSurvive() {
        CountryCatalog restored = (CountryCatalog) serializer.deserialize(serializer.serialize(sample()));

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.getCountries()).allSatisfy(view ->
                assertThat(view).isInstanceOf(CountryView.class));
    }

    @Test
    @DisplayName("값이 하나도 안 바뀐다 — 주말 마스크와 시간대 표시까지")
    void everyFieldSurvives() {
        CountryCatalog restored = (CountryCatalog) serializer.deserialize(serializer.serialize(sample()));

        CountryView korea = restored.find("KR").orElseThrow();
        assertThat(korea.nameKo()).isEqualTo("대한민국");
        assertThat(korea.weekendMask()).isEqualTo((short) 96);

        /* 이 한 칸이 SPEC §9.9(4) 다. 직렬화에서 빠지면 「우리가 골랐다」가
           조용히 사라지고, 응답은 대표 시간대를 사실인 양 말하게 된다 */
        assertThat(restored.find("US").orElseThrow().zoneAmbiguous()).isTrue();
        assertThat(restored.find("BH").orElseThrow().weekendMask()).isEqualTo((short) 48);
    }

    @Test
    @DisplayName("꺼낸 뒤에도 코드로 찾을 수 있다 — 색인은 저장되지 않고 다시 만들어진다")
    void indexIsRebuiltAfterDeserialization() {
        CountryCatalog restored = (CountryCatalog) serializer.deserialize(serializer.serialize(sample()));

        assertThat(restored.find("KR")).isPresent();
        assertThat(restored.find("ZZ")).isEmpty();
        assertThat(restored.find(null)).isEmpty();
    }
}
