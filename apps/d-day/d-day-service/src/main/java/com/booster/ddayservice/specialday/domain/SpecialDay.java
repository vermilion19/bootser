package com.booster.ddayservice.specialday.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecialDay extends BaseEntity {

    @Id
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private SpecialDayCategory category;

    private LocalDate date;
    private LocalTime eventTime;

    @Enumerated(EnumType.STRING)
    private CountryCode countryCode;

    @Enumerated(EnumType.STRING)
    private Timezone eventTimeZone;


    private String description;

    private Long memberId;

    private boolean isPublic;

    @Builder
    public SpecialDay(String name, SpecialDayCategory category, LocalDate date,
                      LocalTime eventTime, Timezone eventTimeZone,
                      CountryCode countryCode, String description,
                      Long memberId, Boolean isPublic) {
        this.id = SnowflakeGenerator.nextId();
        this.name = name;
        this.category = category;
        this.date = date;
        this.eventTime = eventTime;
        this.countryCode = countryCode;
        this.eventTimeZone = eventTimeZone;
        this.description = description;
        this.memberId = memberId;
        this.isPublic = isPublic != null ? isPublic : true;
    }

    public static SpecialDay of(String name, SpecialDayCategory category, LocalDate date,
                                LocalTime eventTime, Timezone eventTimeZone,
                                CountryCode countryCode, String description) {

        return SpecialDay.builder()
                .name(name)
                .category(category)
                .date(date)
                .eventTime(eventTime)
                .countryCode(countryCode)
                .eventTimeZone(eventTimeZone)
                .description(description)
                .memberId(0L)
                .build();
    }

    public static SpecialDay createByMember(String name, SpecialDayCategory category, LocalDate date,
                                            LocalTime eventTime, Timezone eventTimeZone,
                                            CountryCode countryCode, String description,
                                            Long memberId, boolean isPublic) {
        return SpecialDay.builder()
                .name(name)
                .category(category)
                .date(date)
                .eventTime(eventTime)
                .countryCode(countryCode)
                .eventTimeZone(eventTimeZone)
                .description(description)
                .memberId(memberId)
                .isPublic(isPublic)
                .build();
    }

    public void toggleVisibility() {
        this.isPublic = !this.isPublic;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId != null && this.memberId.equals(memberId);
    }

    public void update(String name, SpecialDayCategory category, LocalDate date,
                       LocalTime eventTime, Timezone eventTimeZone,
                       CountryCode countryCode, String description, Boolean isPublic) {
        if (name != null) this.name = name;
        if (category != null) this.category = category;
        if (date != null) this.date = date;
        if (eventTime != null) this.eventTime = eventTime;
        if (eventTimeZone != null) this.eventTimeZone = eventTimeZone;
        if (countryCode != null) this.countryCode = countryCode;
        if (description != null) this.description = description;
        if (isPublic != null) this.isPublic = isPublic;
    }
}
