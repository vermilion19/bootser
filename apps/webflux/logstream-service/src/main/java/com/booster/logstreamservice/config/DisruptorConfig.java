package com.booster.logstreamservice.config;

import com.booster.logstreamservice.event.LogEvent;
import com.booster.logstreamservice.handler.LogEventHandler;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DisruptorConfig {

    private final LogStreamProperties properties;

    @Bean
    public Disruptor<LogEvent> disruptor(LogEventHandler logEventHandler) {
        ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;

        // [P2] 설정에서 버퍼 크기와 WaitStrategy 읽기
        int bufferSize = properties.disruptor().bufferSize();
        WaitStrategy waitStrategy = createWaitStrategy(properties.disruptor().waitStrategy());

        Disruptor<LogEvent> disruptor = new Disruptor<>(
                LogEvent.FACTORY,
                bufferSize,
                threadFactory,
                ProducerType.MULTI,
                waitStrategy
        );

        disruptor.handleEventsWith(logEventHandler);
        disruptor.start();

        log.info("Disruptor started: bufferSize={}, waitStrategy={}",
                bufferSize, properties.disruptor().waitStrategy());

        return disruptor;
    }

    private WaitStrategy createWaitStrategy(String strategy) {
        return switch (strategy.toUpperCase()) {
            case "YIELDING" -> new YieldingWaitStrategy();
            case "BUSY_SPIN" -> new BusySpinWaitStrategy();
            default -> new BlockingWaitStrategy();
        };
    }

    @Bean
    public DisruptorLifecycle disruptorLifecycle(Disruptor<LogEvent> disruptor, LogEventHandler logEventHandler) {
        return new DisruptorLifecycle(disruptor, logEventHandler);
    }

    @RequiredArgsConstructor
    public static class DisruptorLifecycle implements SmartLifecycle {

        private final Disruptor<LogEvent> disruptor;
        private final LogEventHandler logEventHandler;
        private boolean running = false;

        @Override
        public void start() {
            this.running = true;
        }

        @Override
        public void stop() {
            if (this.running) {
                // [P3] System.out.println → log.info
                log.info("Graceful Shutdown Initiated: Flushing remaining logs...");

                // [P1] Disruptor 종료 (기존 이벤트 처리 완료 대기)
                disruptor.shutdown();

                // 마지막 버퍼 플러시
                logEventHandler.forceFlush();

                log.info("Graceful Shutdown Completed. All logs saved.");
                this.running = false;
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            // 다른 빈보다 먼저 종료되도록 (높은 값 = 먼저 종료)
            return Integer.MAX_VALUE;
        }
    }
}
