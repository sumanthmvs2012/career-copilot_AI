package com.careercopilot.jobs.scraper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GreenhouseJobSource implements JobSource {

    static final String SOURCE = "greenhouse";
    static final String BASE_URL = "https://boards-api.greenhouse.io";

    private final RestClient client;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Autowired
    public GreenhouseJobSource(ScraperProperties properties) {
        this(ScraperHttp.builder(properties, BASE_URL));
    }

    /** Tests pass a builder bound to a mock server. */
    GreenhouseJobSource(RestClient.Builder builder) {
        this.client = builder.build();
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<ScrapedJob> fetch(String boardToken) {
        String body = client.get()
                .uri("/v1/boards/{token}/jobs?content=true", boardToken)
                .retrieve()
                .body(String.class);
        return parse(body);
    }

    List<ScrapedJob> parse(String json) {
        List<ScrapedJob> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        for (JsonNode node : mapper.readTree(json).path("jobs")) {
            String id = ScraperText.truncate(text(node, "id"), 200);
            String title = ScraperText.truncate(text(node, "title"), 200);
            if (id == null || title == null) {
                continue;
            }
            String location = ScraperText.truncate(text(node.path("location"), "name"), 200);
            result.add(new ScrapedJob(
                    id,
                    title,
                    ScraperText.truncate(text(node, "company_name"), 200),
                    location,
                    ScraperText.remoteTypeFromText(location),
                    ScraperText.truncate(text(node, "absolute_url"), 1000),
                    ScraperText.unescapedHtmlToText(text(node, "content")),
                    parseInstant(text(node, "first_published")),
                    ScraperText.truncate(firstDepartment(node), 200)));
        }
        return result;
    }

    private static String firstDepartment(JsonNode node) {
        for (JsonNode department : node.path("departments")) {
            String name = text(department, "name");
            if (name != null && !name.isBlank()) {
                return name.strip();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString() : null;
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
