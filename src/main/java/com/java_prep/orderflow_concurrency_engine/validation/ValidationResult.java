package com.java_prep.orderflow_concurrency_engine.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {
    private String orderId;
    private boolean valid;
    private final List<String> errors;

    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.valid = true;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors); // Return immutable view
    }

    public int getErrorCount() {
        return errors.size();
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "orderId='" + orderId + '\'' +
                ", valid=" + valid +
                ", errors=" + errors +
                '}';
    }
}