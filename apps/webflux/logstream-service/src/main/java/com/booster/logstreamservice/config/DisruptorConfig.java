package com.booster.logstreamservice.config;

import com.booster.logstreamservice.event.LogEvent;
import com.booster.logstreamservice.handler.LogEventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class DisruptorConfig {

    // RingBuffer 크기 (반드시 2의 제곱수여야 함)
    // 1024 * 1024 = 1,048,576건 저장 가능
    private static final int BUFFER_SIZE = 1024 * 1024;

    @Bean
    public Disruptor<LogEvent> disruptor(LogEventHandler logEventHandler) {
        // 데몬 쓰레드 팩토리 (JVM 종료 시 같이 종료됨)
        ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;

        // Disruptor 생성
        // ProducerType.MULTI: 여러 HTTP 쓰레드가 동시에 데이터를 넣을 것이므로 MULTI로 설정
        // WaitStrategy:
        //   - BlockingWaitStrategy (CPU 효율 좋음, 지연 조금 있음)
        //   - BusySpinWaitStrategy (CPU 100% 사용, 지연 최소) -> 고성능 서버용
        Disruptor<LogEvent> disruptor = new Disruptor<>(
                LogEvent.FACTORY,
                BUFFER_SIZE,
                threadFactory,
                ProducerType.MULTI,
                new com.lmax.disruptor.BlockingWaitStrategy()
        );

        // 핸들러(소비자) 등록
        disruptor.handleEventsWith(logEventHandler);

        // 시작
        disruptor.start();
        return disruptor;
    }

}
