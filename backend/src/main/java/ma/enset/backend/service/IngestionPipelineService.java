package ma.enset.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.p1.P1CompanyIdentity;
import ma.enset.backend.p1.P1PipelineJobResponse;
import ma.enset.backend.p1.P1ScoreResponse;
import ma.enset.backend.p1.P1SignalHistoryPoint;
import ma.enset.backend.util.SentimentUtils;

/**
 * Client HTTP vers l'API Data-Ingestion-Pipeline (P1).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionPipelineService {

    private final WebClient webClient;

    public P1ScoreResponse getScore(String ticker) {
        String normalized = normalizeTicker(ticker);
        log.debug("P1 getScore {}", normalized);
        return webClient.get()
                .uri("/api/v1/score/{ticker}", normalized)
                .retrieve()
                .bodyToMono(P1ScoreResponse.class)
                .block();
    }

    public List<P1CompanyIdentity> listCompanies() {
        log.debug("P1 listCompanies");
        try {
            List<P1CompanyIdentity> list = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/companies")
                            .queryParam("limit", 1000)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<P1CompanyIdentity>>() {})
                    .block();
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.warn("P1 listCompanies failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Quick connectivity check (GET /health on P1). */
    public boolean isReachable() {
        try {
            webClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(java.time.Duration.ofSeconds(4));
            return true;
        } catch (Exception e) {
            log.debug("P1 unreachable: {}", e.getMessage());
            return false;
        }
    }

    public P1CompanyIdentity getCompanyByTicker(String ticker) {
        String normalized = normalizeTicker(ticker);
        return webClient.get()
                .uri("/api/v1/companies/{ticker}", normalized)
                .retrieve()
                .bodyToMono(P1CompanyIdentity.class)
                .block();
    }

    public List<P1SignalHistoryPoint> getSignalHistory(String ticker, int limit) {
        String normalized = normalizeTicker(ticker);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/signals/{ticker}/history")
                        .queryParam("limit", limit)
                        .build(normalized))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<P1SignalHistoryPoint>>() {})
                .block();
    }

    public String getCompanyName(String ticker) {
        try {
            P1ScoreResponse score = getScore(ticker);
            return score != null ? score.getCompanyName() : null;
        } catch (WebClientResponseException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Error fetching company name for {}", ticker, e);
            return null;
        }
    }

    public boolean companyExists(String ticker) {
        try {
            String name = getCompanyName(ticker);
            return name != null && !name.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    public Double getNciGlobal(String ticker) {
        P1ScoreResponse score = getScore(ticker);
        if (score.getCompositeRiskScore() == null) {
            throw new RuntimeException("NCI not available for " + ticker);
        }
        return score.getCompositeRiskScore();
    }

    public String getLatestEmbeddingText(String ticker, int chunkIdx) {
        String response = webClient.get()
                .uri("/api/v1/embeddings/{ticker}/latest/value/embeddings.text", normalizeTicker(ticker))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return unwrapJsonString(response);
    }

    public String getLatestEmbeddingFiledAt(String ticker) {
        String response = webClient.get()
                .uri("/api/v1/score/{ticker}/value/filings.filed_at", normalizeTicker(ticker))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return unwrapJsonString(response);
    }

    public Double getPriceClose(String ticker) {
        try {
            String response = webClient.get()
                    .uri("/api/v1/score/{ticker}/value/market_prices.price_close", normalizeTicker(ticker))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.isBlank() || "null".equalsIgnoreCase(response.trim())) {
                P1ScoreResponse score = getScore(ticker);
                return score.getMarket() != null ? score.getMarket().getClosePrice() : null;
            }
            return Double.parseDouble(response.trim());
        } catch (Exception e) {
            log.warn("Price close unavailable for {}: {}", ticker, e.getMessage());
            try {
                P1ScoreResponse score = getScore(ticker);
                return score.getMarket() != null ? score.getMarket().getClosePrice() : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public Double getSentimentScore(String ticker) {
        try {
            P1ScoreResponse score = getScore(ticker);
            Float normalized = SentimentUtils.averageFromNews(score.getRecentNews());
            return normalized != null ? normalized.doubleValue() : null;
        } catch (Exception e) {
            log.warn("Sentiment unavailable for {}", ticker);
            return null;
        }
    }

    public P1PipelineJobResponse triggerBackfillPipeline(String ticker) {
        log.info("Triggering P1 backfill for {}", ticker);
        try {
            P1PipelineJobResponse job = webClient.post()
                    .uri("/api/v1/pipelines/backfill/company")
                    .bodyValue(Map.of(
                            "identifier", normalizeTicker(ticker),
                            "run_signals", true
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.createException())
                    .bodyToMono(P1PipelineJobResponse.class)
                    .block();
            log.info("Backfill accepted for {} jobId={}", ticker, job != null ? job.getJobId() : null);
            return job;
        } catch (Exception e) {
            log.warn("Error triggering backfill for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getLlmExplanation(String ticker) {
        String normalized = normalizeTicker(ticker);
        log.debug("P1 getLlmExplanation {}", normalized);
        try {
            return webClient.get()
                    .uri("/api/v1/signals/{ticker}/llm-explanation", normalized)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (Exception e) {
            log.warn("P1 getLlmExplanation failed for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> getAnomalies(String ticker, Integer filingId, int topK) {
        String normalized = normalizeTicker(ticker);
        log.debug("P1 getAnomalies {} filingId={} topK={}", normalized, filingId, topK);
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/embeddings/{ticker}/anomalies");
                        uriBuilder.queryParam("top_k", topK);
                        if (filingId != null) {
                            uriBuilder.queryParam("filing_id", filingId);
                        }
                        return uriBuilder.build(normalized);
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            
            if (response != null && response.containsKey("paragraphs")) {
                return (List<Map<String, Object>>) response.get("paragraphs");
            }
            return List.of();
        } catch (Exception e) {
            log.warn("P1 getAnomalies failed for {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    private static String normalizeTicker(String ticker) {
        return ticker == null ? "" : ticker.trim().toUpperCase();
    }

    private static String unwrapJsonString(String response) {
        if (response == null) {
            return null;
        }
        if (response.startsWith("\"") && response.endsWith("\"")) {
            return response.substring(1, response.length() - 1);
        }
        return response;
    }
}
