package com.bigcourier.service;

import com.bigcourier.exception.DeliveryPricingException;
import com.bigcourier.model.DeliveryItem;
import com.bigcourier.model.DeliveryOrder;
import com.bigcourier.model.DeliveryType;
import com.bigcourier.model.PackageType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Calculates the total price for a {@link DeliveryOrder}.
 *
 * <h2>Business Rules Applied</h2>
 * <ol>
 *   <li>Base rates taken from {@link DeliveryRates} (supports bi-annual rate changes).</li>
 *   <li>Same Day delivery is NOT available for Highlands &amp; Islands addresses.</li>
 *   <li>The pickup address must differ from every recipient address.</li>
 *   <li>Deliveries between Manchester and Leeds carry a 25% surcharge.</li>
 *   <li>Premium clients receive a 7.5% discount, EXCEPT on Large Parcels.</li>
 *   <li>3 Same Day items → 5% discount (takes priority over rule 7).</li>
 *   <li>3 items to the same address → 2% discount, but NOT in addition to rule 6.</li>
 * </ol>
 */
public class DeliveryPricingService {

    /** Known Highlands & Islands identifiers (case-insensitive). Extend as needed. */
    private static final Set<String> HIGHLANDS_AND_ISLANDS = Set.of(
        "highlands", "islands", "highlands and islands"
    );

    /** City pair that triggers the 25% surcharge. */
    private static final Set<String> MANCHESTER_LEEDS_PAIR = Set.of("manchester", "leeds");

    private static final BigDecimal SAME_DAY_3_ITEM_DISCOUNT  = new BigDecimal("0.05");
    private static final BigDecimal THREE_SAME_ADDRESS_DISCOUNT = new BigDecimal("0.02");
    private static final BigDecimal PREMIUM_DISCOUNT          = new BigDecimal("0.075");
    private static final BigDecimal MANCHESTER_LEEDS_SURCHARGE = new BigDecimal("0.25");

    private final DeliveryRates rates;

    /** Creates the service with the default published rate table. */
    public DeliveryPricingService() {
        this(new DeliveryRates());
    }

    /**
     * Creates the service with a custom rate table.
     * Use this constructor to inject updated rates or test-specific prices.
     *
     * @param rates the rate table to use
     */
    public DeliveryPricingService(DeliveryRates rates) {
        this.rates = rates;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Calculates the total price for a delivery order, applying all applicable
     * business rules, discounts, and surcharges.
     *
     * @param order the delivery order to price
     * @return the total price rounded to 2 decimal places (half-up)
     * @throws DeliveryPricingException if any business rule is violated
     * @throws IllegalArgumentException if the order itself is malformed
     */
    public BigDecimal calculatePrice(DeliveryOrder order) {
        validateOrder(order);

        BigDecimal subtotal = calculateSubtotal(order.items());
        subtotal = applyManchesterLeedsSurcharge(subtotal, order.pickupAddress(), order.recipientAddress());
        subtotal = applyDiscounts(subtotal, order);

        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateOrder(DeliveryOrder order) {
        // pickup != recipient is enforced in DeliveryOrder constructor
        validateNoSameDayToHighlandsAndIslands(order.items(), order.recipientAddress());
    }

    /**
     * Rule: Same Day delivery is not available for Highlands and Islands.
     */
    private void validateNoSameDayToHighlandsAndIslands(List<DeliveryItem> items, String recipient) {
        boolean hasSameDay = items.stream()
            .anyMatch(i -> i.deliveryType() == DeliveryType.SAME_DAY);

        if (hasSameDay && isHighlandsAndIslands(recipient)) {
            throw new DeliveryPricingException(
                "Same Day delivery is not available for Highlands and Islands addresses");
        }
    }

    // -------------------------------------------------------------------------
    // Pricing calculations
    // -------------------------------------------------------------------------

    private BigDecimal calculateSubtotal(List<DeliveryItem> items) {
        return items.stream()
            .map(item -> rates.getRate(item.deliveryType(), item.packageType()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Rule: deliveries between Manchester and Leeds attract a 25% surcharge.
     * Applies when one end is Manchester and the other is Leeds (order-independent).
     */
    private BigDecimal applyManchesterLeedsSurcharge(BigDecimal amount, String pickup, String recipient) {
        if (isManchesterLeedsRoute(pickup, recipient)) {
            return amount.multiply(BigDecimal.ONE.add(MANCHESTER_LEEDS_SURCHARGE));
        }
        return amount;
    }

    /**
     * Applies discounts in priority order:
     * <ol>
     *   <li>3 Same Day items → 5% off (highest priority volume discount).</li>
     *   <li>3 items to same address → 2% off (only if rule above does NOT apply).</li>
     *   <li>Premium client → 7.5% off (except on Large Parcel items, applied separately).</li>
     * </ol>
     * Note: the premium discount is independent and stacks only with itself, not with volume discounts.
     * The spec states the 2% cannot be added ON TOP OF the 3-same-day 5%; this implementation
     * applies whichever single volume discount is greater and adds the premium discount independently.
     */
    private BigDecimal applyDiscounts(BigDecimal amount, DeliveryOrder order) {
        List<DeliveryItem> items = order.items();

        // --- Volume discount (mutually exclusive) ---
        BigDecimal volumeDiscount = BigDecimal.ZERO;

        boolean allSameDay = items.stream().allMatch(i -> i.deliveryType() == DeliveryType.SAME_DAY);
        boolean threeItems  = items.size() == 3;

        if (threeItems && allSameDay) {
            // Rule 6: 3 Same Day items → 5% discount (takes precedence over the 2% rule)
            volumeDiscount = SAME_DAY_3_ITEM_DISCOUNT;
        } else if (threeItems) {
            // Rule 7: 3 items → 2% discount (only when rule 6 does not apply)
            volumeDiscount = THREE_SAME_ADDRESS_DISCOUNT;
        }

        amount = amount.subtract(amount.multiply(volumeDiscount));

        // --- Premium client discount (independent of volume discounts) ---
        // Applies 7.5% off on each item that is NOT a Large Parcel.
        if (order.isPremiumClient()) {
            BigDecimal premiumDiscountAmount = calculatePremiumDiscountAmount(items);
            amount = amount.subtract(premiumDiscountAmount);
        }

        return amount;
    }

    /**
     * Premium clients get 7.5% off every item that is not a Large Parcel.
     * We calculate the discount amount per eligible item and sum them.
     */
    private BigDecimal calculatePremiumDiscountAmount(List<DeliveryItem> items) {
        return items.stream()
            .filter(item -> item.packageType() != PackageType.LARGE_PARCEL)
            .map(item -> rates.getRate(item.deliveryType(), item.packageType())
                              .multiply(PREMIUM_DISCOUNT))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isHighlandsAndIslands(String address) {
        return HIGHLANDS_AND_ISLANDS.contains(normalise(address));
    }

    private boolean isManchesterLeedsRoute(String pickup, String recipient) {
        String p = normalise(pickup);
        String r = normalise(recipient);
        return MANCHESTER_LEEDS_PAIR.contains(p) && MANCHESTER_LEEDS_PAIR.contains(r) && !p.equals(r);
    }

    private String normalise(String address) {
        return address == null ? "" : address.trim().toLowerCase();
    }
}
