package com.booster.queryburstmsa.catalog.domain.repository;

import com.booster.queryburstmsa.catalog.domain.entity.InventoryReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, String> {
    Optional<InventoryReservationEntity> findByRequestId(String requestId);
}
