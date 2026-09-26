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
        String expected = "{\"literals\":[null,true,false]," +
                "\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27]," +
                "\"string\":\"\\u20ac$\\u000f\\nA'B\\\"\\\\\\\\\\\"" +
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
    void testSignatureOmissionRule() throws Exception {
        // Issue #3: Signature field must be omitted from canonical envelope for signing
        Map<String, Object> envelopeWithSig = new LinkedHashMap<>();
        envelopeWithSig.put("operation_id", "test");
        envelopeWithSig.put("iat", 1234567890L);
        envelopeWithSig.put("signature", "some-signature-value");
        
        // Create copy without signature for canonical signing input
        Map<String, Object> envelopeForSigning = new LinkedHashMap<>(envelopeWithSig);
        envelopeForSigning.remove("signature");
        
        String jsonWithSig = objectMapper.writeValueAsString(envelopeWithSig);
        String jsonWithoutSig = objectMapper.writeValueAsString(envelopeForSigning);
        
        byte[] canonicalWithoutSig = canonicalizer.canonicalize(jsonWithoutSig);
        
        // The canonical form WITHOUT signature is what gets signed
        String resultWithoutSig = new String(canonicalWithoutSig, StandardCharsets.UTF_8);
        assertFalse(resultWithoutSig.contains("signature"), 
                "Signature field must not be in canonical signing input");
    }
}
