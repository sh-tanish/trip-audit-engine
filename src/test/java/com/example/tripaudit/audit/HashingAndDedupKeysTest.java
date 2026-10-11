package com.example.tripaudit.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashingAndDedupKeysTest {

    @Test
    @DisplayName("Hashing.sha256Hex produces consistent 64-character lowercase hex output")
    void sha256HexProducesValidHash() {
        String hash1 = Hashing.sha256Hex("hello world");
        String hash2 = Hashing.sha256Hex("hello world");

        assertEquals(hash1, hash2, "Hashing must be deterministic");
        assertEquals(64, hash1.length(), "SHA-256 hex must be 64 characters");
        // Known SHA-256 for "hello world"
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash1);
    }

    @Test
    @DisplayName("DedupKeys prevents string concatenation collisions with '|' separator")
    void dedupKeysPreventsCollisions() {
        // Without delimiter, ("1", "23", "sub") and ("12", "3", "sub") would collide
        String key1 = DedupKeys.of(1L, "23", "sub");
        String key2 = DedupKeys.of(12L, "3", "sub");

        assertNotEquals(key1, key2, "DedupKeys must not collide for overlapping tripId and type strings");
    }
}
