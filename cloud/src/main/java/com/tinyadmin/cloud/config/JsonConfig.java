package com.tinyadmin.cloud.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON serialization configuration.
 * 
 * CRITICAL: Disables FAIL_ON_UNKNOWN_PROPERTIES to prevent unknown JSON fields
 * from bypassing authentication checks with 400 errors before controller execution.
 * This aligns with Spring Boot defaults and ensures security validations execute first.
 */
@Configuration
public class JsonConfig {
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Align with Spring Boot defaults: ignore unknown properties
        // CRITICAL: Prevents 400 deserialization errors from short-circuiting auth checks
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
