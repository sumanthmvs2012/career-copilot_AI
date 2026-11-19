package com.careercopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VoyageEmbeddingClientTest {

    private MockRestServiceServer server;
    private VoyageEmbeddingClient client;

    private static String reply(int dimensions) {
        String values = String.join(",", Collections.nCopies(dimensions, "0.5"));
        return "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[" + values
                + "],\"index\":0}],\"model\":\"voyage-3.5-lite\",\"usage\":{\"total_tokens\":3}}";
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VoyageEmbeddingClient(AiTestSupport.properties(), builder, Duration.ZERO);
    }

    @Test
    void sendsTheDocumentRequestWithABearerKey() {
        server.expect(requestTo(VoyageEmbeddingClient.URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + AiTestSupport.VOYAGE_KEY))
                .andExpect(jsonPath("$.model").value("voyage-3.5-lite"))
                .andExpect(jsonPath("$.input_type").value("document"))
                .andExpect(jsonPath("$.output_dimension").value(1024))
                .andExpect(jsonPath("$.input[0]").value("resume words"))
                .andRespond(withSuccess(reply(1024), MediaType.APPLICATION_JSON));

        float[] vector = client.embed("resume words", EmbeddingInputType.DOCUMENT);

        assertThat(vector).hasSize(1024);
        assertThat(vector[0]).isEqualTo(0.5f);
        server.verify();
    }

    @Test
    void queriesUseTheQueryInputType() {
        server.expect(requestTo(VoyageEmbeddingClient.URL))
                .andExpect(jsonPath("$.input_type").value("query"))
                .andRespond(withSuccess(reply(1024), MediaType.APPLICATION_JSON));

        client.embed("q", EmbeddingInputType.QUERY);
        server.verify();
    }

    @Test
    void truncatesLongInputToTheConfiguredLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer small = MockRestServiceServer.bindTo(builder).build();
        VoyageEmbeddingClient limited = new VoyageEmbeddingClient(AiTestSupport.properties(4), builder, Duration.ZERO);
        small.expect(requestTo(VoyageEmbeddingClient.URL))
                .andExpect(jsonPath("$.input[0]").value("abcd"))
                .andRespond(withSuccess(reply(1024), MediaType.APPLICATION_JSON));

        limited.embed("abcdefgh", EmbeddingInputType.DOCUMENT);
        small.verify();
    }

    @Test
    void retriesOnceOnRateLimitThenSucceeds() {
        server.expect(requestTo(VoyageEmbeddingClient.URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(VoyageEmbeddingClient.URL))
                .andRespond(withSuccess(reply(1024), MediaType.APPLICATION_JSON));

        assertThat(client.embed("x", EmbeddingInputType.DOCUMENT)).hasSize(1024);
        server.verify();
    }

    @Test
    void aServerErrorTwiceFailsWithAFixedMessage() {
        server.expect(requestTo(VoyageEmbeddingClient.URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(VoyageEmbeddingClient.URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.embed("private resume text", EmbeddingInputType.DOCUMENT))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.status()).isEqualTo(502);
                    assertThat(e.getMessage()).doesNotContain(AiTestSupport.VOYAGE_KEY).doesNotContain("private");
                });
        server.verify();
    }

    @Test
    void doesNotRetryAnAuthenticationError() {
        server.expect(requestTo(VoyageEmbeddingClient.URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.embed("x", EmbeddingInputType.DOCUMENT))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.status()).isEqualTo(401));
        server.verify();
    }

    @Test
    void aWrongDimensionIsRejected() {
        server.expect(requestTo(VoyageEmbeddingClient.URL))
                .andRespond(withSuccess(reply(3), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("x", EmbeddingInputType.DOCUMENT))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("The AI provider returned an unexpected response.");
    }

    @Test
    void aBodyThatIsNotJsonIsRejected() {
        server.expect(requestTo(VoyageEmbeddingClient.URL)).andRespond(withSuccess("oops", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.embed("x", EmbeddingInputType.DOCUMENT))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void toStringNeverShowsTheKey() {
        assertThat(client.toString()).doesNotContain(AiTestSupport.VOYAGE_KEY);
    }
}
