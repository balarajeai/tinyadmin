package com.tinyadmin.cloud.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Standalone password encoder configuration.
 * 
 * Extracted from SecurityConfig to break circular dependency:
 * - OperatorAuthenticationProvider → PasswordEncoder
 * - SecurityConfig → OperatorAuthenticationProvider
 * 
 * This configuration has no dependencies and can be created first.
 */
@Configuration
public class PasswordEncoderConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
