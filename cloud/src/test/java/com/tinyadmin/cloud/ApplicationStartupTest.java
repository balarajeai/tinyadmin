package com.tinyadmin.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test to verify Spring application context loads successfully.
 * Inherits @SpringBootTest from BaseIntegrationTest.
 */
class ApplicationStartupTest extends BaseIntegrationTest {
    
    @Test
    void contextLoads() {
        assertTrue(true, "Application context should load successfully");
    }
}
