package com.example.saga.order.controller;

import com.example.saga.orchestrator.dto.OrderRequest;
import com.example.saga.orchestrator.dto.OrderResponse;
import com.example.saga.order.entity.PurchaseOrder;
import com.example.saga.order.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        PurchaseOrder order = repository.save(new PurchaseOrder(
                request.productId(), request.quantity(), request.price(), "PENDING"
        ));
        return ResponseEntity.ok(new OrderResponse(order.getId(), order.getProductId(), order.getQuantity(), order.getStatus()));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<Void> confirmOrder(@PathVariable Long id) {
        repository.findById(id).ifPresent(order -> {
            order.setStatus("CONFIRMED");
            repository.save(order);
        });
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        repository.findById(id).ifPresent(order -> {
            order.setStatus("CANCELLED");
            repository.save(order);
        });
        return ResponseEntity.ok().build();
    }
}