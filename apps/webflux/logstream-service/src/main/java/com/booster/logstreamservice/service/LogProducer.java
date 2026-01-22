package com.booster.logstreamservice.service;

import com.booster.logstreamservice.event.LogEvent;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogProducer {

    private final Disruptor<LogEvent> disruptor;

    // [P3] UUID.randomUUID() 대신 AtomicLong 사용 (성능 향상)
    private final AtomicLong idGenerator = new AtomicLong(System.currentTimeMillis());

    /**
     * 로그를 Disruptor RingBuffer에 발행
     *
     * @param payload 로그 데이터
     * @return 발행 성공 여부 (버퍼 가득 차면 false)
     */
    public boolean publish(String payload) {
        RingBuffer<LogEvent> ringBuffer = disruptor.getRingBuffer();

        // [P0] tryNext: 버퍼가 가득 차면 InsufficientCapacityException 발생
        long sequence;
        try {
            sequence = ringBuffer.tryNext();
        } catch (InsufficientCapacityException e) {
            log.warn("Disruptor RingBuffer full, dropping log. Consider increasing buffer size.");
            return false;
        }

        try {
            LogEvent event = ringBuffer.get(sequence);
            // [P3] AtomicLong으로 ID 생성 (UUID보다 빠름)
            event.set(String.valueOf(idGenerator.incrementAndGet()), payload, System.currentTimeMillis());
        } finally {
            ringBuffer.publish(sequence);
        }
        return true;
    }
}
