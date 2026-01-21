package com.booster.minizuulservice;

import com.booster.minizuulservice.server.NettyServer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Arrays;

@RequiredArgsConstructor
@ConfigurationPropertiesScan
@SpringBootApplication
public class MiniZuulServiceApplication implements CommandLineRunner {

    private final NettyServer nettyServer;

    public static void main(String[] args) {
        SpringApplication.run(MiniZuulServiceApplication.class, args);
    }

    @Override
    public void run(String @NonNull ... args){
        System.out.println(">>> Netty Server Starting..." + Arrays.toString(args));
        nettyServer.start();
    }
}
