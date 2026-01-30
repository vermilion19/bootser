package com.booster.core.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Configuration
public class JacksonConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        SimpleModule javaTimeFixModule = new SimpleModule();
        javaTimeFixModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE));
        javaTimeFixModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        javaTimeFixModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ISO_LOCAL_TIME));

        JsonMapper mapper = JsonMapper.builder()
                .addModule(javaTimeFixModule)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        converters.addFirst(new JacksonJsonHttpMessageConverter(mapper));
    }
}
