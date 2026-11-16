package com.careercopilot.mail;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.mail.MailProvider.MailGrant;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Connect, disconnect and scan the caller's mailbox. Tokens are encrypted at rest and never logged. */
@Service
public class MailConnectionService {

    private static final Logger log = LoggerFactory.getLogger(MailConnectionService.class);

    private final MailProperties properties;
    private final MailProvider provider;
    private final MailConnectionRepository connections;
    private final MailStateService states;
    private final TokenCipher cipher;
    private final UserRepository users;
    private final MailSyncService syncService;
    private final Clock clock;

    public MailConnectionService(MailProperties properties, MailProvider provider,
            MailConnectionRepository connections, MailStateService states, TokenCipher cipher,
            UserRepository users, MailSyncService syncService, Clock clock) {
        this.properties = properties;
        this.provider = provider;
        this.connections = connections;
        this.states = states;
        this.cipher = cipher;
        this.users = users;
        this.syncService = syncService;
        this.clock = clock;
    }

    public MailStatusResponse status(Long userId) {
        MailConnection active = connections.findFirstByUserIdAndRevokedAtIsNull(userId).orElse(null);
        return new MailStatusResponse(properties.configured(),
                active == null ? null : MailStatusResponse.ConnectionDto.from(active));
    }

    public AuthUrlResponse start(Long userId) {
        requireConfigured();
        if (connections.findFirstByUserIdAndRevokedAtIsNull(userId).isPresent()) {
            throw new MailAlreadyConnectedException();
        }
        return new AuthUrlResponse(provider.authUrl(states.create(userId)));
    }

    /**
     * Finishes the OAuth round trip. Returns true when a connection was saved; every failure (bad or expired
     * state, Google refusing the code, missing refresh token) is false, because the browser is only redirected.
     */
    public boolean complete(String code, String state, String providerError) {
        try {
            requireConfigured();
            Long userId = states.verify(state);
            if (providerError != null || code == null || code.isBlank() || !users.existsById(userId)) {
                return false;
            }
            MailGrant grant = provider.exchangeCode(code);
            if (grant == null || grant.refreshToken() == null || grant.refreshToken().isBlank()) {
                return false;
            }
            String encrypted = cipher.encrypt(grant.refreshToken());
            String scope = grant.scope() == null ? null : truncate(grant.scope(), 200);
            MailConnection connection = connections.findFirstByUserIdAndRevokedAtIsNull(userId)
                    .orElseGet(() -> new MailConnection(userId, encrypted, scope));
            connection.setEncryptedRefreshToken(encrypted);
            connection.setScope(scope);
            connections.save(connection);
            log.info("Mailbox connected for user {}", userId);
            return true;
        } catch (RuntimeException e) {
            // Class name only: messages from the provider or the cipher are not trusted to be free of secrets.
            log.warn("Mailbox connection failed ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    public void disconnect(Long userId, Long id) {
        MailConnection connection = connections.findByIdAndUserId(id, userId)
                .filter(found -> found.getRevokedAt() == null)
                .orElseThrow(MailConnectionNotFoundException::new);
        String encrypted = connection.getEncryptedRefreshToken();
        if (encrypted != null && properties.configured()) {
            try {
                provider.revoke(cipher.decrypt(encrypted));
            } catch (RuntimeException e) {
                // The user's wish to disconnect wins; the token is dropped locally either way.
                log.warn("Could not revoke the mailbox token at the provider ({})", e.getClass().getSimpleName());
            }
        }
        connection.setEncryptedRefreshToken(null);
        connection.setRevokedAt(clock.instant());
        connections.save(connection);
        log.info("Mailbox disconnected for user {}", userId);
    }

    public MailSyncService.SyncResult sync(Long userId) {
        requireConfigured();
        MailConnection connection = connections.findFirstByUserIdAndRevokedAtIsNull(userId)
                .filter(found -> found.getEncryptedRefreshToken() != null)
                .orElseThrow(MailNotConnectedException::new);
        return syncService.syncConnection(connection);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new MailNotConfiguredException();
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
