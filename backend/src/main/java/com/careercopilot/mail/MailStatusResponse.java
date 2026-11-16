package com.careercopilot.mail;

import java.time.Instant;

/** connection is null when the user has no active mailbox link; revoked links are never shown. */
public record MailStatusResponse(boolean configured, ConnectionDto connection) {

    public record ConnectionDto(Long id, String provider, Instant createdAt) {

        static ConnectionDto from(MailConnection connection) {
            return new ConnectionDto(connection.getId(), connection.getProvider(), connection.getCreatedAt());
        }
    }
}
