package com.booster.dday.holiday.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link NameSlug} ↔ {@code name_slug}.
 *
 * <p>정규화가 <b>한 곳에만</b> 있게 한다 (SCHEMA §1.3). 엔티티가 {@code String} 을
 * 들고 다니면 「정렬해 쉼표로 잇고 빈 것은 빈 문자열」이라는 규약을 아는 자리가
 * 둘이 되고, 둘 중 하나가 빠뜨리면 조용히 다른 키가 된다.
 *
 * <p>{@code autoApply} 를 켜지 않는다 — 어느 칼럼에 붙는지가 엔티티에 보여야 한다.
 */
@Converter
public class NameSlugConverter implements AttributeConverter<NameSlug, String> {

    @Override
    public String convertToDatabaseColumn(NameSlug attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NameSlug convertToEntityAttribute(String column) {
        return column == null ? null : new NameSlug(column);
    }
}
