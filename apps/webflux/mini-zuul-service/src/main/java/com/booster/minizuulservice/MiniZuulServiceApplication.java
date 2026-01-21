package com.booster.minizuulservice;

import com.booster.minizuulservice.server.NettyServer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@RequiredArgsConstructor
@ConfigurationPropertiesScan
@SpringBootApplication
public class MiniZuulServiceApplication implements CommandLineRunner {

    private final NettyServer nettyServer;

    public static void main(String[] args) {
        SpringApplication.run(MiniZuulServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> Netty Server Starting...");
        nettyServer.start();
    }
}
