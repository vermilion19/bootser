package com.booster.ddayservice.specialday.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateSpecialDayRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Size(max = 50) String category,
        @NotNull LocalDate date,
        LocalTime eventTime,
        @NotNull @Size(max = 10) String countryCode,
        @Size(max = 50) String timezone,
        @Size(max = 1000) String description,
        Boolean isPublic
) {}
