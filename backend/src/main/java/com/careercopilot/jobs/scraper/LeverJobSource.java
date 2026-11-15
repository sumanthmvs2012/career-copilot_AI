package com.careercopilot.jobs.scraper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LeverJobSource implements JobSource {

    static final String SOURCE = "lever";
    static final String BASE_URL = "https://api.lever.co";

    private final RestClient client;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Autowired
    public LeverJobSource(ScraperProperties properties) {
        this(ScraperHttp.builder(properties, BASE_URL));
    }

    /** Tests pass a builder bound to a mock server. */
    LeverJobSource(RestClient.Builder builder) {
        this.client = builder.build();
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<ScrapedJob> fetch(String boardToken) {
        String body = client.get()
                .uri("/v0/postings/{token}?mode=json", boardToken)
                .retrieve()
                .body(String.class);
        return parse(body);
    }

    List<ScrapedJob> parse(String json) {
        List<ScrapedJob> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        for (JsonNode node : mapper.readTree(json)) {
            String id = ScraperText.truncate(text(node, "id"), 200);
            String title = ScraperText.truncate(text(node, "text"), 200);
            if (id == null || title == null) {
                continue;
            }
            String location = ScraperText.truncate(text(node.path("categories"), "location"), 200);
            JsonNode createdAt = node.path("createdAt");
            result.add(new ScrapedJob(
                    id,
                    title,
                    null,
                    location,
                    remoteType(text(node, "workplaceType"), location),
                    ScraperText.truncate(text(node, "hostedUrl"), 1000),
                    description(node),
                    createdAt.isNumber() ? Instant.ofEpochMilli(createdAt.asLong()) : null,
                    ScraperText.truncate(department(node.path("categories")), 200)));
        }
        return result;
    }

    private static String department(JsonNode categories) {
        String team = text(categories, "team");
        if (team != null && !team.isBlank()) {
            return team.strip();
        }
        String department = text(categories, "department");
        return department != null && !department.isBlank() ? department.strip() : null;
    }

    private static String remoteType(String workplaceType, String location) {
        if (workplaceType != null) {
            switch (workplaceType.toLowerCase(Locale.ROOT)) {
                case "remote":
                    return "remote";
                case "hybrid":
                    return "hybrid";
                case "on-site", "onsite":
                    return "onsite";
                default:
                    break;
            }
        }
        return ScraperText.remoteTypeFromText(location);
    }

    private static String description(JsonNode node) {
        StringBuilder out = new StringBuilder();
        String plain = text(node, "descriptionPlain");
        out.append(plain != null ? ScraperText.tidy(plain) : ScraperText.htmlToText(text(node, "description")));
        for (JsonNode list : node.path("lists")) {
            out.append("\n\n");
            String heading = text(list, "text");
            if (heading != null) {
                out.append(heading.strip()).append('\n');
            }
            out.append(ScraperText.htmlToText(text(list, "content")));
        }
        String additional = text(node, "additionalPlain");
        if (additional != null) {
            out.append("\n\n").append(ScraperText.tidy(additional));
        }
        return ScraperText.tidy(out.toString());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString() : null;
    }
}
