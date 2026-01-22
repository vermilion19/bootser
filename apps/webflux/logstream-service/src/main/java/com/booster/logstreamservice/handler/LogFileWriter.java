package com.booster.logstreamservice.handler;

import com.booster.logstreamservice.config.LogStreamProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogFileWriter {

    private final LogStreamProperties properties;

    // [P1] RandomAccessFile 참조 보관 (핸들 누수 방지)
    private RandomAccessFile raf;
    private FileChannel fileChannel;
    private ByteBuffer buffer;
    private int bufferSize;

    @PostConstruct
    public void init() {
        try {
            String filePath = properties.file().path();
            this.bufferSize = properties.file().bufferSize();

            File file = new File(filePath);
            // [P1] raf 필드로 보관
            this.raf = new RandomAccessFile(file, "rw");
            this.fileChannel = raf.getChannel();

            // 파일 끝으로 이동 (Append 모드)
            this.fileChannel.position(this.fileChannel.size());

            // Direct Buffer 할당 (OS 네이티브 메모리 사용)
            this.buffer = ByteBuffer.allocateDirect(bufferSize);
            log.info("LogFileWriter initialized: path={}, bufferSize={} bytes", filePath, bufferSize);

        } catch (Exception e) {
            log.error("Failed to initialize LogFileWriter", e);
            throw new RuntimeException(e);
        }
    }

    public void write(String logData) {
        byte[] bytes = (logData + "\n").getBytes(StandardCharsets.UTF_8);

        // [P0] 버퍼보다 큰 로그는 직접 쓰기
        if (bytes.length > bufferSize) {
            flush();  // 기존 버퍼 플러시
            writeDirect(bytes);
            return;
        }

        // 일반 로그는 버퍼에 추가
        if (buffer.remaining() < bytes.length) {
            flush();
        }
        buffer.put(bytes);
    }

    /**
     * [P0] 대용량 로그 직접 쓰기 (버퍼 우회)
     */
    private void writeDirect(byte[] bytes) {
        try {
            fileChannel.write(ByteBuffer.wrap(bytes));
            log.debug("Large log written directly: {} bytes", bytes.length);
        } catch (IOException e) {
            log.error("Failed to write large log directly", e);
        }
    }

    public void flush() {
        if (buffer.position() > 0) {
            buffer.flip(); // 읽기 모드로 전환
            try {
                fileChannel.write(buffer);
                // force(false): 처리량 우선 (의도적으로 비활성화)
                // fileChannel.force(false);
            } catch (Exception e) {
                log.error("Failed to write to file", e);
            } finally {
                buffer.clear();
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("LogFileWriter cleanup: flushing remaining data...");
        flush();
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
            // [P1] RandomAccessFile 명시적 닫기
            if (raf != null) {
                raf.close();
            }
            log.info("LogFileWriter cleanup completed");
        } catch (Exception e) {
            log.error("Failed to close file resources", e);
        }
    }
}
