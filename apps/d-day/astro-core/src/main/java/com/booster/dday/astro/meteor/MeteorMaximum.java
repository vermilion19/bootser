package com.booster.dday.astro.meteor;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.frame.J2000EclipticLongitude;

import java.time.Instant;
import java.util.Optional;

/**
 * 유성우의 극대. <b>공표된 것</b>과 <b>우리가 셈한 것</b>이 다른 타입이다.
 *
 * <p>둘을 한 타입으로 두면 응답을 만드는 쪽이 구별하는 것을 잊는다. 그리고
 * "공표값인지 추정값인지 안 알리고 내보내는 것" 은 이 서비스가 스스로 금지한 고장이다
 * (SPEC §9.9(4)). {@code sealed} 라 {@code switch} 가 둘을 다 다루지 않으면
 * 컴파일되지 않는다.
 *
 * <p>왜 공표값을 1급으로 두는가 — 유성우 극대는 궤도 요소가 아니라 <b>관측 통계</b>에서
 * 나온다. 계산이 원천보다 나을 수 없는 자리다. 여기서 계산을 앞세우면 우리는 IMO 보다
 * 덜 맞는 값을 더 정밀한 척 내게 된다. 절기는 정반대다 — 정의가 황경이므로 계산이 곧
 * 정답이다. <b>두 갈래는 인식론이 다르고, 그래서 구조도 달라야 한다</b>
 * (docs/ARCHITECTURE.md §6.3).
 */
public sealed interface MeteorMaximum permits MeteorMaximum.Published, MeteorMaximum.Estimated {

    /** IMO 코드. 예: 004GEM */
    String code();

    /** 한국어 이름 */
    String ko();

    /** 영어 이름 */
    String name();

    /** 극대 시각(UTC) */
    Instant utc();

    Precision precision();

    /** 원천이 공표한 값인가 */
    boolean isPublished();

    /**
     * IMO 가 낸 값 그대로.
     *
     * @param utcEnd          {@link Precision#RANGE} 일 때 창의 끝
     * @param solarLongitude  <b>equinox 2000.0</b> 기준 황경. IMO 가 안 준 갈래도 있다
     * @param source          달력 본문의 원문. 옮기다 틀린 것을 나중에 되짚을 수 있게 남긴다
     */
    record Published(
            String code,
            String ko,
            String name,
            Instant utc,
            Instant utcEnd,
            Precision precision,
            J2000EclipticLongitude solarLongitude,
            String source
    ) implements MeteorMaximum {

        @Override
        public boolean isPublished() {
            return true;
        }

        public Optional<Instant> end() {
            return Optional.ofNullable(utcEnd);
        }

        public Optional<J2000EclipticLongitude> longitude() {
            return Optional.ofNullable(solarLongitude);
        }
    }

    /**
     * 공표값이 없는 해를 우리가 셈한 것.
     *
     * <p>정밀도는 언제나 {@link Precision#DATE} 다. 황경 역변환 자체는 분까지 나오지만
     * <b>IMO 가 준 황경이 이미 「1도보다 정밀하지 않다」</b>. 계산이 정밀하다고 해서 답이
     * 정밀해지지 않는다 — 그 구별을 타입이 들고 있게 한다.
     *
     * @param from  원천이 준 equinox 2000.0 황경
     * @param at    그 해의 겉보기 황경으로 옮긴 값. 이것으로 시각을 풀었다
     */
    record Estimated(
            String code,
            String ko,
            String name,
            Instant utc,
            J2000EclipticLongitude from,
            ApparentEclipticLongitude at
    ) implements MeteorMaximum {

        @Override
        public Precision precision() {
            return Precision.DATE;
        }

        @Override
        public boolean isPublished() {
            return false;
        }
    }
}
