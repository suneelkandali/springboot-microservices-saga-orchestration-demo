package com.example.saga.order.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private Integer quantity;
    private Double price;
    private String status; // PENDING, CONFIRMED, CANCELLED

    public PurchaseOrder() {}
    public PurchaseOrder(Long productId, Integer quantity, Double price, String status) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
    }
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
}