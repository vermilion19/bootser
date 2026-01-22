package com.booster.logstreamservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logstream")
public record LogStreamProperties(
        DisruptorConfig disruptor,
        FileConfig file
) {
    public record DisruptorConfig(
            int bufferSize,
            String waitStrategy
    ) {
        public DisruptorConfig {
            if (bufferSize <= 0) bufferSize = 1024 * 1024;  // 기본값 1M
            if (waitStrategy == null) waitStrategy = "BLOCKING";
        }
    }

    public record FileConfig(
            String path,
            int bufferSize
    ) {
        public FileConfig {
            if (path == null || path.isBlank()) path = "access_logs.txt";
            if (bufferSize <= 0) bufferSize = 4 * 1024 * 1024;  // 기본값 4MB
        }
    }
}
