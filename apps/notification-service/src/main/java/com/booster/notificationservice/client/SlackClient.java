package com.booster.notificationservice.client;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackClient {

    private final String webhookUrl;
    private final RestClient restClient;

    public SlackClient(@Value("${app.slack.webhook-url}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.builder().build();
    }

    @Async
    public void sendMessage(String message) {
        try {
            Map<String, String> payload = Map.of("text", message);

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity(); // 응답 본문 필요 없을 때

            log.info("슬랙 전송 완료 (Virtual Thread): {}", Thread.currentThread());
        } catch (Exception e) {
            log.error("전송 실패: {}", e.getMessage());
        }
    }
}
