package com.maxlapushkin.delivery.exception;

public class InvalidDeliveryStateException extends RuntimeException {

    public InvalidDeliveryStateException(String message) {
        super(message);
    }
}
