package com.booster.waitingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WaitingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaitingServiceApplication.class, args);
    }

}
