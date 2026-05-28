package com.finpulse.explain;

import com.finpulse.explain.dto.AnomalyExplainRequest;
import com.finpulse.explain.dto.V1ExplainRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExplainController {

    private final ExplainService explainService;

    public ExplainController(ExplainService explainService) {
        this.explainService = explainService;
    }

    /** Appelé par `ExplicabilityEngine._step5_call_spring_ai` (Python). */
    @PostMapping(value = "/v1/explain", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> explainV1(@RequestBody V1ExplainRequest request) {
        return explainService.explainFromPromptPayload(request);
    }

    /** Appelé par `request_explanation` / `build_explanation_request` (Python). */
    @PostMapping(value = "/explain/anomaly", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> explainAnomaly(@RequestBody AnomalyExplainRequest request) {
        return explainService.explainAnomaly(request);
    }
}
