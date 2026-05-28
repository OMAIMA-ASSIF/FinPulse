package com.finpulse.explain;

import com.finpulse.explain.dto.AnomalyExplainRequest;
import com.finpulse.explain.dto.V1ExplainRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExplainService {

    private static final String SYSTEM_JSON =
            "Tu es un analyste financier expert. Réponds UNIQUEMENT en JSON valide.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ExplainService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> explainFromPromptPayload(V1ExplainRequest request) {
        String prompt = request.prompt() != null ? request.prompt() : "";
        return callAndParse(prompt);
    }

    public Map<String, Object> explainAnomaly(AnomalyExplainRequest request) {
        String paragraphs = request.paragraphs() == null ? "" : request.paragraphs().stream()
                .map(p -> "- [" + p.section() + "] score=" + p.anomalyScore() + " : "
                        + truncate(p.text(), 200))
                .collect(Collectors.joining("\n"));

        String prompt = """
                Tu es un analyste financier. Explique les paragraphes anormaux suivants.
                Entreprise: %s | Secteur: %s | Période: %s

                Paragraphes:
                %s

                Réponds en JSON: summary, key_drivers, sector_comparison, recommended_actions
                """.formatted(
                request.ticker(),
                request.sector(),
                request.filingPeriod(),
                paragraphs);

        return callAndParse(prompt);
    }

    private Map<String, Object> callAndParse(String userPrompt) {
        String raw = chatClient.prompt()
                .system(SYSTEM_JSON)
                .user(userPrompt)
                .call()
                .content();

        try {
            return objectMapper.readValue(stripMarkdownJson(raw), new TypeReference<>() {});
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", raw);
            fallback.put("key_drivers", java.util.List.of());
            fallback.put("sector_comparison", "");
            fallback.put("recommended_actions", java.util.List.of());
            fallback.put("parse_error", ex.getMessage());
            return fallback;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /** Retire les blocs ```json ... ``` que certains modèles ajoutent. */
    private static String stripMarkdownJson(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNewline = s.indexOf('\n');
        if (firstNewline > 0) {
            s = s.substring(firstNewline + 1);
        }
        int fence = s.lastIndexOf("```");
        if (fence >= 0) {
            s = s.substring(0, fence);
        }
        return s.trim();
    }
}
