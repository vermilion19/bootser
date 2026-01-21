package com.booster.massivesseservice.controller;

import com.booster.massivesseservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sse")
public class PushController {

    private final NotificationService service;

    // 1. 연결 요청 (구독)
    // curl -N http://localhost:8080/sse/connect/user1
    @GetMapping(value = "/connect/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> connect(@PathVariable String userId) {
        return service.subscribe(userId);
    }

    @PostMapping("/broadcast")
    public String broadcast(@RequestParam String message) {
        service.broadcast(message);
        return "Broadcasted!";
    }

}
