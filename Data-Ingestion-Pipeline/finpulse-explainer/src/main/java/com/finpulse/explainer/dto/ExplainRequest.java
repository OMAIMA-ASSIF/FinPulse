package com.finpulse.explainer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ExplainRequest {
    @JsonProperty("filing_id")
    private Integer filingId;
    private String ticker;
    private String companyName;
    private String sector;
    private String sicCode;
    private Integer fiscalYear;
    @JsonProperty("nci_global")
    private Double nciGlobal;
    private Map<String, Double> signals;
    private List<Anomaly> anomalies;
    private Map<String, Double> sectorBenchmarks;

    @Data
    public static class Anomaly {
        private String text;
        private Double anomalyScore;
        private String section;
    }
}