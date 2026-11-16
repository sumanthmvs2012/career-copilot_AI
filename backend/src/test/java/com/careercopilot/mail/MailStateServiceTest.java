package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class MailStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    private SecretKey key;
    private JwtEncoder encoder;
    private MailProperties properties;

    @BeforeEach
    void setUp() {
        key = new SecretKeySpec("test-only-jwt-secret-0123456789-abcdefghijklmnop".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        properties = new MailProperties("", "", "", "", "http://localhost:5173", false, "", true, "", 7,
                Duration.ofMinutes(10), Duration.ofSeconds(5), Duration.ofSeconds(20));
    }

    private MailStateService serviceAt(Instant now) {
        return new MailStateService(encoder, key, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private String token(String issuer, Object purpose, Object uid, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(NOW).expiresAt(expiresAt);
        if (purpose != null) {
            claims.claim("purpose", purpose);
        }
        if (uid != null) {
            claims.claim("uid", uid);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void aFreshStateReturnsTheUserId() {
        MailStateService service = serviceAt(NOW);

        assertThat(service.verify(service.create(42L))).isEqualTo(42L);
    }

    @Test
    void anExpiredStateIsRejected() {
        String state = serviceAt(NOW).create(42L);

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(9))).verify(state)).isEqualTo(42L);
        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofMinutes(11))).verify(state))
                .isInstanceOf(InvalidMailStateException.class);
    }

    @Test
    void aTamperedStateIsRejected() {
        MailStateService service = serviceAt(NOW);
        String state = service.create(42L);
        String[] parts = state.split("\\.");
        // A different payload (another user id) with the old signature must not pass.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"career-copilot-mail-state\",\"purpose\":\"mail_oauth\",\"uid\":7,\"exp\":"
                        + NOW.plusSeconds(600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.verify(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> service.verify(state.substring(0, state.length() - 2) + "xx"))
                .isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> service.verify("garbage")).isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> service.verify(" ")).isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> service.verify(null)).isInstanceOf(InvalidMailStateException.class);
    }

    @Test
    void aStateSignedWithAnotherKeyIsRejected() {
        SecretKey other = new SecretKeySpec("another-secret-another-secret-another!!".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        MailStateService foreign = new MailStateService(new NimbusJwtEncoder(new ImmutableSecret<>(other)), other,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> serviceAt(NOW).verify(foreign.create(1L))).isInstanceOf(InvalidMailStateException.class);
    }

    @Test
    void aLoginTokenCannotBeUsedAsAState() {
        // Same key, but the login issuer and a subject instead of the purpose claim.
        String login = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("career-copilot").subject("42").issuedAt(NOW)
                        .expiresAt(NOW.plusSeconds(3600)).build())).getTokenValue();

        assertThatThrownBy(() -> serviceAt(NOW).verify(login)).isInstanceOf(InvalidMailStateException.class);
    }

    @Test
    void aTokenWithTheRightIssuerButNoPurposeOrUserIsRejected() {
        Instant later = NOW.plusSeconds(600);

        assertThatThrownBy(() -> serviceAt(NOW).verify(token(MailStateService.ISSUER, null, 1L, later)))
                .isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> serviceAt(NOW).verify(token(MailStateService.ISSUER, "login", 1L, later)))
                .isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> serviceAt(NOW).verify(token(MailStateService.ISSUER, "mail_oauth", null, later)))
                .isInstanceOf(InvalidMailStateException.class);
        assertThatThrownBy(() -> serviceAt(NOW).verify(token(MailStateService.ISSUER, "mail_oauth", "abc", later)))
                .isInstanceOf(InvalidMailStateException.class);
    }
}
