package com.booster.logstreamservice.handler;

import com.booster.logstreamservice.event.LogEvent;
import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogEventHandler implements EventHandler<LogEvent> {

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
        // 실제 파일 쓰기/DB 저장은 나중에 구현
        // 지금은 데이터가 잘 넘어오는지 확인만 함
        if (sequence % 5000 == 0) { // 로그 너무 많이 찍히면 느려지므로 샘플링
            log.info("Processing Sequence: {}, Data: {}", sequence, event.getPayload());
        }
    }
}
