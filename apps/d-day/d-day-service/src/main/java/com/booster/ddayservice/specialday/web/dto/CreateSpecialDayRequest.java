package com.booster.ddayservice.specialday.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateSpecialDayRequest(
        @NotBlank String name,
        @NotNull String category,
        @NotNull LocalDate date,
        LocalTime eventTime,
        @NotNull String countryCode,
        String timezone,
        String description,
        Boolean isPublic
) {}
