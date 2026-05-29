package com.finpulse.explainer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExplainResponse {
    private String explanation;
    private String riskLevel;
    private String modelUsed;
    private Long processingTimeMs;
}