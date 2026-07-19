// InventoryRequest.java
package com.example.saga.orchestrator.dto;
public record InventoryRequest(Long orderId, Long productId, Integer quantity) {}
