package com.careercopilot.ai;

import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared POST-JSON helper for the provider clients: timeouts, one retry on 429 or 5xx with a small backoff, and
 * fixed-message failures. Only the provider name, status codes and durations are logged.
 */
final class AiHttp {

    private static final Logger log = LoggerFactory.getLogger(AiHttp.class);
    static final Duration DEFAULT_BACKOFF = Duration.ofMillis(500);

    private AiHttp() {
    }

    static RestClient.Builder builder(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory);
    }

    static String postJson(RestClient client, String provider, String url, Consumer<HttpHeaders> headers,
            String json, Duration backoff) {
        long started = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                String body = client.post().uri(url)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json)
                        .retrieve().body(String.class);
                log.info("{} call succeeded in {} ms (attempt {})", provider, millisSince(started), attempt);
                return body;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                log.warn("{} call returned status {} after {} ms (attempt {})", provider, status,
                        millisSince(started), attempt);
                if (retryable && attempt == 1) {
                    pause(backoff);
                    continue;
                }
                throw new AiProviderException("The AI provider request failed.", status);
            } catch (RestClientException e) {
                // Timeouts and connection errors: the exception text can carry the URL, so only its class is logged.
                log.warn("{} call failed after {} ms: {}", provider, millisSince(started), e.getClass().getSimpleName());
                throw new AiProviderException("The AI provider could not be reached.", 0);
            }
        }
    }

    private static void pause(Duration backoff) {
        if (backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("The AI provider request was interrupted.", 0);
        }
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, Math.max(0, maxChars));
    }
}
