package com.finpulse.explain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnomalyExplainRequest(
        String ticker,
        String sector,
        @JsonProperty("filing_period") String filingPeriod,
        List<ParagraphPayload> paragraphs,
        Map<String, Object> context
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParagraphPayload(
            String text,
            String section,
            @JsonProperty("anomaly_score") double anomalyScore,
            double mse,
            @JsonProperty("paragraph_index") int paragraphIndex
    ) {}
}
