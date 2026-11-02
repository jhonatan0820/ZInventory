package com.dotacioneszambrano.inventorio.service;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class PasswordHashService {

    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = KEY_LENGTH_BITS / 8;
    private static final int MIN_ACCEPTED_ITERATIONS = 100_000;
    private static final int MAX_ACCEPTED_ITERATIONS = 2_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    public String encode(String password) {
        if (password == null) throw new IllegalArgumentException("Password is required");
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                + "$" + encoder.encodeToString(hash);
    }

    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null) return false;
        return matchesPbkdf2(password, storedHash);
    }

    private static boolean matchesPbkdf2(String password, String storedHash) {
        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MIN_ACCEPTED_ITERATIONS || iterations > MAX_ACCEPTED_ITERATIONS) {
                return false;
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expectedHash = decoder.decode(parts[3]);
            if (salt.length < SALT_LENGTH_BYTES || expectedHash.length != HASH_LENGTH_BYTES) {
                return false;
            }
            return MessageDigest.isEqual(expectedHash, derive(password, salt, iterations));
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] passwordChars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, KEY_LENGTH_BITS);
        Arrays.fill(passwordChars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
