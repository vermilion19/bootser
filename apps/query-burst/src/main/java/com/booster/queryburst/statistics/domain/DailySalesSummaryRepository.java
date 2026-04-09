package com.booster.queryburst.statistics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySalesSummaryRepository extends JpaRepository<DailySalesSummary, Long> {

    Optional<DailySalesSummary> findByDateAndCategoryId(LocalDate date, Long categoryId);

    List<DailySalesSummary> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
