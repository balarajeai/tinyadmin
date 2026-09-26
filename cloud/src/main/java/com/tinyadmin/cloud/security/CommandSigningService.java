package com.tinyadmin.cloud.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * Cloud command signing service implementing Issue #3 / ADR 0007 protocol.
 * 
 * Uses Ed25519 signatures over RFC 8785 JCS canonical JSON.
 * Mutation payload integrity via SHA-256 digest binding.
 */
@Service
@Slf4j
public class CommandSigningService {
    
    private final ObjectMapper objectMapper;
    private final JcsCanonicalizer canonicalizer;
    
    @Value("${tinyadmin.signing.kid:cloud-signing-key-v1}")
    private String keyId;
    
    @Value("${tinyadmin.signing.private-key:}")
    private String privateKeyBase64;
    
    @Value("${tinyadmin.signing.allow-ephemeral:false}")
    private boolean allowEphemeral;
    
    private PrivateKey signingKey;
    private PublicKey verificationKey;
    
    public CommandSigningService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalizer = new JcsCanonicalizer(objectMapper);
    }
    
    @PostConstruct
    public void initialize() {
        try {
            // Register BouncyCastle provider for consistent Ed25519 handling
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            
            if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
                if (!allowEphemeral) {
                    throw new IllegalStateException(
                        "No signing key configured and ephemeral keys not allowed. " +
                        "Set tinyadmin.signing.private-key or set tinyadmin.signing.allow-ephemeral=true for testing."
                    );
                }
                
                log.warn("No signing key configured, generating ephemeral Ed25519 key pair (allow-ephemeral=true)");
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
                KeyPair keyPair = keyGen.generateKeyPair();
                signingKey = keyPair.getPrivate();
                verificationKey = keyPair.getPublic();
                
                String publicKeyBase64 = Base64.getEncoder().encodeToString(verificationKey.getEncoded());
                log.info("Generated ephemeral Ed25519 key. Public key (X.509): {}", publicKeyBase64);
            } else {
                byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
                
                // Use BouncyCastle's KeyFactory for consistent Ed25519 handling
                KeyFactory bcKeyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
                PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
                signingKey = bcKeyFactory.generatePrivate(privateKeySpec);
                
                // Derive public key from private key using BouncyCastle
                byte[] publicKeyBytes = extractEd25519PublicKeyFromPKCS8(privateKeyBytes);
                X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
                verificationKey = bcKeyFactory.generatePublic(publicKeySpec);
                
                log.info("Loaded Ed25519 signing key with kid={}", keyId);
                String publicKeyBase64 = Base64.getEncoder().encodeToString(verificationKey.getEncoded());
                log.info("Derived public key (X.509): {}", publicKeyBase64);
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            log.error("Failed to initialize Ed25519 signing key", e);
            throw new RuntimeException("Failed to initialize command signing", e);
        }
    }
    
    /**
     * Derives Ed25519 public key from PKCS8 private key using BouncyCastle.
     * 
     * Ed25519 public keys can be mathematically derived from private keys.
     * BouncyCastle provides proper parsing and key derivation.
     */
    private byte[] extractEd25519PublicKeyFromPKCS8(byte[] pkcs8Bytes) {
        try {
            // Parse PKCS8 structure using BouncyCastle
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pkcs8Bytes);
            
            // Extract the 32-byte Ed25519 private seed from the OCTET STRING
            byte[] privateSeed = privateKeyInfo.getPrivateKey().getOctets();
            
            // Use BouncyCastle to derive the public key from the private seed
            Ed25519PrivateKeyParameters privateKeyParams = new Ed25519PrivateKeyParameters(privateSeed, 0);
            byte[] publicKeyBytes = privateKeyParams.generatePublicKey().getEncoded();
            
            // Construct X.509 SubjectPublicKeyInfo structure for Ed25519
            // OID 1.3.101.112 is Ed25519
            ASN1ObjectIdentifier ed25519Oid = new ASN1ObjectIdentifier("1.3.101.112");
            AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(ed25519Oid);
            SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(algorithmIdentifier, publicKeyBytes);
            
            return publicKeyInfo.getEncoded();
            
        } catch (Exception e) {
            throw new RuntimeException("Could not derive Ed25519 public key from PKCS8 private key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates SHA-256 digest of canonical mutation payload per Issue #3 §6.1.
     * Uses RFC 8785 JCS for canonicalization.
     */
    public String createMutationPayloadDigest(Map<String, Object> mutationPayload) {
        try {
            String json = objectMapper.writeValueAsString(mutationPayload);
            byte[] canonicalBytes = canonicalizer.canonicalize(json);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalBytes);
            
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Failed to create mutation payload digest", e);
            throw new RuntimeException("Failed to create mutation payload digest", e);
        }
    }
    
    /**
     * Creates signed command per Issue #3 protocol.
     * Envelope signed with Ed25519 over JCS canonical bytes (excluding signature field).
     * Mutation payload integrity via SHA-256 digest binding.
     */
    public SignedCommand createSignedCommand(
        UUID operationId,
        UUID organizationId,
        UUID environmentId,
        UUID agentId,
        UUID connectionId,
        UUID actorUserId,
        Map<String, Object> actionOrFieldOp,
        Map<String, Object> mutationPayload,
        int maxAffectedRecords
    ) {
        String payloadDigest = createMutationPayloadDigest(mutationPayload);
        
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kid", keyId);
        envelope.put("operation_id", operationId.toString());
        envelope.put("actor_id", actorUserId != null ? actorUserId.toString() : "system");
        envelope.put("organization_id", organizationId.toString());
        envelope.put("environment_id", environmentId.toString());
        envelope.put("agent_id", agentId.toString());
        envelope.put("connection_id", connectionId.toString());
        envelope.put("action_or_field_op", actionOrFieldOp);
        envelope.put("mutation_payload_sha256", payloadDigest);
        envelope.put("max_affected_records", maxAffectedRecords);
        
        Instant now = Instant.now();
        envelope.put("iat", now.getEpochSecond());
        envelope.put("exp", now.plusSeconds(300).getEpochSecond());
        
        String signature = signEnvelope(envelope);
        envelope.put("signature", signature);
        
        return SignedCommand.builder()
            .envelope(envelope)
            .mutationPayload(mutationPayload)
            .build();
    }
    
    /**
     * Signs envelope using Ed25519 over RFC 8785 JCS canonical bytes.
     * Signature is base64url-encoded raw Ed25519 signature (64 bytes).
     * Uses BouncyCastle provider for consistent Ed25519 handling.
     */
    private String signEnvelope(Map<String, Object> envelopeWithoutSignature) {
        try {
            String json = objectMapper.writeValueAsString(envelopeWithoutSignature);
            byte[] canonicalBytes = canonicalizer.canonicalize(json);
            
            Signature signature = Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(signingKey);
            signature.update(canonicalBytes);
            byte[] signatureBytes = signature.sign();
            
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign envelope", e);
            throw new RuntimeException("Failed to sign envelope", e);
        }
    }
    
    /**
     * Verifies envelope signature for testing and Cloud self-check.
     * Agent will implement independent verification using public key.
     * Does not mutate the input envelope map.
     * Uses BouncyCastle provider for consistent Ed25519 handling.
     */
    public boolean verifyEnvelope(Map<String, Object> envelope, PublicKey publicKey) {
        try {
            // Copy envelope to avoid mutating caller's map
            Map<String, Object> envelopeCopy = new LinkedHashMap<>(envelope);
            String signatureB64 = (String) envelopeCopy.remove("signature");
            if (signatureB64 == null) {
                return false;
            }
            
            String json = objectMapper.writeValueAsString(envelopeCopy);
            byte[] canonicalBytes = canonicalizer.canonicalize(json);
            
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
            
            Signature signature = Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(publicKey);
            signature.update(canonicalBytes);
            
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to verify envelope signature", e);
            return false;
        }
    }
    
    public PublicKey getPublicKey() {
        return verificationKey;
    }
    
    public String getKeyId() {
        return keyId;
    }
    
    /**
     * Signs arbitrary bytes with Cloud command-signing key (Finding #3).
     * Used for result_ack and other authenticated Cloud responses.
     * 
     * @param canonicalBytes Canonical bytes to sign (e.g., JCS of ack payload)
     * @return Ed25519 signature bytes
     */
    public byte[] signBytes(byte[] canonicalBytes) {
        try {
            Signature signature = Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(signingKey);
            signature.update(canonicalBytes);
            return signature.sign();
        } catch (Exception e) {
            log.error("Failed to sign bytes", e);
            throw new RuntimeException("Failed to sign bytes", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

