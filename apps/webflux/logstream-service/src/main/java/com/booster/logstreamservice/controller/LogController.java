package com.booster.logstreamservice.controller;

import com.booster.logstreamservice.service.LogProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogProducer logProducer;

    @PostMapping("/logs")
    public Mono<String> receiveLog(@RequestBody String payload) {
        // Disruptor에 던지기만 하고 바로 OK 응답 (비동기 처리)
        logProducer.publish(payload);
        return Mono.just("ok");
    }
}
