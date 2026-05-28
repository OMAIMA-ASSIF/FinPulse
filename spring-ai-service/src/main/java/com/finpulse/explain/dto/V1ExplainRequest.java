package com.finpulse.explain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record V1ExplainRequest(
        String prompt,
        Long filingId,
        String companyName,
        Double nciScore,
        String riskLevel,
        List<Map<String, Object>> topSignals,
        List<Map<String, Object>> deviations
) {}
