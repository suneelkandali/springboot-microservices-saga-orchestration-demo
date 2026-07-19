// InventoryResponse.java
package com.example.saga.orchestrator.dto;
public record InventoryResponse(Long productId, boolean success, String message) {}
