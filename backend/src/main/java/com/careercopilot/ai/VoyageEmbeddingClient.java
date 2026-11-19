package com.careercopilot.ai;

import java.time.Duration;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Voyage AI embeddings over RestClient (1024 dimensions). Texts and vectors are never logged; failures carry fixed
 * messages. Created only when VOYAGE_API_KEY is set (see AiConfig).
 */
public class VoyageEmbeddingClient implements EmbeddingClient {

    static final String URL = "https://api.voyageai.com/v1/embeddings";

    private final AiProperties properties;
    private final RestClient client;
    private final Duration backoff;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public VoyageEmbeddingClient(AiProperties properties) {
        this(properties, AiHttp.builder(properties.timeout()), AiHttp.DEFAULT_BACKOFF);
    }

    /** Tests pass a builder bound to a mock server and no backoff. */
    VoyageEmbeddingClient(AiProperties properties, RestClient.Builder builder, Duration backoff) {
        this.properties = properties;
        this.client = builder.build();
        this.backoff = backoff;
    }

    @Override
    public float[] embed(String text, EmbeddingInputType inputType) {
        ObjectNode request = mapper.createObjectNode();
        request.putArray("input").add(AiHttp.truncate(text, properties.maxInputChars()));
        request.put("model", properties.voyageModel());
        request.put("input_type", inputType.wireValue());
        request.put("output_dimension", DIMENSIONS);

        String body = AiHttp.postJson(client, "Voyage", URL,
                headers -> headers.setBearerAuth(properties.voyageApiKey()), request.toString(), backoff);
        return parse(body);
    }

    private float[] parse(String body) {
        try {
            JsonNode values = mapper.readTree(body).path("data").path(0).path("embedding");
            if (!values.isArray() || values.size() != DIMENSIONS) {
                throw new AiProviderException("The AI provider returned an unexpected response.", 0);
            }
            float[] vector = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            return vector;
        } catch (JacksonException | NullPointerException e) {
            throw new AiProviderException("The AI provider returned an unexpected response.", 0);
        }
    }

    @Override
    public String toString() {
        return "VoyageEmbeddingClient[model=" + properties.voyageModel() + "]";
    }
}
