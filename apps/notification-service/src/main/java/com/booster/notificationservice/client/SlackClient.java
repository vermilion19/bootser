package com.booster.notificationservice.client;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackClient {
    @Value("${app.slack.webhook-url}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(String message) {
        try {
            // 슬랙이 요구하는 JSON 포맷: { "text": "보낼 메시지" }
            Map<String, String> payload = new HashMap<>();
            payload.put("text", message);

            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.info("슬랙 알림 전송 성공: {}", message);
        } catch (Exception e) {
            log.error("슬랙 알림 전송 실패: {}", e.getMessage());
            // 실제 운영에선 알림 실패 시 재시도 로직(Retry)이나 DLQ 처리가 필요합니다.
        }
    }
}
