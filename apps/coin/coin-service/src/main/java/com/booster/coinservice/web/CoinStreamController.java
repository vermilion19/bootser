package com.booster.coinservice.web;

import com.booster.coinservice.application.CoinSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;


@RestController
@RequestMapping("/coin/v1")
@RequiredArgsConstructor
public class CoinStreamController {

    private final CoinSseService coinSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCoins() {
        // 임의의 클라이언트 ID 생성 (실제론 JWT에서 사용자 ID 추출)
        String clientId = UUID.randomUUID().toString();
        return coinSseService.subscribe(clientId);
    }
}
