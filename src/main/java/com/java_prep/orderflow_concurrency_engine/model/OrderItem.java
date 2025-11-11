package com.java_prep.orderflow_concurrency_engine.model;

import java.math.BigDecimal;
import java.util.Objects;

public class OrderItem {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private String reservationId;

    public OrderItem(String productId, String productName, int quantity, BigDecimal price, String reservationId) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.reservationId = reservationId;
    }
    public OrderItem(String productId, String productName, int quantity, BigDecimal price) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.reservationId = null;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "productId='" + productId + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", reservationId='" + reservationId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return quantity == orderItem.quantity && Objects.equals(productId, orderItem.productId) && Objects.equals(productName, orderItem.productName) && Objects.equals(price, orderItem.price) && Objects.equals(reservationId, orderItem.reservationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, quantity, price, reservationId);
    }
}