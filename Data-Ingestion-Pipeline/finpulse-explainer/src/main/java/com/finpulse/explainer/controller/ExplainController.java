package com.finpulse.explainer.controller;

import com.finpulse.explainer.dto.ExplainRequest;
import com.finpulse.explainer.dto.ExplainResponse;
import com.finpulse.explainer.service.ExplainService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExplainController {
    private static final Logger log = LoggerFactory.getLogger(ExplainController.class);
    private final ExplainService explainService;

    @PostMapping("/explain")
    public ResponseEntity<ExplainResponse> explain(@RequestBody ExplainRequest request) {
        log.info("Received explain request for filing {}", request.getFilingId());
        ExplainResponse response = explainService.generateExplanation(request);
        return ResponseEntity.ok(response);
    }
}