package com.masl.goofy_protocol_core.crypto.isolated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;

public class SecretUtils {
    private static final Logger log = LoggerFactory.getLogger(SecretUtils.class);

    public static final String ENC_DELIMITER = ".";
    public static final int DEFAULT_ITERATIONS = 100_000;
    public static final String DEFAULT_DETERMINISTIC_SALT = "Goofy Protocol Default Salt";

    public static final int DEFAULT_HANDLE_ROOT_ITERATIONS = 300_000;
    public static final int DEFAULT_HANDLE_WORD_ITERATIONS = 50_000;
    public static final String DEFAULT_HANDLE_ROOT_SALT = "Goofy Protocol Root Salt";
    public static final String DEFAULT_HANDLE_WORD_SALT = "Goofy Protocol Derived Word Salt";

    public static byte[] symmSecretFromSecret(String secret, String salt, int size) {
        return symmSecretFromSecret(secret, salt, size, DEFAULT_ITERATIONS);
    }

    public static byte[] symmSecretFromSecret(String secret, String salt, int size, int iterations) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt.getBytes(), iterations, size);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            log.error("Error generating symmetric secret from secret: {}", e.getMessage(), e);
            return null;
        }
    }

    public static byte[] sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            log.error("Error generating SHA-256 hash: {}", e.getMessage(), e);
            return null;
        }
    }
}
