package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * RFC 8785 JSON Canonicalization Scheme (JCS) implementation.
 * 
 * Uses proven erdtman/java-json-canonicalization library for RFC 8785 compliance.
 * Produces deterministic canonical JSON for cryptographic operations.
 * 
 * CR-PR23-003: Replaced custom implementation with maintained RFC 8785 library
 * to ensure 100% compliance with RFC 8785 specification and Agent gowebpki/jcs.
 */
public class JcsCanonicalizer {
    
    private final ObjectMapper objectMapper;
    
    public JcsCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Canonicalizes JSON string per RFC 8785.
     * 
     * Uses erdtman/java-json-canonicalization which implements:
     * - Keys sorted lexicographically by Unicode code point
     * - No whitespace
     * - Numbers in RFC 8785 canonical format (no trailing zeros, proper scientific notation)
     * - UTF-8 encoding
     * - Proper string escaping per RFC 8785 rules
     * 
     * @param json JSON string to canonicalize
     * @return Canonical JSON bytes (UTF-8)
     * @throws IOException if canonicalization fails
     */
    public byte[] canonicalize(String json) throws IOException {
        // Use proven RFC 8785 library for exact compliance
        JsonCanonicalizer canonicalizer = new JsonCanonicalizer(json);
        return canonicalizer.getEncodedUTF8();
    }
    
    /**
     * Canonicalizes JSON string and returns as String.
     * Convenience method for debugging/testing.
     */
    public String canonicalizeToString(String json) throws IOException {
        return new String(canonicalize(json), StandardCharsets.UTF_8);
    }
}
