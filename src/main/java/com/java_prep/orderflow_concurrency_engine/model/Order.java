package com.java_prep.orderflow_concurrency_engine.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class Order {
    private String orderId;
    private String customerId;
    private CustomerType customerType;
    private LocalDateTime timeStamp;
    private OrderStatus orderStatus;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String shippingAddress;
    private int priority;
    private LocalDateTime expirationTime;

    public Order(String orderId, String customerId, CustomerType customerType,
                 LocalDateTime timeStamp, OrderStatus orderStatus,
                 List<OrderItem> items, BigDecimal totalAmount,
                 int priority,
                 LocalDateTime expirationTime) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerType = customerType;
        this.timeStamp = timeStamp;
        this.orderStatus = orderStatus;
        this.items = items;
        this.totalAmount = totalAmount;
        this.paymentMethod = null;
        this.shippingAddress = null;
        this.priority = priority;
        this.expirationTime = expirationTime;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public void setCustomerType(CustomerType customerType) {
        this.customerType = customerType;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public LocalDateTime getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(LocalDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", customerType=" + customerType +
                ", timeStamp=" + timeStamp +
                ", orderStatus=" + orderStatus +
                ", items=" + items +
                ", totalAmount=" + totalAmount +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", shippingAddress='" + shippingAddress + '\'' +
                ", priority=" + priority +
                ", expirationTime=" + expirationTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return priority == order.priority && Objects.equals(orderId, order.orderId) && Objects.equals(customerId, order.customerId) && customerType == order.customerType && Objects.equals(timeStamp, order.timeStamp) && orderStatus == order.orderStatus && Objects.equals(items, order.items) && Objects.equals(totalAmount, order.totalAmount) && Objects.equals(paymentMethod, order.paymentMethod) && Objects.equals(shippingAddress, order.shippingAddress) && Objects.equals(expirationTime, order.expirationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, customerId, customerType, timeStamp, orderStatus, items, totalAmount, paymentMethod, shippingAddress, priority, expirationTime);
    }
}