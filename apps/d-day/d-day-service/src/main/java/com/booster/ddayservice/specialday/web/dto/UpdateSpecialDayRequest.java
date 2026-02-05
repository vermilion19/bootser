package com.booster.ddayservice.specialday.web.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateSpecialDayRequest(
        @Size(max = 255) String name,
        @Size(max = 50) String category,
        LocalDate date,
        LocalTime eventTime,
        @Size(max = 10) String countryCode,
        @Size(max = 50) String timezone,
        @Size(max = 1000) String description,
        Boolean isPublic
) {}
