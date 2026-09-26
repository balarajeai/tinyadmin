package com.tinyadmin.cloud;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Spring Boot integration tests with Testcontainers.
 * 
 * Uses singleton static-start pattern to avoid multiple container instances
 * when Spring caches application contexts across subclasses.
 * 
 * Container is started once per JVM and shared across all test classes.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    
    static final PostgreSQLContainer<?> POSTGRES;
    
    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tinyadmin_test")
            .withUsername("test")
            .withPassword("test");
        POSTGRES.start();
    }
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
