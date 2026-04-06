package com.booster.queryburst.product.domain;

public class StaleTokenException extends RuntimeException {

    public StaleTokenException(String message) {
        super(message);
    }
}
