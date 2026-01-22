package com.booster.logstreamservice.handler;

import com.booster.logstreamservice.event.LogEvent;
import com.lmax.disruptor.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventHandler implements EventHandler<LogEvent> {

    private final LogFileWriter logFileWriter;
    private long count = 0;

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
        // 1. 메모리 버퍼에 기록 (매우 빠름)
        // 파일에 직접 쓰는게 아니라, ByteBuffer에 put만 함
        logFileWriter.write(event.getPayload());

        // 2. 배치 플러시 전략
        // endOfBatch: 생산자가 너무 빨라서 큐에 데이터가 쌓여있을 때,
        // Disruptor는 이것들을 한 번에 처리하라고 기회를 줍니다.
        // 이때가 바로 디스크로 밀어넣을(Flush) 타이밍입니다.
        if (endOfBatch) {
            logFileWriter.flush();
        }

        // (옵션) 진행 상황 로깅 (매번 찍으면 느리므로 10만건마다)
        if (++count % 100000 == 0) {
            log.info("Processed {} logs. Last Sequence: {}", count, sequence);
        }
    }

    public void forceFlush() {
        log.info("Force flushing remaining data in buffer...");
        logFileWriter.flush(); // 버퍼에 남은거 다 파일로 내리기
    }
}
