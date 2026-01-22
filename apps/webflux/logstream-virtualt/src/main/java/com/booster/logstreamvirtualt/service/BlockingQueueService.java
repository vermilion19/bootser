package com.booster.logstreamvirtualt.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class BlockingQueueService {

    // 1. 표준 블로킹 큐 사용 (최대 100만 건)
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(1024 * 1024);

    // 파일 쓰기 관련 (이전과 동일)
    private static final String FILE_PATH = "access_logs_vt.txt";
    private static final int BUFFER_SIZE = 1024 * 1024 * 4;
    private FileChannel fileChannel;
    private ByteBuffer buffer;

    // 소비 스레드 (가상 스레드 아님 - 백그라운드 워커는 플랫폼 스레드가 유리할 수 있음)
    private Thread consumerThread;
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        try {
            // 파일 채널 초기화 (이전과 동일)
            File file = new File(FILE_PATH);
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            this.fileChannel = raf.getChannel();
            this.fileChannel.position(this.fileChannel.size());
            this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            // 2. 소비자 스레드 시작
            this.consumerThread = new Thread(this::consumeLoop);
            this.consumerThread.setName("log-consumer-thread");
            this.consumerThread.start();

            log.info("Virtual Thread Log Server Initialized.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 생산자 (Controller가 호출)
    public void produce(String logData) {
        // 큐가 꽉 차면 예외 발생 대신 false 반환하거나 대기할 수 있음.
        // 여기서는 offer로 시도.
        if (!queue.offer(logData)) {
            log.warn("Queue is full! Dropping log.");
        }
    }

    // 소비자 루프
    private void consumeLoop() {
        List<String> batch = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                // 3. 큐에서 데이터를 가져옴 (배치 처리)
                // drainTo: 큐에 있는걸 싹 긁어옴 (Lock 비용 절감 효과)
                int count = queue.drainTo(batch, 5000);

                if (count == 0) {
                    // 데이터 없으면 잠깐 쉼 (CPU 과열 방지)
                    Thread.sleep(1);
                    continue;
                }

                // 4. 파일 버퍼에 쓰기
                for (String logData : batch) {
                    writeToBuffer(logData);
                }

                // 5. 배치 끝날 때마다 플러시 시도
                flush();
                batch.clear();

            } catch (Exception e) {
                log.error("Consumer error", e);
            }
        }
    }

    private void writeToBuffer(String logData) {
        byte[] bytes = (logData + "\n").getBytes(StandardCharsets.UTF_8);
        if (buffer.remaining() < bytes.length) {
            flush();
        }
        buffer.put(bytes);
    }

    private void flush() {
        if (buffer.position() > 0) {
            buffer.flip();
            try {
                fileChannel.write(buffer);
            } catch (Exception e) {
                log.error("Write error", e);
            } finally {
                buffer.clear();
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        running = false;
        try {
            consumerThread.join();
            flush();
            fileChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
