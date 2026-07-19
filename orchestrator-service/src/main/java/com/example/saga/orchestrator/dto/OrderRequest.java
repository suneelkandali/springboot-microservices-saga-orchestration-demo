package com.example.saga.orchestrator.dto;
public record OrderRequest(Long productId, Integer quantity, Double price) {}
