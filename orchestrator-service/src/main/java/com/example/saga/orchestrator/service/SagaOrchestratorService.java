package com.example.saga.orchestrator.service;

import com.example.saga.orchestrator.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SagaOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorService.class);
    private final RestClient restClient = RestClient.create();

    private final SagaOrchestratorService self;

    @Autowired
    public SagaOrchestratorService(@Lazy SagaOrchestratorService self) {
        this.self = self;
    }

    @Value("${service.order}")
    private String orderServiceUrl;

    @Value("${service.inventory}")
    private String inventoryServiceUrl;

    public String executeSaga(OrderRequest orderRequest) {
        OrderResponse orderResponse = self.createOrder(orderRequest);
        if (orderResponse == null || orderResponse.orderId() == null) {
            return "Saga Failed: Could not contact Order Service.";
        }

        Long orderId = orderResponse.orderId();

        InventoryRequest invRequest = new InventoryRequest(orderId, orderRequest.productId(), orderRequest.quantity());
        InventoryResponse invResponse = self.reserveInventory(invRequest);

        if (invResponse != null && invResponse.success()) {
            if (self.confirmOrder(orderId)) {
                return "Saga Complete: Order Processed and Finalized successfully.";
            }
            self.rollbackOrder(orderId);
            return "Saga Rolled Back: Order confirmation failed. Compensating action completed.";
        }

        self.rollbackOrder(orderId);
        return "Saga Rolled Back: Inventory allocation failed -> " + (invResponse != null ? invResponse.message() : "Unknown execution error");
    }

    @CircuitBreaker(name = "orderServiceCircuitBreaker", fallbackMethod = "fallbackCreateOrder")
    public OrderResponse createOrder(OrderRequest orderRequest) {
        return restClient.post()
                .uri(orderServiceUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderRequest)
                .retrieve()
                .body(OrderResponse.class);
    }

    @CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "fallbackReserveInventory")
    public InventoryResponse reserveInventory(InventoryRequest invRequest) {
        return restClient.post()
                .uri(inventoryServiceUrl + "/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invRequest)
                .retrieve()
                .body(InventoryResponse.class);
    }

    @CircuitBreaker(name = "orderServiceCircuitBreaker", fallbackMethod = "fallbackConfirmOrder")
    public boolean confirmOrder(Long orderId) {
        restClient.put()
                .uri(orderServiceUrl + "/" + orderId + "/confirm")
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    @CircuitBreaker(name = "orderServiceCircuitBreaker", fallbackMethod = "fallbackCancelOrder")
    public void rollbackOrder(Long orderId) {
        restClient.put()
                .uri(orderServiceUrl + "/" + orderId + "/cancel")
                .retrieve()
                .toBodilessEntity();
    }

    private OrderResponse fallbackCreateOrder(OrderRequest orderRequest, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for order service: {}", throwable.getMessage());
        return null;
    }

    private InventoryResponse fallbackReserveInventory(InventoryRequest invRequest, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for inventory service: {}", throwable.getMessage());
        return null;
    }

    private boolean fallbackConfirmOrder(Long orderId, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for order confirmation (orderId={}): {}", orderId, throwable.getMessage());
        return false;
    }

    private void fallbackCancelOrder(Long orderId, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for order cancellation (orderId={}): {}", orderId, throwable.getMessage());
    }
}