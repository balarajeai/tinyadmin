package com.tinyadmin.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApplicationStartupTest extends BaseIntegrationTest {
    
    @Test
    void contextLoads() {
        assertTrue(true, "Application context should load successfully");
    }
}
