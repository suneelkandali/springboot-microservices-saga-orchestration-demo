package com.example.saga.orchestrator.service;

import com.example.saga.orchestrator.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SagaOrchestratorService {

    private final RestClient restClient = RestClient.create();

    @Value("${service.order}")
    private String orderServiceUrl;

    @Value("${service.inventory}")
    private String inventoryServiceUrl;

    public String executeSaga(OrderRequest orderRequest) {
        OrderResponse orderResponse;

        // Step 1: Create Order in PENDING status
        try {
            orderResponse = restClient.post()
                    .uri(orderServiceUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(orderRequest)
                    .retrieve()
                    .body(OrderResponse.class);
        } catch (Exception e) {
            return "Saga Failed: Could not contact Order Service.";
        }

        Long orderId = orderResponse.orderId();

        // Step 2: Attempt Inventory Reservation
        try {
            InventoryRequest invRequest = new InventoryRequest(orderId, orderRequest.productId(), orderRequest.quantity());
            InventoryResponse invResponse = restClient.post()
                    .uri(inventoryServiceUrl + "/reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(invRequest)
                    .retrieve()
                    .body(InventoryResponse.class);

            if (invResponse != null && invResponse.success()) {
                // Step 3a: Success path -> Confirm Order
                restClient.put()
                        .uri(orderServiceUrl + "/" + orderId + "/confirm")
                        .retrieve()
                        .toBodilessEntity();
                return "Saga Complete: Order Processed and Finalized successfully.";
            } else {
                // Step 3b: Business Failure path -> Trigger Compensating Action
                rollbackOrder(orderId);
                return "Saga Rolled Back: Inventory allocation failed -> " + (invResponse != null ? invResponse.message() : "Unknown execution error");
            }
        } catch (Exception e) {
            // Step 3c: Infrastructure Failure path -> Trigger Compensating Action
            rollbackOrder(orderId);
            return "Saga Rolled Back: Inventory service unavailable. Compensating action completed.";
        }
    }

    private void rollbackOrder(Long orderId) {
        restClient.put()
                .uri(orderServiceUrl + "/" + orderId + "/cancel")
                .retrieve()
                .toBodilessEntity();
    }
}
