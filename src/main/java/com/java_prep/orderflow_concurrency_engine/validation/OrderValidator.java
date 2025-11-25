package com.java_prep.orderflow_concurrency_engine.validation;

import com.java_prep.orderflow_concurrency_engine.model.CustomerType;
import com.java_prep.orderflow_concurrency_engine.model.Order;
import com.java_prep.orderflow_concurrency_engine.model.OrderItem;
import com.java_prep.orderflow_concurrency_engine.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderValidator {
    private static final Logger logger= LoggerFactory.getLogger(OrderValidator.class);

    private static final BigDecimal MIN_ORDER_AMOUNT = BigDecimal.valueOf(0.01);
    private static final BigDecimal MAX_ORDER_AMOUNT = BigDecimal.valueOf(10000000.00);
    private static final int MIN_ITEM_QUANTITY = 1;
    private static final int MAX_ITEM_QUANTITY = 100;
    private static final BigDecimal MIN_ITEM_PRICE = BigDecimal.valueOf(0.01);
    private static final int MAX_ORDER_ITEMS = 50;

    public ValidationResult validate(Order order){
        ValidationResult result=new ValidationResult();
        result.setValid(true);
        if(order==null){
            result.setOrderId("Invalid/Unknown");
            result.setValid(false);
            result.addError("Order object is Null!!");
            return result;
        }
        result.setOrderId(order.getOrderId());

        validateOrderId(order, result);
        validateCustomer(order, result);
        validateTimestamp(order, result);
        validateItems(order, result);
        validateTotalAmount(order, result);
        validateStatus(order, result);

        if (result.isValid()) {
            logger.debug("Order {} passed validation", order.getOrderId());
        } else {
            logger.warn("Order {} failed validation: {}",
                    order.getOrderId(), result.getErrors());
        }
        return result;
    }

    /**
     * Validates order status is appropriate for validation stage
     */
    private void validateStatus(Order order, ValidationResult result) {
        OrderStatus status = order.getOrderStatus();
        if (status == null) {
            result.setValid(false);
            result.addError("Order status is null");
            return;
        }
        if (status != OrderStatus.PENDING) {
            result.setValid(false);
            result.addError("Order status is not PENDING: " + status + " (may have been processed already)");
        }
    }

    private void validateTotalAmount(Order order, ValidationResult result) {
        BigDecimal totalAmount = order.getTotalAmount();
        if (totalAmount.compareTo(MIN_ORDER_AMOUNT) < 0) {
            result.setValid(false);
            result.addError("Total amount too low: " + totalAmount);
        }
        if (totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            result.setValid(false);
            result.addError("Total amount too high: " + totalAmount);
        }
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            BigDecimal calculatedTotal = order.getItems().stream()
                    .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal difference = totalAmount.subtract(calculatedTotal).abs();
            if (difference.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                result.setValid(false);
                result.addError("Total amount mismatch: declared=" + totalAmount +
                        ", calculated=" + calculatedTotal + ", difference=" + difference);
            }
        }
    }

    private void validateItems(Order order, ValidationResult result) {
        List<OrderItem> items = order.getItems();
        if (items == null) {
            result.setValid(false);
            result.addError("Order items list is null");
            return;
        }
        if (items.isEmpty()) {
            result.setValid(false);
            result.addError("Order has no items");
            return;
        }
        if (items.size() > MAX_ORDER_ITEMS) {
            result.setValid(false);
            result.addError("Order exceeds maximum items limit: " +
                    items.size() + " > " + MAX_ORDER_ITEMS);
        }
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            validateSingleItem(item, i, result);
        }
    }

    private void validateSingleItem(OrderItem item, int index, ValidationResult result) {
        if (item == null) {
            result.setValid(false);
            result.addError("Item at index " + index + " is null");
            return;
        }
        if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
            result.setValid(false);
            result.addError("Item " + index + ": Product ID is null or empty");
        }
        if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
            result.setValid(false);
            result.addError("Item " + index + ": Product name is null or empty");
        }
        int quantity = item.getQuantity();
        if (quantity < MIN_ITEM_QUANTITY) {
            result.setValid(false);
            result.addError("Item " + index + ": Quantity too low (" + quantity + " < " + MIN_ITEM_QUANTITY + ")");
        }
        if (quantity > MAX_ITEM_QUANTITY) {
            result.setValid(false);
            result.addError("Item " + index + ": Quantity too high (" + quantity + " > " + MAX_ITEM_QUANTITY + ")");
        }
        BigDecimal price = item.getPrice();
        if (price.compareTo(MIN_ITEM_PRICE) < 0) {
            result.setValid(false);
            result.addError("Item " + index + ": Price too low (" + price + " < " + MIN_ITEM_PRICE + ")");
        }
    }


    private void validateTimestamp(Order order, ValidationResult result) {
        Instant timestamp = Instant.from(order.getTimeStamp());
        if (timestamp == null) {
            result.setValid(false);
            result.addError("Order timestamp is null");
            return;
        }
        Instant now = Instant.now();
        if (timestamp.isAfter(now.plusSeconds(300))) {
            result.setValid(false);
            result.addError("Order timestamp is in the future: " + timestamp);
        }
        Instant oneDayAgo = now.minusSeconds(86400);
        if (timestamp.isBefore(oneDayAgo)) {
            result.setValid(false);
            result.addError("Order timestamp is too old: " + timestamp);
        }
    }

    private void validateCustomer(Order order, ValidationResult result) {
        String customerId = order.getCustomerId();
        CustomerType customerType = order.getCustomerType();
        if (customerId == null || customerId.trim().isEmpty()) {
            result.setValid(false);
            result.addError("Customer ID is null or empty");
        }
        if (customerType == null) {
            result.setValid(false);
            result.addError("Customer type is null");
        }
        if (customerId != null && !customerId.matches("CUST_\\d{5}")) {
            result.setValid(false);
            result.addError("Customer ID format is invalid: " + customerId);
        }
    }

    private void validateOrderId(Order order, ValidationResult result) {
        String orderId = order.getOrderId();
        if (orderId == null || orderId.trim().isEmpty()) {
            result.setValid(false);
            result.addError("Order ID is null or empty");
            return;
        }
        if (!orderId.matches("ORD_\\d{8}_\\d{6}")) {
            result.setValid(false);
            result.addError("Order ID format is invalid: " + orderId);
        }
    }
}
