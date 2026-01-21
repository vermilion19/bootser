package com.booster.massivesseservice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WindowsSimpleLoadTester {
    private static final int USER_COUNT = 300;
    private static final String SERVER_URL = "http://localhost:8080/sse/connect/";

    // 카운터를 static으로 빼서 어디서든 접근 가능하게 변경
    private static final AtomicInteger successCount = new AtomicInteger(0);

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(USER_COUNT);

        System.out.println(">>> Test Start: Connecting " + USER_COUNT + " users...");

        for (int i = 1; i <= USER_COUNT; i++) {
            final String userId = "user_" + i;
            executor.submit(() -> {
                try {
                    connectSSE(userId);
                } catch (Exception e) {
                    System.err.println("[" + userId + "] Connection Failed: " + e.getMessage());
                }
            });

            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
    }

    private static void connectSSE(String userId) throws Exception {
        URL url = new URL(SERVER_URL + userId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setReadTimeout(0);
        connection.setDoInput(true);

        // 1. 응답 코드가 200(OK)이면 연결은 성공한 것임!
        if (connection.getResponseCode() == 200) {

            // [수정된 위치] 여기서 카운트를 올려야 합니다! (데이터 읽기 대기하기 전에)
            int current = successCount.incrementAndGet();
            if (current % 50 == 0) {
                System.out.println(">>> Current connected users: " + current);
            }

            // 2. 이제 데이터를 기다립니다 (여기서 무한 대기하므로 코드가 안 넘어감)
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // 메시지 수신 대기 중...
                // (확인용) 첫 번째 유저만 메시지를 출력해서 제대로 동작하는지 보기
                if (userId.equals("user_1")) {
                    System.out.println("[User-1 Received]: " + line);
                }
            }
        } else {
            throw new RuntimeException("Status: " + connection.getResponseCode());
        }
    }
}
