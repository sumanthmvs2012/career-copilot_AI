package com.careercopilot.mail;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for refresh tokens at rest. Output is base64(iv + ciphertext + tag) with a fresh random IV per
 * call. Exception messages never contain the key, the token or the ciphertext.
 */
@Component
public class TokenCipher {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    @Autowired
    public TokenCipher(MailProperties properties) {
        this(properties.tokenKey());
    }

    /** A blank key means the mailbox feature is off; encrypt and decrypt then fail with MailNotConfiguredException. */
    public TokenCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MAIL_TOKEN_KEY must be base64 of 32 bytes");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("MAIL_TOKEN_KEY must be base64 of 32 bytes");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the token");
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= IV_BYTES) {
                throw new IllegalStateException("Could not decrypt the token");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt the token");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new MailNotConfiguredException();
        }
    }
}
