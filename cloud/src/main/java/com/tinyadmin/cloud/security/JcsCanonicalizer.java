package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * RFC 8785 JSON Canonicalization Scheme (JCS) implementation.
 * Produces deterministic canonical JSON for cryptographic operations.
 */
public class JcsCanonicalizer {
    
    private final ObjectMapper objectMapper;
    
    public JcsCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Canonicalizes JSON string per RFC 8785.
     * Key features:
     * - Keys sorted lexicographically
     * - No whitespace
     * - Numbers in standard format
     * - UTF-8 encoding
     */
    public byte[] canonicalize(String json) throws IOException {
        JsonNode node = objectMapper.readTree(json);
        String canonical = canonicalizeNode(node);
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
    
    private String canonicalizeNode(JsonNode node) {
        if (node.isObject()) {
            return canonicalizeObject(node);
        } else if (node.isArray()) {
            return canonicalizeArray(node);
        } else if (node.isTextual()) {
            return canonicalizeString(node.asText());
        } else if (node.isNumber()) {
            return canonicalizeNumber(node);
        } else if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        } else if (node.isNull()) {
            return "null";
        }
        return node.toString();
    }
    
    private String canonicalizeObject(JsonNode node) {
        TreeMap<String, JsonNode> sorted = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            sorted.put(field.getKey(), field.getValue());
        }
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(canonicalizeString(entry.getKey()));
            sb.append(":");
            sb.append(canonicalizeNode(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String canonicalizeArray(JsonNode node) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (JsonNode element : node) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(canonicalizeNode(element));
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String canonicalizeString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
    
    private String canonicalizeNumber(JsonNode node) {
        if (node.isIntegralNumber()) {
            return node.asText();
        } else {
            double d = node.asDouble();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "null";
            }
            return node.asText();
        }
    }
}
