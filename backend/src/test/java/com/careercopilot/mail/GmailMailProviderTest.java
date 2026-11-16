package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercopilot.mail.MailProvider.MailGrant;
import com.careercopilot.mail.MailProvider.MailMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GmailMailProviderTest {

    private MockRestServiceServer server;
    private GmailMailProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MailProperties properties = new MailProperties("client-id", "client-secret",
                "http://localhost:8080/mail-connections/callback", "key", "http://localhost:5173", false, "", true,
                "", 7, Duration.ofMinutes(10), Duration.ofSeconds(5), Duration.ofSeconds(20));
        provider = new GmailMailProvider(properties, builder);
    }

    @Test
    void authUrlAsksForOfflineReadOnlyAccessAndCarriesTheState() {
        String url = provider.authUrl("state.value-1");

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=client-id")
                .contains("response_type=code")
                .contains("scope=https://www.googleapis.com/auth/gmail.readonly")
                .contains("access_type=offline")
                .contains("prompt=consent")
                .contains("state=state.value-1")
                .contains("redirect_uri=http://localhost:8080/mail-connections/callback")
                .doesNotContain("client-secret");
    }

    @Test
    void exchangeCodeSendsSecretsInTheBodyAndReturnsTheRefreshToken() {
        server.expect(requestTo(GmailMailProvider.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=the-code")))
                .andRespond(withSuccess("{\"refresh_token\":\"r-1\",\"access_token\":\"a-1\","
                        + "\"scope\":\"https://www.googleapis.com/auth/gmail.readonly\"}", MediaType.APPLICATION_JSON));

        MailGrant grant = provider.exchangeCode("the-code");

        assertThat(grant.refreshToken()).isEqualTo("r-1");
        assertThat(grant.scope()).endsWith("gmail.readonly");
        assertThat(grant.toString()).doesNotContain("r-1");
        server.verify();
    }

    @Test
    void exchangeWithoutARefreshTokenOrOnServerErrorFailsWithAFixedMessage() {
        server.expect(requestTo(GmailMailProvider.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"a-1\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.exchangeCode("c")).isInstanceOf(MailProviderException.class)
                .hasMessage("Could not complete the mailbox connection.");

        server.reset();
        server.expect(requestTo(GmailMailProvider.TOKEN_URL)).andRespond(withServerError());
        assertThatThrownBy(() -> provider.exchangeCode("secret-code")).isInstanceOf(MailProviderException.class)
                .hasMessageNotContaining("secret-code");
    }

    @Test
    void listsMetadataOnlyUsingTheAccessTokenFromTheRefreshToken() {
        server.expect(requestTo(GmailMailProvider.TOKEN_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\"access-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GmailMailProvider.GMAIL_URL + "?q=after%3A1793491200")))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GmailMailProvider.GMAIL_URL + "/m1?format=metadata")))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andRespond(withSuccess("{\"id\":\"m1\",\"internalDate\":\"1793000000000\",\"snippet\":\"Hello there\","
                        + "\"payload\":{\"headers\":[{\"name\":\"From\",\"value\":\"Jobs <jobs@acme.com>\"},"
                        + "{\"name\":\"Subject\",\"value\":\"Your application\"}]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GmailMailProvider.GMAIL_URL + "/m2?format=metadata")))
                .andRespond(withSuccess("{\"id\":\"m2\",\"payload\":{\"headers\":[]}}", MediaType.APPLICATION_JSON));

        List<MailMessage> messages = provider.listMessagesSince("refresh-1", Instant.parse("2026-11-01T00:00:00Z"));

        // m2 has no date, so it is skipped instead of guessed.
        assertThat(messages).hasSize(1);
        MailMessage first = messages.get(0);
        assertThat(first.id()).isEqualTo("m1");
        assertThat(first.from()).isEqualTo("Jobs <jobs@acme.com>");
        assertThat(first.subject()).isEqualTo("Your application");
        assertThat(first.receivedAt()).isEqualTo(Instant.ofEpochMilli(1793000000000L));
        assertThat(first.snippet()).isEqualTo("Hello there");
        server.verify();
    }

    @Test
    void listingFailuresBecomeAFixedMessage() {
        server.expect(requestTo(GmailMailProvider.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"access-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(GmailMailProvider.GMAIL_URL)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.listMessagesSince("refresh-1", Instant.now()))
                .isInstanceOf(MailProviderException.class)
                .hasMessage("Could not read the mailbox.")
                .hasMessageNotContaining("access-1").hasMessageNotContaining("refresh-1");
    }

    @Test
    void revokePostsTheTokenInTheBody() {
        server.expect(requestTo(GmailMailProvider.REVOKE_URL))
                .andExpect(content().string("token=refresh-1"))
                .andRespond(withSuccess());

        provider.revoke("refresh-1");

        server.verify();
    }
}
