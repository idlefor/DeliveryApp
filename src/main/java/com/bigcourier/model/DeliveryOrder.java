package com.bigcourier.model;

import com.bigcourier.exception.DeliveryPricingException;

import java.util.List;

/**
 * Represents a delivery order from one pickup address to one or more recipient addresses.
 * An order can contain up to 3 items.
 *
 * @param pickupAddress    the address items are collected from
 * @param recipientAddress the destination address
 * @param items            the list of items (1–3) in this order
 * @param isPremiumClient  whether the sender is a premium client
 */
public record DeliveryOrder(
        String pickupAddress,
        String recipientAddress,
        List<DeliveryItem> items,
        boolean isPremiumClient
) {
    private static final int MAX_ITEMS = 3;

    public DeliveryOrder {
        if (pickupAddress == null || pickupAddress.isBlank())
            throw new IllegalArgumentException("pickupAddress must not be blank");
        if (recipientAddress == null || recipientAddress.isBlank())
            throw new IllegalArgumentException("recipientAddress must not be blank");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Order must contain at least one item");
        if (items.size() > MAX_ITEMS)
            throw new IllegalArgumentException("Order cannot contain more than " + MAX_ITEMS + " items");

        // Business rule: pickup and recipient must differ (case-insensitive, trimmed)
        if (pickupAddress.trim().equalsIgnoreCase(recipientAddress.trim()))
            throw new DeliveryPricingException(
                "Pickup address must be different from the delivery address: '" + pickupAddress.trim() + "'");

        items = List.copyOf(items); // defensive copy — makes it truly immutable
    }
}
