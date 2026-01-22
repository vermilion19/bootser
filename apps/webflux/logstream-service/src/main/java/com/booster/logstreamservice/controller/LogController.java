package com.booster.logstreamservice.controller;

import com.booster.logstreamservice.service.LogProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogProducer logProducer;

    @PostMapping("/logs")
    public Mono<ResponseEntity<String>> receiveLog(@RequestBody String payload) {
        // [P0] 발행 결과에 따라 적절한 응답 반환
        boolean success = logProducer.publish(payload);

        if (success) {
            return Mono.just(ResponseEntity.ok("ok"));
        } else {
            // 버퍼가 가득 찼을 때 503 Service Unavailable
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Buffer full, try again later"));
        }
    }
}
