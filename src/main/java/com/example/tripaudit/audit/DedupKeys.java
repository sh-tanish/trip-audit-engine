package com.example.tripaudit.audit;

/** Builds the deterministic key that makes violation inserts idempotent. */
public final class DedupKeys {

    private DedupKeys() { }

    /**
     * dedupKey = sha256(tripId | type | subjectKey).
     * The "|" separator stops ("1","23") and ("12","3") from producing the same input string.
     * The violation type is passed as its enum name (a String) so this package does not depend on domain.
     */
    public static String of(Long tripId, String type, String subjectKey) {
        return Hashing.sha256Hex(tripId + "|" + type + "|" + subjectKey);
    }
}
