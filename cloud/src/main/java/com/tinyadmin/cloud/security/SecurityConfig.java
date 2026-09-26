package com.tinyadmin.cloud.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for V1 MVP.
 * 
 * CRITICAL CHANGES:
 * - Operator mutation APIs (preview/confirm/execute) require authentication
 * - Agent protocol endpoints (/agent/v1/*) use separate authentication (challenge-response)
 * - No permitAll on mutation endpoints
 * - actor_id is server-assigned from authenticated session, never client-supplied
 * 
 * V1 implementation: HTTP Basic Auth with in-memory users for MVP
 * Production: Replace with proper session management or JWT
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
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
            .httpBasic(basic -> {}); // V1 MVP: HTTP Basic Auth
        
        return http.build();
    }
    
    /**
     * V1 MVP: In-memory user store for testing/demo.
     * Production: Replace with database-backed UserDetailsService.
     */
    @Bean
    @Profile("!test")
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        var user = User.builder()
                .username("admin@tinyadmin.test")
                .password(passwordEncoder.encode("admin"))
                .roles("OPERATOR")
                .build();
        
        return new InMemoryUserDetailsManager(user);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

