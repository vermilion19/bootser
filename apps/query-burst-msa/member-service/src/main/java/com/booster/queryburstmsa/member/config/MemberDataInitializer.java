package com.booster.queryburstmsa.member.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class MemberDataInitializer {

    private static final String[] GRADES = {"BASIC", "BASIC", "BASIC", "VIP", "VVIP"};
    private static final String[] REGIONS = {"SEOUL", "GYEONGGI", "BUSAN", "INCHEON", "DAEGU", "GWANGJU"};

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private final AtomicReference<String> status = new AtomicReference<>("대기 중");

    private final JdbcTemplate jdbcTemplate;

    @Value("${data.init.member-count:1000000}")
    private int memberCount;

    @Value("${data.init.batch-size:5000}")
    private int batchSize;

    public void initializeAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 적재가 진행 중입니다.");
        }
        CompletableFuture.runAsync(() -> {
            try {
                initialize();
            } finally {
                running.set(false);
            }
        });
    }

    private void initialize() {
        status.set("member 데이터 초기화 중");
        jdbcTemplate.execute("TRUNCATE TABLE member_master RESTART IDENTITY CASCADE");

        String sql = "INSERT INTO member_master (id, email, name, grade, region, created_at, updated_at) VALUES (?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        for (int offset = 0; offset < memberCount; offset += batchSize) {
            List<Object[]> batch = new ArrayList<>(batchSize);
            int end = Math.min(offset + batchSize, memberCount);
            for (int i = offset; i < end; i++) {
                long id = i + 1L;
                batch.add(new Object[]{
                        id,
                        "msa-user" + id + "@example.com",
                        "회원_" + id,
                        GRADES[i % GRADES.length],
                        REGIONS[i % REGIONS.length],
                        now,
                        now
                });
            }
            jdbcTemplate.batchUpdate(sql, batch);
            status.set("member 적재 중: " + end + "/" + memberCount);
        }

        status.set("완료");
    }
}
