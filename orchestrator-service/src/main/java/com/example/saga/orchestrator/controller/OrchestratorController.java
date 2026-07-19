package com.example.saga.orchestrator.controller;

import com.example.saga.orchestrator.dto.OrderRequest;
import com.example.saga.orchestrator.service.SagaOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saga")
public class OrchestratorController {

    private final SagaOrchestratorService service;

    public OrchestratorController(SagaOrchestratorService service) {
        this.service = service;
    }

    @PostMapping("/checkout")
    public ResponseEntity<String> checkout(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(service.executeSaga(request));
    }
}