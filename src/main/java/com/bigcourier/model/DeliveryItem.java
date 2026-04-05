package com.bigcourier.model;

/**
 * Represents a single item in a delivery order.
 *
 * @param packageType  the type of package
 * @param deliveryType the requested delivery speed
 */
public record DeliveryItem(PackageType packageType, DeliveryType deliveryType) {

    public DeliveryItem {
        if (packageType == null) throw new IllegalArgumentException("packageType must not be null");
        if (deliveryType == null) throw new IllegalArgumentException("deliveryType must not be null");
    }
}
