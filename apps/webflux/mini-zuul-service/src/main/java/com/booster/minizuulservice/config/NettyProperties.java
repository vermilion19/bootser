package com.booster.minizuulservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mini-zuul")
public record NettyProperties(int port) {
}
