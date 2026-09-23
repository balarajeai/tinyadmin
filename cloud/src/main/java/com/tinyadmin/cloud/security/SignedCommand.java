package com.tinyadmin.cloud.security;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SignedCommand {
    private Map<String, Object> envelope;
    private Map<String, Object> mutationPayload;
}
