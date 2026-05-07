package com.example.bidmart.bidding.strategy;

public record ValidationResult(boolean valid, String errorMessage) {

    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult fail(String message) {
        return new ValidationResult(false, message);
    }
}
