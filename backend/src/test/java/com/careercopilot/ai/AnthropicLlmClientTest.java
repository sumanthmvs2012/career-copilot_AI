package com.careercopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnthropicLlmClientTest {

    private static final String REPLY = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[{"type":"text","text":"Hello "},{"type":"text","text":"there"}],"stop_reason":"end_turn"}
            """;

    private MockRestServiceServer server;
    private AnthropicLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnthropicLlmClient(AiTestSupport.properties(), builder, Duration.ZERO);
    }

    @Test
    void sendsTheMessagesRequestWithKeyAndVersionHeaders() {
        server.expect(requestTo(AnthropicLlmClient.URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", AiTestSupport.LLM_KEY))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(300))
                .andExpect(jsonPath("$.system").value("Be brief."))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Say hi"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.complete("Be brief.", "Say hi", 300)).isEqualTo("Hello there");
        server.verify();
    }

    @Test
    void usesTheConfiguredMaxTokensWhenNoneIsGivenAndOmitsABlankSystemPrompt() {
        server.expect(requestTo(AnthropicLlmClient.URL))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.system").doesNotExist())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        client.complete(" ", "Say hi", 0);
        server.verify();
    }

    @Test
    void truncatesLongInputToTheConfiguredLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer small = MockRestServiceServer.bindTo(builder).build();
        AnthropicLlmClient limited = new AnthropicLlmClient(AiTestSupport.properties(5), builder, Duration.ZERO);
        small.expect(requestTo(AnthropicLlmClient.URL))
                .andExpect(jsonPath("$.messages[0].content").value("abcde"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        limited.complete(null, "abcdefghij", 10);
        small.verify();
    }

    @Test
    void retriesOnceOnRateLimitThenSucceeds() {
        server.expect(requestTo(AnthropicLlmClient.URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(AnthropicLlmClient.URL)).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.complete("s", "u", 10)).isEqualTo("Hello there");
        server.verify();
    }

    @Test
    void givesUpAfterOneRetryWithAFixedMessageThatHasNoKeyOrPrompt() {
        server.expect(requestTo(AnthropicLlmClient.URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("prompt echo"));
        server.expect(requestTo(AnthropicLlmClient.URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("prompt echo"));

        assertThatThrownBy(() -> client.complete("s", "my resume text", 10))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.status()).isEqualTo(503);
                    assertThat(e.getMessage()).isEqualTo("The AI provider request failed.")
                            .doesNotContain(AiTestSupport.LLM_KEY).doesNotContain("resume");
                    assertThat(e.getCause()).isNull();
                });
        server.verify();
    }

    @Test
    void doesNotRetryOtherClientErrors() {
        server.expect(requestTo(AnthropicLlmClient.URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.complete("s", "u", 10))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.status()).isEqualTo(401));
        server.verify();
    }

    @Test
    void anUnexpectedReplyBecomesAFixedMessage() {
        server.expect(requestTo(AnthropicLlmClient.URL))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("s", "u", 10))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("The AI provider returned an unexpected response.");
    }

    @Test
    void aReplyThatIsNotJsonBecomesAFixedMessage() {
        server.expect(requestTo(AnthropicLlmClient.URL)).andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.complete("s", "u", 10)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void toStringNeverShowsTheKey() {
        assertThat(client.toString()).doesNotContain(AiTestSupport.LLM_KEY).contains("claude-sonnet-5-5");
        assertThat(AiTestSupport.properties().toString())
                .doesNotContain(AiTestSupport.LLM_KEY).doesNotContain(AiTestSupport.VOYAGE_KEY)
                .contains("llmConfigured=true");
    }
}
