package com.example.tripaudit.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Small helper around SHA-256 so the rest of the code never touches MessageDigest. */
public final class Hashing {

    private Hashing() { }

    /** @return the SHA-256 of the UTF-8 bytes of {@code input}, as 64 lowercase hex characters. */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must support SHA-256, so this cannot happen in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
