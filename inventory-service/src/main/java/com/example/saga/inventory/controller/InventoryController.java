package com.example.saga.inventory.controller;

import com.example.saga.orchestrator.dto.InventoryRequest;
import com.example.saga.orchestrator.dto.InventoryResponse;
import com.example.saga.inventory.entity.Inventory;
import com.example.saga.inventory.repository.InventoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryRepository repository;

    public InventoryController(InventoryRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/reserve")
    @Transactional
    public ResponseEntity<InventoryResponse> reserveInventory(@RequestBody InventoryRequest request) {
        Inventory inventory = repository.findById(request.productId())
                .orElse(new Inventory(request.productId(), 0));

        if (inventory.getAvailableStock() >= request.quantity()) {
            inventory.setAvailableStock(inventory.getAvailableStock() - request.quantity());
            repository.save(inventory);
            return ResponseEntity.ok(new InventoryResponse(request.productId(), true, "Stock reserved successfully"));
        } else {
            return ResponseEntity.ok(new InventoryResponse(request.productId(), false, "Insufficient inventory stock available"));
        }
    }
}