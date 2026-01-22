package com.booster.logstreamservice.handler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LogFileWriter {

    private static final String FILE_PATH = "access_logs.txt";
    // 4MB 버퍼 (일반적으로 OS Page Size인 4KB의 배수로 잡는 것이 유리함)
    private static final int BUFFER_SIZE = 1024 * 1024 * 4;

    private FileChannel fileChannel;
    private ByteBuffer buffer;

    @PostConstruct
    public void init() {
        try {
            File file = new File(FILE_PATH);
            // "rw": 읽기/쓰기 모드로 파일 열기
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            this.fileChannel = raf.getChannel();

            // 파일 끝으로 이동 (Append 모드)
            this.fileChannel.position(this.fileChannel.size());

            // Direct Buffer 할당 (OS 네이티브 메모리 사용)
            this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            log.info("LogFileWriter initialized using Direct Buffer ({} bytes)", BUFFER_SIZE);

        } catch (Exception e) {
            log.error("Failed to initialize LogFileWriter", e);
            throw new RuntimeException(e);
        }
    }

    public void write(String logData) {
        byte[] bytes = (logData + "\n").getBytes(StandardCharsets.UTF_8);

        if (buffer.remaining() < bytes.length) {
            flush();
        }
        buffer.put(bytes);
    }

    public void flush() {
        if (buffer.position() > 0) {
            buffer.flip(); // 읽기 모드로 전환
            try {
                fileChannel.write(buffer);
                // force(false): OS Page Cache까지만 기록 (디스크 물리 동기화는 OS에 위임 -> 속도 극대화)
                // force(true): 디스크 물리 기록까지 대기 -> 속도 느림, 안전성 높음
                // 로그 시스템은 보통 false로 충분함
                // fileChannel.force(false);
            }catch (Exception e) {
                log.error("Failed to write to file", e);
            } finally {
                buffer.clear();
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        flush(); // 남은 데이터 저장
        try {
            if (fileChannel != null) fileChannel.close();
        } catch (Exception e) {
            // ignore
        }
    }
}
