package com.careercopilot.mail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gmail over plain REST with the read-only scope. Only message metadata is requested (From, Subject, date and
 * Google's snippet); bodies are never fetched. Failures become MailProviderException with a fixed message,
 * because the original exception text can carry URLs and response bodies.
 */
@Component
public class GmailMailProvider implements MailProvider {

    static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    static final String GMAIL_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    /** One scan looks at the 100 newest messages only, which keeps a scan to a bounded number of calls. */
    static final int MAX_MESSAGES = 100;

    private final MailProperties properties;
    private final RestClient client;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Autowired
    public GmailMailProvider(MailProperties properties) {
        this(properties, timeouts(properties));
    }

    /** Tests pass a builder bound to a mock server. */
    GmailMailProvider(MailProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    private static RestClient.Builder timeouts(MailProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", "career-copilot-mail");
    }

    @Override
    public String authUrl(String state) {
        // offline + consent make Google return a refresh token every time the user connects.
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", properties.googleClientId())
                .queryParam("redirect_uri", properties.googleRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public MailGrant exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.googleClientId());
        form.add("client_secret", properties.googleClientSecret());
        form.add("redirect_uri", properties.googleRedirectUri());
        form.add("grant_type", "authorization_code");
        JsonNode body = postForm(TOKEN_URL, form, "Could not complete the mailbox connection.");
        String refresh = text(body, "refresh_token");
        if (refresh == null) {
            throw new MailProviderException("Could not complete the mailbox connection.");
        }
        return new MailGrant(refresh, text(body, "scope"));
    }

    @Override
    public List<MailMessage> listMessagesSince(String refreshToken, Instant since) {
        String accessToken = accessToken(refreshToken);
        try {
            String listBody = client.get()
                    .uri(GMAIL_URL + "?q={q}&maxResults={max}", "after:" + since.getEpochSecond() + " -from:me",
                            MAX_MESSAGES)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(String.class);
            List<MailMessage> messages = new ArrayList<>();
            for (JsonNode ref : parse(listBody).path("messages")) {
                String id = text(ref, "id");
                if (id == null) {
                    continue;
                }
                MailMessage message = fetchMetadata(accessToken, id);
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages;
        } catch (RestClientException e) {
            throw new MailProviderException("Could not read the mailbox.");
        }
    }

    @Override
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        postForm(REVOKE_URL, form, "Could not revoke the mailbox access.");
    }

    private MailMessage fetchMetadata(String accessToken, String id) {
        String body = client.get()
                .uri(GMAIL_URL + "/{id}?format=metadata&metadataHeaders=From&metadataHeaders=Subject", id)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve().body(String.class);
        JsonNode node = parse(body);
        String from = null;
        String subject = null;
        for (JsonNode header : node.path("payload").path("headers")) {
            String name = text(header, "name");
            if ("From".equalsIgnoreCase(name)) {
                from = text(header, "value");
            } else if ("Subject".equalsIgnoreCase(name)) {
                subject = text(header, "value");
            }
        }
        String internalDate = text(node, "internalDate");
        if (internalDate == null) {
            return null;
        }
        try {
            return new MailMessage(id, from, subject, Instant.ofEpochMilli(Long.parseLong(internalDate)),
                    text(node, "snippet"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String accessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.googleClientId());
        form.add("client_secret", properties.googleClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        String token = text(postForm(TOKEN_URL, form, "Could not read the mailbox."), "access_token");
        if (token == null) {
            throw new MailProviderException("Could not read the mailbox.");
        }
        return token;
    }

    /** Secrets travel in the form body, never in the URL. */
    private JsonNode postForm(String url, MultiValueMap<String, String> form, String failureMessage) {
        try {
            String body = client.post().uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(String.class);
            return parse(body);
        } catch (RestClientException e) {
            throw new MailProviderException(failureMessage);
        }
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (JacksonException e) {
            throw new MailProviderException("Unexpected response from the mail provider.");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString() : null;
    }
}
