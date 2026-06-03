package com.finpulse.explainer.service;

import com.finpulse.explainer.dto.ExplainRequest;
import com.finpulse.explainer.dto.ExplainResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExplainService {
    private static final Logger log = LoggerFactory.getLogger(ExplainService.class);
    private final MistralClient mistralClient;

    @Value("${explainer.fallback.enabled}")
    private boolean fallbackEnabled;

    public ExplainResponse generateExplanation(ExplainRequest request) {
        Instant start = Instant.now();
        String prompt = buildPrompt(request);
        String explanation = mistralClient.callMistral(prompt).block(Duration.ofSeconds(30));

        if (explanation == null || explanation.isBlank()) {
            if (fallbackEnabled) {
                explanation = buildFallbackExplanation(request);
                log.info("Used fallback explanation for filing {}", request.getFilingId());
            } else {
                explanation = "Unable to generate explanation at this time.";
            }
        }

        long elapsed = Duration.between(start, Instant.now()).toMillis();
        String riskLevel = evaluateRiskLevel(request.getNciGlobal());
        String modelUsed = explanation.contains("fallback") ? "template" : "mistral";

        return new ExplainResponse(explanation, riskLevel, modelUsed, elapsed);
    }

    private String buildPrompt(ExplainRequest req) {
        String anomaliesText = req.getAnomalies() == null || req.getAnomalies().isEmpty()
                ? "Aucun paragraphe anormal détecté."
                : req.getAnomalies().stream()
                  .limit(3)
                  .map(a -> String.format("- [%s] score=%.2f : %s", a.getSection(), a.getAnomalyScore(), truncate(a.getText(), 200)))
                  .collect(Collectors.joining("\n"));

        String elevatedSignals = req.getSignals() == null || req.getSignals().isEmpty()
                ? "Aucun signal élevé."
                : req.getSignals().entrySet().stream()
                  .filter(e -> e.getValue() != null && e.getValue() > 0.5)
                  .map(e -> String.format("- %s = %.2f", e.getKey(), e.getValue()))
                  .collect(Collectors.joining("\n"));

        String benchmarks = req.getSectorBenchmarks() == null || req.getSectorBenchmarks().isEmpty()
                ? ""
                : "Comparaisons sectorielles :\n" +
                req.getSectorBenchmarks().entrySet().stream()
                .map(e -> String.format("- %s moyenne secteur = %.2f", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                Tu es un analyste financier spécialisé dans les risques d'entreprise.
                Analyse le filing 10-K de %s (%s), secteur %s, année fiscale %d.
                
                Score NCI global = %.3f (0=risque faible, 1=risque élevé).
                
                Signaux anormaux détectés :
                %s
                
                Signaux quantitatifs élevés (>0.5) :
                %s
                
                %s
                
                Rédige une explication concise en français. Reste factuel.
                
                INSTRUCTION OBLIGATOIRE :
                Réponds UNIQUEMENT au format JSON valide, sans markdown autour, avec cette structure exacte :
                {
                  "summary": "1-2 phrases résumant le risque global et si le score NCI est justifié",
                  "key_drivers": [{"signal": "nom", "contribution": "explication"}],
                  "sector_comparison": "positionnement par rapport au secteur",
                  "recommended_actions": ["action 1", "action 2"]
                }
                """,
                req.getCompanyName(), req.getTicker(), req.getSector(), req.getFiscalYear(),
                req.getNciGlobal(),
                anomaliesText,
                elevatedSignals,
                benchmarks);
    }

    private String buildFallbackExplanation(ExplainRequest req) {
        double nci = req.getNciGlobal();
        String riskDesc = nci > 0.7 ? "élevé" : (nci > 0.4 ? "modéré" : "faible");
        String signalsStr = req.getSignals() == null ? "" :
                req.getSignals().entrySet().stream()
                .filter(e -> e.getValue() > 0.5)
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.joining(", "));
        return String.format("Pour le filing %d de %s, le score NCI est %.3f (risque %s). Signaux contributifs : %s. Analyse détaillée nécessite l'IA.",
                req.getFilingId(), req.getTicker(), nci, riskDesc, signalsStr);
    }

    private String evaluateRiskLevel(double nci) {
        if (nci >= 0.7) return "high";
        if (nci >= 0.4) return "medium";
        return "low";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}