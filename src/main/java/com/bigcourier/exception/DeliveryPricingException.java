package com.bigcourier.exception;

/**
 * Thrown when a delivery order violates a business pricing rule
 * (e.g., Same Day delivery to Highlands & Islands).
 */
public class DeliveryPricingException extends RuntimeException {

    public DeliveryPricingException(String message) {
        super(message);
    }
}
