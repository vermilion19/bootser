package com.booster.queryburstmsa.catalog.web;

import com.booster.queryburstmsa.catalog.application.CatalogService;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationRequest;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory/reservations")
public class InternalInventoryController {

    private final CatalogService catalogService;

    public InternalInventoryController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<InventoryReservationResponse> reserve(@RequestBody InventoryReservationRequest request) {
        return ResponseEntity.ok(catalogService.reserve(request));
    }

    @PostMapping("/{reservationId}/release")
    public ResponseEntity<InventoryReservationResponse> release(@PathVariable String reservationId) {
        return ResponseEntity.ok(catalogService.release(reservationId));
    }

    @PostMapping("/{reservationId}/commit")
    public ResponseEntity<InventoryReservationResponse> commit(@PathVariable String reservationId) {
        return ResponseEntity.ok(catalogService.commit(reservationId));
    }
}
