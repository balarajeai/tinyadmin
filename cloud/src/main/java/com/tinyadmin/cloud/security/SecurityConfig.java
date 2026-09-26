package com.tinyadmin.cloud.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for V1 MVP.
 * 
 * CRITICAL CHANGES:
 * - Operator mutation APIs (preview/confirm/execute) require authentication
 * - OperatorAuthenticationProvider wired to return OperatorAuthenticationPrincipal
 * - Agent protocol endpoints (/agent/v1/*) use separate authentication (challenge-response)
 * - No permitAll on mutation endpoints
 * - actor_id is server-assigned from authenticated session, never client-supplied
 * 
 * V1 implementation: HTTP Basic Auth with custom AuthenticationProvider
 * Production: Replace with proper session management or JWT
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final OperatorAuthenticationProvider operatorAuthenticationProvider;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Agent protocol: custom authentication (challenge-response over WebSocket)
                .requestMatchers("/agent/v1/**").permitAll()
                
                // Operator mutation APIs: require authentication
                .requestMatchers("/api/v1/operations/preview").authenticated()
                .requestMatchers("/api/v1/operations/confirm").authenticated()
                .requestMatchers("/api/v1/operations/execute").authenticated()
                .requestMatchers("/api/v1/operations/{id}").authenticated()
                
                // Everything else: permit for V1 (actuator, health, etc.)
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> {})
            // CRITICAL: Wire OperatorAuthenticationProvider to return OperatorAuthenticationPrincipal
            .authenticationProvider(operatorAuthenticationProvider);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

