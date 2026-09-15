package com.booster.dday.country.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link Weekend} ↔ {@code smallint}.
 *
 * <p>변환기를 쓰는 까닭은 <b>비트 접기가 한 곳에만 있게</b> 하기 위해서다.
 * 엔티티가 {@code short} 를 들고 다니면 «bit0=월» 규약을 아는 자리가 둘이 되고,
 * 둘 중 하나가 1을 빼는 것을 잊으면 조용히 하루씩 밀린다 (SCHEMA §4.3).
 *
 * <p>{@code autoApply} 를 켜지 않는다 — 이 변환이 필요한 칼럼은 지금 하나뿐이고,
 * 자동 적용은 나중에 {@link Weekend} 를 다른 뜻으로 쓰는 자리가 생겼을 때
 * 아무도 모르게 따라붙는다.
 */
@Converter
public class WeekendConverter implements AttributeConverter<Weekend, Short> {

    @Override
    public Short convertToDatabaseColumn(Weekend weekend) {
        return weekend == null ? null : weekend.mask();
    }

    @Override
    public Weekend convertToEntityAttribute(Short mask) {
        return mask == null ? null : new Weekend(mask);
    }
}
