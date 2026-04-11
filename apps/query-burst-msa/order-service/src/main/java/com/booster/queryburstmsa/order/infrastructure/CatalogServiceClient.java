package com.booster.queryburstmsa.order.infrastructure;

import com.booster.queryburstmsa.contracts.inventory.InventoryReservationRequest;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationResponse;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogServiceClient {

    private final RestClient restClient;

    public CatalogServiceClient(@Value("${query-burst-msa.clients.catalog.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public InventoryReservationResponse reserve(InventoryReservationRequest request) {
        try {
            InventoryReservationResponse response = restClient.post()
                    .uri("/internal/inventory/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(InventoryReservationResponse.class);

            if (response == null) {
                return new InventoryReservationResponse(null, InventoryReservationStatus.REJECTED, "EMPTY_RESPONSE", java.util.List.of());
            }
            return response;
        } catch (RestClientException ex) {
            return new InventoryReservationResponse(null, InventoryReservationStatus.REJECTED, "CATALOG_UNAVAILABLE", java.util.List.of());
        }
    }

    public InventoryReservationResponse commit(String reservationId) {
        try {
            InventoryReservationResponse response = restClient.post()
                    .uri("/internal/inventory/reservations/{reservationId}/commit", reservationId)
                    .retrieve()
                    .body(InventoryReservationResponse.class);
            return response != null
                    ? response
                    : new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, "EMPTY_RESPONSE", java.util.List.of());
        } catch (RestClientException ex) {
            return new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, "CATALOG_UNAVAILABLE", java.util.List.of());
        }
    }

    public InventoryReservationResponse release(String reservationId) {
        try {
            InventoryReservationResponse response = restClient.post()
                    .uri("/internal/inventory/reservations/{reservationId}/release", reservationId)
                    .retrieve()
                    .body(InventoryReservationResponse.class);
            return response != null
                    ? response
                    : new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, "EMPTY_RESPONSE", java.util.List.of());
        } catch (RestClientException ex) {
            return new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, "CATALOG_UNAVAILABLE", java.util.List.of());
        }
    }
}
