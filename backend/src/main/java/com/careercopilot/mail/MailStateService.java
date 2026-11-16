package com.careercopilot.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

/**
 * The OAuth "state" is a short-lived signed JWT that carries the user id. It uses a different issuer and a
 * purpose claim, so it can never be accepted as a login token (the login decoder requires the other issuer)
 * and a login token can never be accepted as a state.
 */
@Component
public class MailStateService {

    static final String ISSUER = "career-copilot-mail-state";
    static final String PURPOSE = "mail_oauth";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final MailProperties properties;
    private final Clock clock;

    public MailStateService(JwtEncoder encoder, SecretKey jwtSecretKey, MailProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        this.decoder = nimbus;
    }

    public String create(Long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(properties.stateTtl()))
                .claim("purpose", PURPOSE)
                .claim("uid", userId)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Returns the user id, or throws InvalidMailStateException for anything that is not a fresh, valid state. */
    public Long verify(String state) {
        if (state == null || state.isBlank()) {
            throw new InvalidMailStateException();
        }
        try {
            Jwt jwt = decoder.decode(state);
            Object uid = jwt.getClaim("uid");
            if (!PURPOSE.equals(jwt.getClaimAsString("purpose")) || !(uid instanceof Number number)) {
                throw new InvalidMailStateException();
            }
            return number.longValue();
        } catch (JwtException e) {
            throw new InvalidMailStateException();
        }
    }
}
