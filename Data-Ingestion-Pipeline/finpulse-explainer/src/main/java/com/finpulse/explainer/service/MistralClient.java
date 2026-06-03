package com.finpulse.explainer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MistralClient {
    private static final Logger log = LoggerFactory.getLogger(MistralClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mistral.api.key}")
    private String apiKey;
    @Value("${mistral.api.url}")
    private String apiUrl;
    @Value("${mistral.model}")
    private String model;

    public Mono<String> callMistral(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Mistral API key missing");
            return Mono.empty();
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3
        );
        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        return root.path("choices").get(0).path("message").path("content").asText();
                    } catch (Exception e) {
                        log.error("Error parsing Mistral response", e);
                        return null;
                    }
                });
    }
}