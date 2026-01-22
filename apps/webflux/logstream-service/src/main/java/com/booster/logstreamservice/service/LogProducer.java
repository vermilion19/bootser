package com.booster.logstreamservice.service;

import com.booster.logstreamservice.event.LogEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogProducer {

    private final Disruptor<LogEvent> disruptor;

    public void publish(String payload) {
        RingBuffer<LogEvent> ringBuffer = disruptor.getRingBuffer();

        // 1. 데이터를 넣을 공간(Sequence) 확보
        long sequence = ringBuffer.next();
        try {
            // 2. 해당 공간의 객체 가져오기
            LogEvent event = ringBuffer.get(sequence);

            // 3. 데이터 채우기 (객체 생성 X, 값만 복사)
            event.set(UUID.randomUUID().toString(), payload, Instant.now().toEpochMilli());
        } finally {
            // 4. 발행 (이제 소비자가 이 데이터를 볼 수 있음)
            ringBuffer.publish(sequence);
        }
    }
}
