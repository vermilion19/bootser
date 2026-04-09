package com.booster.queryburst.order.web;

import com.booster.queryburst.order.application.OutboxAdminService;
import com.booster.queryburst.order.web.dto.response.OutboxEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    @GetMapping("/failed")
    public ResponseEntity<List<OutboxEventResponse>> getFailedEvents(
            @RequestParam(defaultValue = "20") int size
    ) {
        List<OutboxEventResponse> response = outboxAdminService.getFailedEvents(size).stream()
                .map(OutboxEventResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{outboxEventId}/retry")
    public ResponseEntity<Void> retryFailedEvent(@PathVariable Long outboxEventId) {
        outboxAdminService.retryFailedEvent(outboxEventId);
        return ResponseEntity.noContent().build();
    }
}
