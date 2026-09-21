package net.arthonetwork.donation.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password storage.
 *
 * <p>New hashes are PBKDF2-HMAC-SHA256 with a random salt per password, stored as
 * {@code pbkdf2$<iterations>$<salt>$<hash>} (base64). The iteration count travels
 * with each hash, so it can be raised later without invalidating existing ones.
 *
 * <p>Passwords used to be stored as a bare, unsalted SHA-256 hex digest: identical
 * passwords gave identical hashes, and one leaked userdata.yml let every password
 * be recovered almost instantly with a precomputed table. Those legacy hashes are
 * still accepted, so nobody has to change anything: {@link #needsUpgrade} tells the
 * caller to store a fresh hash the next time the right password is typed.
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2";
    /** Costly enough to slow offline guessing, cheap enough not to stall the main thread on a login. */
    static final int ITERATIONS = 300_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(derived);
    }

    /** True when {@code password} matches {@code stored}, in either the current or the legacy format. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        if (isLegacy(stored)) {
            // Constant-time even for the old format: no reason to leak how much of a hash matched.
            return MessageDigest.isEqual(legacyHash(password).getBytes(StandardCharsets.UTF_8),
                    stored.toLowerCase().getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals(PREFIX)) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (iterations < 1 || salt.length == 0 || expected.length == 0) {
                return false;
            }
            return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
        } catch (IllegalArgumentException malformed) {
            return false; // a hand-edited or truncated entry must fail closed, never throw
        }
    }

    /** True for a legacy hash, or one made with fewer iterations than the current cost. */
    public static boolean needsUpgrade(String stored) {
        if (stored == null) {
            return false;
        }
        if (isLegacy(stored)) {
            return true;
        }
        String[] parts = stored.split("\\$");
        try {
            return parts.length == 4 && Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException malformed) {
            return false;
        }
    }

    private static boolean isLegacy(String stored) {
        return stored.length() == 64 && stored.matches("[0-9a-fA-F]{64}");
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            // Mandated by every JDK: better to fail loudly than to store nothing (a null hash used to erase the password).
            throw new IllegalStateException("PBKDF2WithHmacSHA256 indisponible", e);
        }
    }

    private static String legacyHash(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
