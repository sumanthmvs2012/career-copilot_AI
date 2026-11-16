package com.careercopilot.mail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** In-memory MailProvider for tests: no network, and it records what the service asked of it. */
public class FakeMailProvider implements MailProvider {

    public static final String REFRESH_TOKEN = "fake-refresh-token-value";

    public final List<MailMessage> inbox = new ArrayList<>();
    public final List<String> revokedTokens = new ArrayList<>();
    public final List<String> exchangedCodes = new ArrayList<>();
    public String grantedRefreshToken = REFRESH_TOKEN;
    public boolean failListing;
    public boolean failRevoke;
    public Instant lastSince;
    public String lastRefreshToken;

    public void reset() {
        inbox.clear();
        revokedTokens.clear();
        exchangedCodes.clear();
        grantedRefreshToken = REFRESH_TOKEN;
        failListing = false;
        failRevoke = false;
        lastSince = null;
        lastRefreshToken = null;
    }

    @Override
    public String authUrl(String state) {
        return "https://accounts.example.test/auth?state=" + state;
    }

    @Override
    public MailGrant exchangeCode(String code) {
        exchangedCodes.add(code);
        if ("bad-code".equals(code)) {
            throw new MailProviderException("Could not complete the mailbox connection.");
        }
        return new MailGrant(grantedRefreshToken, GmailMailProvider.SCOPE);
    }

    @Override
    public List<MailMessage> listMessagesSince(String refreshToken, Instant since) {
        lastRefreshToken = refreshToken;
        lastSince = since;
        if (failListing) {
            throw new MailProviderException("Could not read the mailbox.");
        }
        return inbox.stream().filter(m -> m.receivedAt().isAfter(since)).toList();
    }

    @Override
    public void revoke(String refreshToken) {
        if (failRevoke) {
            throw new MailProviderException("Could not revoke the mailbox access.");
        }
        revokedTokens.add(refreshToken);
    }
}
