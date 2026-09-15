package com.booster.dday.holiday.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 영어 이름 → 한국어 라벨 (SPEC §9.9(1)).
 *
 * <p><b>원천에 한국어가 없다는 것이 확인됐다</b> (SPEC §11.1). {@code localName} 은
 * 현지어라서 일본 공휴일에는 {@code 元日} 이 온다 — 한국어가 아니다. 그래서 이 표가
 * 있고, 사람이 채운다.
 *
 * <p>키가 {@link NameSlug} 다. {@code name_en} 을 키로 잡으면 축의 정체성과 라벨의
 * 정체성이 갈라지고, <b>셋이 갈라지는 것은 시간 문제</b>다 (SCHEMA §4.2).
 *
 * <p>캐시에서도 이 표는 따로 산다 — {@code lbl:{lang}} 통째로 담고 조립에서 합친다
 * (ARCHITECTURE §4.2). 언어를 공휴일 키에 섞으면 라벨 하나가 추가될 때
 * <b>204×5 개 키가 식는다.</b>
 *
 * <h2>여기서만 slug 가 {@link NameSlug} 가 아니라 {@code String} 이다</h2>
 *
 * <p>Hibernate 는 <b>{@code @Id} 에 {@code AttributeConverter} 를 못 붙인다.</b>
 * {@code holiday.name_slug} 는 VO 로 두었지만 여기서는 그것이 키라서 안 된다.
 * 대신 {@link #slug()} 로 꺼내 쓰고, <b>들어올 때는 {@link #of} 가 VO 를 받는다</b> —
 * 정규화를 안 거친 문자열이 키로 들어오는 길을 막는 것이 요점이다.
 *
 * <p>⚠ <b>「문턱을 넘는 이름만 채운다」의 문턱이 몇인지는 아직 안 정했다</b>
 * (ARCHITECTURE §10-9). 코퍼스를 보고 정한다 — 60개 이름이 176개국을 덮는다는
 * SPEC §5 의 실측이 그 근거가 될 것이다.
 */
@Entity
@Table(name = "holiday_name_label")
@IdClass(HolidayNameLabel.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayNameLabel extends BaseEntity {

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String nameSlug;
        private String lang;
    }

    @Id
    @Column(name = "name_slug", nullable = false, length = NameSlug.MAX_LENGTH)
    private String nameSlug;

    @Id
    @Column(nullable = false, length = 5)
    private String lang;

    @Column(nullable = false, length = 200)
    private String label;

    /** 사람이 표를 채울 때 보는 참고값. <b>응답에 나가지 않는다</b> */
    @Column(name = "sample_name_en", nullable = false, length = 200)
    private String sampleNameEn;

    public static HolidayNameLabel of(NameSlug nameSlug, String lang, String label, String sampleNameEn) {
        HolidayNameLabel entity = new HolidayNameLabel();
        entity.nameSlug = require(nameSlug, "slug").value();
        entity.lang = require(lang, "언어");
        entity.label = require(label, "라벨");
        entity.sampleNameEn = require(sampleNameEn, "참고 영어 이름");
        return entity;
    }

    /** 키를 VO 로 되돌려 준다 — 읽는 쪽이 문자열 규약을 다시 외우지 않게 */
    public NameSlug slug() {
        return new NameSlug(nameSlug);
    }

    public void rename(String label) {
        this.label = require(label, "라벨");
    }

    private static <T> T require(T value, String what) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }
}
