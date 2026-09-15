package com.booster.dday.country.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * 나라 하나. <b>동기화 대상이 아니라 시드다</b> (SPEC §9.2).
 *
 * <p>CLDR · IANA tzdb · Nager 에서 한 번 뽑아 넣고 판을 박는다. 국가 목록과
 * 시간대는 자주 바뀌지 않고, <b>바뀌면 그것 자체가 사람이 확인할 사건</b>이다 —
 * 그래서 {@code tools/gen-country-seed.mjs} 가 원천 넷을 견주다 어긋나면 멈춘다.
 *
 * <h2>PK 가 Snowflake 가 아니다</h2>
 *
 * <p>SCHEMA §1.1 이 둔 예외 셋 중 하나다. 국가 코드는 <b>이미 세상이 정한 자연키</b>이고,
 * 그 위에 우리 번호를 또 얹으면 모든 조인이 한 단계 깊어진다. {@code holiday} ·
 * {@code sync_run_item} 이 전부 {@code country_code} 로 참조한다.
 *
 * <h2>{@code CHECK} 둘 중 하나만 여기 옮겨 온다</h2>
 *
 * <p>SCHEMA §1.5 R1 은 정합성 제약을 엔티티에도 적으라고 한다. 그런데
 * {@code ck_country_code} 는 {@code code ~ '^[A-Z]{2}$'} — <b>PostgreSQL 정규식
 * 연산자</b>라 H2 {@code create-drop} 에서 그대로 쓸 수 없다. 그래서 그 검사는
 * 아래 팩토리가 들고, {@code ck_country_weekend} 는 {@link Weekend} 가 들었다.
 * <b>DB 가 못 지키는 자리를 안 적으면 지켜지는 줄 안다.</b>
 */
@Entity
@Table(name = "country")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Country extends BaseEntity {

    private static final Pattern CODE = Pattern.compile("^[A-Z]{2}$");

    /** ISO 3166-1 alpha-2. {@code char(2)} 가 아니라 {@code varchar(2)} 다 (SCHEMA §1.1) */
    @Id
    @Column(length = 2)
    private String code;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_ko", nullable = false, length = 100)
    private String nameKo;

    /**
     * 대표 시간대. <b>E-1 의 근거다.</b>
     *
     * <p>정적 사이트는 보는 사람의 기기 날짜로 D-day 를 세어 다른 시간대 국가에서
     * 하루씩 어긋났다. 이 칼럼이 그것을 고친다.
     */
    @Column(name = "primary_zone_id", nullable = false, length = 64)
    private String primaryZoneId;

    /**
     * 시간대가 여럿이라 <b>우리가 하나를 골랐다</b>는 표시 (SPEC §9.9(4)).
     *
     * <p>고른 사실을 응답에 싣는다. 조용히 대표로 퉁치면 E-1 이 고치려던 고장을
     * 자리만 옮겨 다시 만드는 셈이다.
     */
    @Column(name = "zone_is_ambiguous", nullable = false)
    private boolean zoneIsAmbiguous;

    @Convert(converter = WeekendConverter.class)
    @Column(name = "weekend_mask", nullable = false)
    private Weekend weekend;

    public static Country of(String code, String nameEn, String nameKo,
                             String primaryZoneId, boolean zoneIsAmbiguous, Weekend weekend) {
        Country country = new Country();
        country.code = requireCode(code);
        country.nameEn = requireText(nameEn, "영어 이름");
        country.nameKo = requireText(nameKo, "한국어 이름");
        country.primaryZoneId = requireZone(primaryZoneId);
        country.zoneIsAmbiguous = zoneIsAmbiguous;
        country.weekend = requireWeekend(weekend);
        return country;
    }

    /**
     * 시드를 다시 돌렸을 때 달라진 것만 반영한다.
     *
     * @return 실제로 바뀐 것이 있으면 {@code true} — 그때만 캐시 버전을 올린다
     */
    public boolean refresh(String nameEn, String nameKo,
                           String primaryZoneId, boolean zoneIsAmbiguous, Weekend weekend) {
        String zone = requireZone(primaryZoneId);
        String en = requireText(nameEn, "영어 이름");
        String ko = requireText(nameKo, "한국어 이름");
        Weekend newWeekend = requireWeekend(weekend);

        if (en.equals(this.nameEn) && ko.equals(this.nameKo)
                && zone.equals(this.primaryZoneId)
                && zoneIsAmbiguous == this.zoneIsAmbiguous
                && newWeekend.equals(this.weekend)) {
            return false;
        }
        this.nameEn = en;
        this.nameKo = ko;
        this.primaryZoneId = zone;
        this.zoneIsAmbiguous = zoneIsAmbiguous;
        this.weekend = newWeekend;
        return true;
    }

    /** 그 나라의 「오늘」을 셀 때 쓰는 시간대 (E-1) */
    public ZoneId zone() {
        return ZoneId.of(primaryZoneId);
    }

    private static String requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("국가 코드는 대문자 두 글자다: " + code);
        }
        return code;
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }

    /**
     * 시간대 이름이 <b>이 JVM 의 tzdb 에 실재하는지</b> 본다.
     *
     * <p>문자열로만 두면 오타나 폐기된 이름이 그대로 들어오고, 터지는 것은
     * 저장할 때가 아니라 <b>몇 달 뒤 그 나라의 D-day 를 셀 때</b>다.
     */
    private static String requireZone(String zoneId) {
        try {
            return ZoneId.of(requireText(zoneId, "대표 시간대")).getId();
        } catch (Exception e) {
            throw new IllegalArgumentException("이 JVM 의 tzdb 가 모르는 시간대다: " + zoneId, e);
        }
    }

    private static Weekend requireWeekend(Weekend weekend) {
        if (weekend == null) {
            throw new IllegalArgumentException("주말 없이는 요일 축(A-7)을 셀 수 없다");
        }
        return weekend;
    }
}
