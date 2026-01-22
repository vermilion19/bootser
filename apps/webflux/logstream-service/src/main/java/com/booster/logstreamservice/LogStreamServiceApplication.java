package com.booster.logstreamservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LogStreamServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogStreamServiceApplication.class, args);
    }
}
