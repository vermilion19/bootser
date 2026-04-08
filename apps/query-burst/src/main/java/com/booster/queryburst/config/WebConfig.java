package com.booster.queryburst.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // OpenMetrics 형식 지원을 위한 ByteArrayHttpMessageConverter 추가
        ByteArrayHttpMessageConverter byteArrayConverter = new ByteArrayHttpMessageConverter();
        byteArrayConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.parseMediaType("application/openmetrics-text;version=1.0.0;charset=utf-8"),
                MediaType.parseMediaType("text/plain;version=0.0.4;charset=utf-8")
        ));
        converters.add(0, byteArrayConverter);
    }
}
