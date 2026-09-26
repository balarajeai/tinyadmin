package com.tinyadmin.cloud.agent.protocol;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for Agent↔Cloud protocol (Issue #3 / ADR 0007).
 * 
 * Agents initiate wss:// connections outbound-only; no inbound Agent ports required.
 * TLS termination happens at nginx/reverse-proxy layer (production) or embedded Tomcat (dev).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final AgentProtocolHandler agentProtocolHandler;
    
    public WebSocketConfig(AgentProtocolHandler agentProtocolHandler) {
        this.agentProtocolHandler = agentProtocolHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Agent protocol endpoint: wss://cloud/agent/v1/ws
        // Agents authenticate via challenge-response after WS upgrade
        registry.addHandler(agentProtocolHandler, "/agent/v1/ws")
                .setAllowedOrigins("*"); // Agents connect from customer infrastructure
    }
}
