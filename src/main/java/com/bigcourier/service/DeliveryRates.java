package com.bigcourier.service;

import com.bigcourier.model.DeliveryType;
import com.bigcourier.model.PackageType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Holds the delivery rate table.
 *
 * <p>Rates can change twice a year. Inject a new instance of this class
 * to update pricing without modifying any logic in {@link DeliveryPricingService}.
 *
 * <pre>
 * Delivery     Documents   Small Parcel   Large Parcel
 * Same Day     £4.00       £7.00          £9.00
 * Two Days     £1.00       £2.50          £3.00
 * </pre>
 */
public class DeliveryRates {

    private final Map<DeliveryType, Map<PackageType, BigDecimal>> rates;

    /** Constructs the default rate table as published by Big Courier Company. */
    public DeliveryRates() {
        this(
            Map.of(
                DeliveryType.SAME_DAY, Map.of(
                    PackageType.DOCUMENTS,    new BigDecimal("4.00"),
                    PackageType.SMALL_PARCEL, new BigDecimal("7.00"),
                    PackageType.LARGE_PARCEL, new BigDecimal("9.00")
                ),
                DeliveryType.TWO_DAYS, Map.of(
                    PackageType.DOCUMENTS,    new BigDecimal("1.00"),
                    PackageType.SMALL_PARCEL, new BigDecimal("2.50"),
                    PackageType.LARGE_PARCEL, new BigDecimal("3.00")
                )
            )
        );
    }

    /**
     * Constructs a custom rate table — useful for testing and future rate reviews.
     *
     * @param rates a map of DeliveryType → (PackageType → price)
     */
    public DeliveryRates(Map<DeliveryType, Map<PackageType, BigDecimal>> rates) {
        this.rates = Map.copyOf(rates);
    }

    /**
     * Returns the base price for a given delivery and package combination.
     *
     * @param deliveryType the delivery speed
     * @param packageType  the package type
     * @return the base price
     * @throws IllegalArgumentException if the combination is not found
     */
    public BigDecimal getRate(DeliveryType deliveryType, PackageType packageType) {
        var packageRates = rates.get(deliveryType);
        if (packageRates == null) {
            throw new IllegalArgumentException("No rates defined for delivery type: " + deliveryType);
        }
        var rate = packageRates.get(packageType);
        if (rate == null) {
            throw new IllegalArgumentException(
                "No rate defined for: " + deliveryType + " / " + packageType);
        }
        return rate;
    }
}
