package com.careercopilot.jobs.scraper;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Builds the HTTP client shared by all sources: timeouts and a generic User-Agent, no cookies or credentials. */
final class ScraperHttp {

    static final String USER_AGENT = "career-copilot-scraper";

    private ScraperHttp() {
    }

    static RestClient.Builder builder(ScraperProperties properties, String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT);
    }
}
