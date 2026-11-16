package com.careercopilot.mail;

import java.time.Instant;
import java.util.List;

/** A read-only mailbox. Gmail is the first implementation; tests use a fake that never touches the network. */
public interface MailProvider {

    /** The consent page URL; state is echoed back to the callback unchanged. */
    String authUrl(String state);

    /** Trades the one-time code for a long-lived refresh token. */
    MailGrant exchangeCode(String code);

    /** Metadata of messages received after the given time, newest first. Never fetches bodies. */
    List<MailMessage> listMessagesSince(String refreshToken, Instant since);

    /** Asks the provider to invalidate the refresh token. */
    void revoke(String refreshToken);

    /** The long-lived token and the scope the user actually granted. */
    record MailGrant(String refreshToken, String scope) {

        @Override
        public String toString() {
            return "MailGrant[scope=" + scope + "]";
        }
    }

    /**
     * Message metadata. The snippet is used in memory by the classifier and is never stored or logged, so it
     * is left out of toString.
     */
    record MailMessage(String id, String from, String subject, Instant receivedAt, String snippet) {

        @Override
        public String toString() {
            return "MailMessage[id=" + id + "]";
        }
    }
}
