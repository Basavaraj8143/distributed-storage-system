package com.byteharvest.master.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Pure helper, no state and no dependencies, so it is a static utility rather than a Spring bean. */
public final class Checksums {

    private Checksums() {
    }

    /** Lower-case hex SHA-256 of the data (64 characters). */
    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
