package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY =
            Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes());

    @Test
    void roundTripReturnsTheOriginalToken() {
        TokenCipher cipher = new TokenCipher(KEY);

        String encrypted = cipher.encrypt("1//refresh-token-abc");

        assertThat(encrypted).doesNotContain("refresh-token-abc");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("1//refresh-token-abc");
    }

    @Test
    void everyEncryptionUsesAFreshIv() {
        TokenCipher cipher = new TokenCipher(KEY);

        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void tamperedOrWrongKeyCiphertextIsRejectedWithoutLeakingAnything() {
        TokenCipher cipher = new TokenCipher(KEY);
        String encrypted = cipher.encrypt("secret-token");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret-token").hasMessageNotContaining(KEY);
        assertThatThrownBy(() -> new TokenCipher(OTHER_KEY).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("not base64 !!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[5])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aBlankKeyMeansNotConfigured() {
        TokenCipher cipher = new TokenCipher("");

        assertThatThrownBy(() -> cipher.encrypt("x")).isInstanceOf(MailNotConfiguredException.class);
        assertThatThrownBy(() -> cipher.decrypt("x")).isInstanceOf(MailNotConfiguredException.class);
    }

    @Test
    void aKeyOfTheWrongSizeOrFormatFailsFastWithoutPrintingIt() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new TokenCipher(shortKey)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortKey);
        assertThatThrownBy(() -> new TokenCipher("%%%not-base64%%%")).isInstanceOf(IllegalStateException.class);
    }
}
