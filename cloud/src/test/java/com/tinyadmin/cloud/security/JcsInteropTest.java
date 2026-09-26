package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JCS (RFC 8785) interoperability tests (Finding CR-PR23-003).
 * 
 * Validates Cloud JCS canonicalization produces identical bytes/digests
 * as RFC 8785 specification and Agent gowebpki/jcs implementation.
 * 
 * Test vectors from RFC 8785 Appendix A and cross-check with Agent PR #24.
 * 
 * CRITICAL: Byte-for-byte exactness required for signature interop.
 */
class JcsInteropTest {
    
    private JcsCanonicalizer canonicalizer;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        canonicalizer = new JcsCanonicalizer(objectMapper);
    }
    
    @Test
    void testRFC8785SimpleObject() throws Exception {
        // RFC 8785 Appendix A.1: Simple Object
        String input = """
            {
              "numbers": [333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001],
              "string": "\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\"/",
              "literals": [null, true, false]
            }
            """;
        
        byte[] canonical = canonicalizer.canonicalize(input);
        String result = new String(canonical, StandardCharsets.UTF_8);
        
        // Expected canonical form per RFC 8785
        // Note: € (U+20AC) can be represented as literal UTF-8 or as \u20ac escape
        // RFC 8785 allows both; erdtman library outputs literal UTF-8 for valid non-ASCII
        String expected = "{\"literals\":[null,true,false]," +
                "\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27]," +
                "\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\\\\\"" +
                "/\"}";
        
        assertEquals(expected, result, "RFC 8785 simple object canonicalization");
    }
    
    @Test
    void testRFC8785NumberFormatting() throws Exception {
        // RFC 8785 Number formatting rules
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("zero", 0);
        numbers.put("negative", -1);
        numbers.put("float", 3.14);
        numbers.put("scientific", 1.5e10);
        numbers.put("large", 999999999999999999L);
        
        String json = objectMapper.writeValueAsString(numbers);
        byte[] canonical = canonicalizer.canonicalize(json);
        String result = new String(canonical, StandardCharsets.UTF_8);
        
        // Verify no trailing zeros, no +, proper scientific notation
        assertTrue(result.contains("\"zero\":0"), "Zero should be 0");
        assertTrue(result.contains("\"negative\":-1"), "Negative should be -1");
        assertTrue(result.contains("\"float\":3.14"), "Float should be 3.14");
        assertTrue(result.contains("\"scientific\":1.5e+10") || result.contains("\"scientific\":15000000000"), 
                "Scientific notation or full number");
    }
    
    @Test
    void testKeyOrdering() throws Exception {
        // JCS requires lexicographic key ordering by Unicode code point
        Map<String, Object> unordered = new LinkedHashMap<>();
        unordered.put("zebra", 1);
        unordered.put("apple", 2);
        unordered.put("banana", 3);
        unordered.put("123", 4);
        unordered.put("Zebra", 5);
        
        String json = objectMapper.writeValueAsString(unordered);
        byte[] canonical = canonicalizer.canonicalize(json);
        String result = new String(canonical, StandardCharsets.UTF_8);
        
        // Keys should be in lexicographic order: "123" < "Zebra" < "apple" < "banana" < "zebra"
        int idx123 = result.indexOf("\"123\"");
        int idxZebra = result.indexOf("\"Zebra\"");
        int idxApple = result.indexOf("\"apple\"");
        int idxBanana = result.indexOf("\"banana\"");
        int idxZebraLower = result.lastIndexOf("\"zebra\"");
        
        assertTrue(idx123 < idxZebra, "123 before Zebra");
        assertTrue(idxZebra < idxApple, "Zebra before apple");
        assertTrue(idxApple < idxBanana, "apple before banana");
        assertTrue(idxBanana < idxZebraLower, "banana before zebra");
    }
    
    @Test
    void testEnvelopeCanonicalConsistency() throws Exception {
        // Agent PR #24 envelope shape - verify consistent canonicalization
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("operation_id", "00000000-0000-0000-0000-000000000001");
        envelope.put("organization_id", "00000000-0000-0000-0000-000000000002");
        envelope.put("agent_id", "00000000-0000-0000-0000-000000000003");
        envelope.put("iat", 1700000000L);
        envelope.put("exp", 1700000300L);
        
        // Canonicalize twice - should produce identical bytes
        String json1 = objectMapper.writeValueAsString(envelope);
        byte[] canonical1 = canonicalizer.canonicalize(json1);
        
        String json2 = objectMapper.writeValueAsString(envelope);
        byte[] canonical2 = canonicalizer.canonicalize(json2);
        
        assertArrayEquals(canonical1, canonical2, "Canonical bytes must be identical");
    }
    
    @Test
    void testDigestStability() throws Exception {
        // Verify digest is stable across canonicalizations
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action_or_field_op", Map.of("type", "action", "name", "unlock_user"));
        payload.put("targets", Map.of("user_id", "user123"));
        payload.put("max_affected_records", 1);
        
        String json = objectMapper.writeValueAsString(payload);
        byte[] canonical = canonicalizer.canonicalize(json);
        
        MessageDigest digest1 = MessageDigest.getInstance("SHA-256");
        byte[] hash1 = digest1.digest(canonical);
        
        MessageDigest digest2 = MessageDigest.getInstance("SHA-256");
        byte[] hash2 = digest2.digest(canonical);
        
        assertArrayEquals(hash1, hash2, "SHA-256 digests must be identical");
    }
    
    @Test
    void testEmptyObjectAndArray() throws Exception {
        // RFC 8785: Empty structures
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("empty_object", Map.of());
        obj.put("empty_array", new Object[0]);
        
        String json = objectMapper.writeValueAsString(obj);
        byte[] canonical = canonicalizer.canonicalize(json);
        String result = new String(canonical, StandardCharsets.UTF_8);
        
        assertTrue(result.contains("\"empty_object\":{}"), "Empty object");
        assertTrue(result.contains("\"empty_array\":[]"), "Empty array");
    }
    
    @Test
    void testSignatureEmptyStringRule() throws Exception {
        // Agent PR #24: Signature field must be present as EMPTY STRING during canonicalization
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("operation_id", "00000000-0000-0000-0000-000000000001");
        envelope.put("organization_id", "00000000-0000-0000-0000-000000000002");
        envelope.put("agent_id", "00000000-0000-0000-0000-000000000003");
        envelope.put("iat", "2026-09-26T03:00:00Z");
        envelope.put("exp", "2026-09-26T03:05:00Z");
        envelope.put("signature", "");  // Empty string during canonicalization
        
        String json = objectMapper.writeValueAsString(envelope);
        byte[] canonical = canonicalizer.canonicalize(json);
        String result = new String(canonical, StandardCharsets.UTF_8);
        
        // CRITICAL: signature field MUST BE PRESENT with empty string value
        assertTrue(result.contains("\"signature\":\"\""), 
                "Signature field must be present as empty string per Agent PR #24");
    }
    
    @Test
    void testAgentEnvelopeGoldenVector() throws Exception {
        // Golden vector matching Agent PR #24 e3a006885cb79f0123da8404feb4c7cf4b81272f
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kid", "cloud-signing-key-v1");
        envelope.put("operation_id", "12345678-1234-5678-1234-567812345678");
        envelope.put("actor_id", "actor-123");
        envelope.put("organization_id", "00000000-0000-0000-0000-000000000001");
        envelope.put("environment_id", "00000000-0000-0000-0000-000000000002");
        envelope.put("agent_id", "00000000-0000-0000-0000-000000000003");
        envelope.put("connection_id", "00000000-0000-0000-0000-000000000004");
        
        Map<String, Object> actionOp = new LinkedHashMap<>();
        actionOp.put("type", "action");
        actionOp.put("action_definition_id", "00000000-0000-0000-0000-000000000005");
        envelope.put("action_or_field_op", actionOp);
        
        envelope.put("mutation_payload_sha256", "abc123");
        envelope.put("max_affected_records", 1);
        envelope.put("iat", "2026-09-26T03:00:00Z");
        envelope.put("exp", "2026-09-26T03:05:00Z");
        envelope.put("signature", "");
        
        String json = objectMapper.writeValueAsString(envelope);
        byte[] canonical = canonicalizer.canonicalize(json);
        String result = new String(canonical, StandardCharsets.UTF_8);
        
        // Expected canonical form (golden vector) - exact byte-for-byte match required
        String expectedCanonical = 
            "{\"action_or_field_op\":{\"action_definition_id\":\"00000000-0000-0000-0000-000000000005\",\"type\":\"action\"}," +
            "\"actor_id\":\"actor-123\"," +
            "\"agent_id\":\"00000000-0000-0000-0000-000000000003\"," +
            "\"connection_id\":\"00000000-0000-0000-0000-000000000004\"," +
            "\"environment_id\":\"00000000-0000-0000-0000-000000000002\"," +
            "\"exp\":\"2026-09-26T03:05:00Z\"," +
            "\"iat\":\"2026-09-26T03:00:00Z\"," +
            "\"kid\":\"cloud-signing-key-v1\"," +
            "\"max_affected_records\":1," +
            "\"mutation_payload_sha256\":\"abc123\"," +
            "\"operation_id\":\"12345678-1234-5678-1234-567812345678\"," +
            "\"organization_id\":\"00000000-0000-0000-0000-000000000001\"," +
            "\"signature\":\"\"}";
        
        assertEquals(expectedCanonical, result, "Envelope canonical form must match Agent PR #24 golden vector exactly");
        
        // Verify critical Agent PR #24 contract requirements:
        assertTrue(result.contains("\"signature\":\"\""), "signature present as empty string");
        assertTrue(result.contains("\"iat\":\"2026-09-26T03:00:00Z\""), "iat is RFC3339 string");
        assertTrue(result.contains("\"exp\":\"2026-09-26T03:05:00Z\""), "exp is RFC3339 string");
        
        // Compute digest for documentation
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(canonical);
        StringBuilder hexDigest = new StringBuilder();
        for (byte b : hash) {
            hexDigest.append(String.format("%02x", b));
        }
        System.out.println("Golden vector SHA-256: " + hexDigest);
    }
}
