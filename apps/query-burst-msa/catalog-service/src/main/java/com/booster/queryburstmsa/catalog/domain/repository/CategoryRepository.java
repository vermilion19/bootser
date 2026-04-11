package com.booster.queryburstmsa.catalog.domain.repository;

import com.booster.queryburstmsa.catalog.domain.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
}
