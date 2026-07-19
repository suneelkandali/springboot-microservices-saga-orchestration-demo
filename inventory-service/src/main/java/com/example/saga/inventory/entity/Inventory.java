package com.example.saga.inventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    private Long productId;
    private Integer availableStock;

    public Inventory() {}
    public Inventory(Long productId, Integer availableStock) {
        this.productId = productId;
        this.availableStock = availableStock;
    }
    public Long getProductId() { return productId; }
    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
}
