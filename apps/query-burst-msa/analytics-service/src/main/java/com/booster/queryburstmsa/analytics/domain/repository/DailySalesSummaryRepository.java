package com.booster.queryburstmsa.analytics.domain.repository;

import com.booster.queryburstmsa.analytics.domain.entity.DailySalesSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySalesSummaryRepository extends JpaRepository<DailySalesSummaryEntity, Long> {
    Optional<DailySalesSummaryEntity> findByDateAndCategoryId(LocalDate date, Long categoryId);
    List<DailySalesSummaryEntity> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
