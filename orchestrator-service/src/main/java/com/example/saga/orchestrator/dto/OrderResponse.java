// OrderResponse.java
package com.example.saga.orchestrator.dto;
public record OrderResponse(Long orderId, Long productId, Integer quantity, String status) {}