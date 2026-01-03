package com.booster.common;


import java.security.SecureRandom;
import java.time.Instant;

public class SnowflakeGenerator {

    // 1. 유일한 인스턴스를 내부에서 미리 만듭니다. (Thread-safe)
    private static final SnowflakeGenerator INSTANCE = new SnowflakeGenerator();

    private static final int UNUSED_BITS = 1;
    private static final int EPOCH_BITS = 41;
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long maxNodeId = (1L << NODE_ID_BITS) - 1;
    private static final long maxSequence = (1L << SEQUENCE_BITS) - 1;

    private static final long DEFAULT_CUSTOM_EPOCH = 1767225600000L; // 2026-01-01

    private final long nodeId;
    private final long customEpoch;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    // 2. 생성자를 private으로 막습니다.
    private SnowflakeGenerator() {
        this.nodeId = createNodeId();
        this.customEpoch = DEFAULT_CUSTOM_EPOCH;
    }

    // 3. 외부에서 바로 호출할 수 있는 static 메서드
    public static long nextId() {
        return INSTANCE.nextIdInternal();
    }

    // 실제 ID 생성 로직 (synchronized)
    private synchronized long nextIdInternal() {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Invalid System Clock!");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - customEpoch) << (NODE_ID_BITS + SEQUENCE_BITS))
                | (nodeId << SEQUENCE_BITS)
                | sequence;
    }

    private long timestamp() {
        return Instant.now().toEpochMilli();
    }

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp <= lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }

    private long createNodeId() {
        // ... (이전과 동일한 노드 ID 생성 로직) ...
        long nodeId;
        try {
            // (생략: 맥주소 로직)
            nodeId = (new SecureRandom().nextInt()); // 간소화
        } catch (Exception ex) {
            nodeId = (new SecureRandom().nextInt());
        }
        return nodeId & maxNodeId;
    }
}