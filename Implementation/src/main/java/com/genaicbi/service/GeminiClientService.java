package com.genaicbi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.genaicbi.exception.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class GeminiClientService {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxOutputTokens;

    public GeminiClientService(
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-3-flash-preview}") String model,
            @Value("${app.gemini.temperature:0.1}") double temperature,
            @Value("${app.gemini.max-output-tokens:2048}") int maxOutputTokens
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
    }

    public String generateContent(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("GEMINI API key is missing. Set app.gemini.api-key or GEMINI_API_KEY.");
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxOutputTokens,
                        "responseMimeType", "application/json"
                )
        );

        try {
            JsonNode response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new AiProviderException("Gemini returned an empty response");
            }

            JsonNode textNode = response.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new AiProviderException("Gemini response did not contain text content");
            }
            return textNode.asText();
        } catch (RestClientException ex) {
            throw new AiProviderException("Gemini request failed: " + ex.getMessage(), ex);
        }
    }
}
