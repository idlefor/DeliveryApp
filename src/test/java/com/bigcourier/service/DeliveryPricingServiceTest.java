package com.bigcourier.service;

import com.bigcourier.exception.DeliveryPricingException;
import com.bigcourier.model.DeliveryItem;
import com.bigcourier.model.DeliveryOrder;
import com.bigcourier.model.DeliveryType;
import com.bigcourier.model.PackageType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DeliveryPricingService}.
 *
 * <p>Test organisation follows a nested structure aligned with the business rules:
 * <ul>
 *   <li>Base rates</li>
 *   <li>Validation rules (same address, Highlands & Islands)</li>
 *   <li>Surcharges (Manchester ↔ Leeds)</li>
 *   <li>Discounts (Same Day bulk, 3-item, premium)</li>
 *   <li>Combined / integration scenarios</li>
 *   <li>Custom rate table (bi-annual rate change support)</li>
 * </ul>
 */
@DisplayName("DeliveryPricingService")
class DeliveryPricingServiceTest {

    private DeliveryPricingService service;

    // Convenient reusable addresses
    private static final String LONDON     = "London";
    private static final String BIRMINGHAM = "Birmingham";
    private static final String MANCHESTER = "Manchester";
    private static final String LEEDS      = "Leeds";
    private static final String HIGHLANDS  = "Highlands";

    @BeforeEach
    void setUp() {
        service = new DeliveryPricingService();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static DeliveryOrder order(String pickup, String recipient,
                                       boolean premium, DeliveryItem... items) {
        return new DeliveryOrder(pickup, recipient, List.of(items), premium);
    }

    private static DeliveryItem item(PackageType pkg, DeliveryType delivery) {
        return new DeliveryItem(pkg, delivery);
    }

    // =========================================================================
    // 1. Base Rates
    // =========================================================================

    @Nested
    @DisplayName("1. Base Rates")
    class BaseRates {

        @ParameterizedTest(name = "{0} + {1} = £{2}")
        @MethodSource("baseRateSource")
        @DisplayName("charges correct base rate per package and delivery type")
        void chargesCorrectBaseRate(PackageType pkg, DeliveryType delivery, String expectedPrice) {
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false, item(pkg, delivery));
            assertThat(service.calculatePrice(o))
                .isEqualByComparingTo(new BigDecimal(expectedPrice));
        }

        static Stream<Arguments> baseRateSource() {
            return Stream.of(
                Arguments.of(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY,  "4.00"),
                Arguments.of(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY,  "7.00"),
                Arguments.of(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY,  "9.00"),
                Arguments.of(PackageType.DOCUMENTS,    DeliveryType.TWO_DAYS,  "1.00"),
                Arguments.of(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS,  "2.50"),
                Arguments.of(PackageType.LARGE_PARCEL, DeliveryType.TWO_DAYS,  "3.00")
            );
        }

        @Test
        @DisplayName("sums base rates for multiple items with no discounts or surcharges")
        void sumsMultipleItemsBaseRates() {
            // £4.00 + £2.50 = £6.50
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("6.50");
        }

        @Test
        @DisplayName("result is always rounded to 2 decimal places")
        void resultRoundedToTwoDecimalPlaces() {
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
            BigDecimal price = service.calculatePrice(o);
            assertThat(price.scale()).isEqualTo(2);
        }
    }

    // =========================================================================
    // 2. Validation Rules
    // =========================================================================

    @Nested
    @DisplayName("2. Validation Rules")
    class ValidationRules {

        @Nested
        @DisplayName("2a. Pickup ≠ Recipient address")
        class PickupNotSameAsRecipient {

            @Test
            @DisplayName("throws when pickup equals recipient (exact match)")
            void throwsWhenPickupEqualsRecipient() {
                assertThatThrownBy(() ->
                    order(LONDON, LONDON, false, item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS))
                )
                .isInstanceOf(DeliveryPricingException.class)
                .hasMessageContaining("Pickup address must be different");
            }

            @Test
            @DisplayName("throws when pickup equals recipient (case-insensitive)")
            void throwsWhenPickupEqualsRecipientCaseInsensitive() {
                assertThatThrownBy(() ->
                    order("london", "LONDON", false, item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS))
                )
                .isInstanceOf(DeliveryPricingException.class);
            }

            @Test
            @DisplayName("throws when pickup equals recipient (with whitespace)")
            void throwsWhenPickupEqualsRecipientWithWhitespace() {
                assertThatThrownBy(() ->
                    order("  London  ", "London", false, item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS))
                )
                .isInstanceOf(DeliveryPricingException.class);
            }

            @Test
            @DisplayName("allows different pickup and recipient addresses")
            void allowsDifferentAddresses() {
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS));
                assertThatCode(() -> service.calculatePrice(o)).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("2b. Same Day not available for Highlands and Islands")
        class SameDayHighlandsRestriction {

            @Test
            @DisplayName("throws when Same Day item is destined for Highlands")
            void throwsForSameDayToHighlands() {
                DeliveryOrder o = order(LONDON, HIGHLANDS, false,
                    item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
                assertThatThrownBy(() -> service.calculatePrice(o))
                    .isInstanceOf(DeliveryPricingException.class)
                    .hasMessageContaining("Same Day delivery is not available for Highlands");
            }

            @Test
            @DisplayName("throws when Same Day item is destined for Islands")
            void throwsForSameDayToIslands() {
                DeliveryOrder o = order(LONDON, "Islands", false,
                    item(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY));
                assertThatThrownBy(() -> service.calculatePrice(o))
                    .isInstanceOf(DeliveryPricingException.class);
            }

            @Test
            @DisplayName("throws when any item in a mixed order is Same Day to Highlands")
            void throwsWhenAnyItemIsSameDayToHighlands() {
                DeliveryOrder o = order(LONDON, HIGHLANDS, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.TWO_DAYS),
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY)); // violates rule
                assertThatThrownBy(() -> service.calculatePrice(o))
                    .isInstanceOf(DeliveryPricingException.class);
            }

            @Test
            @DisplayName("allows Two Day delivery to Highlands")
            void allowsTwoDayToHighlands() {
                DeliveryOrder o = order(LONDON, HIGHLANDS, false,
                    item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS));
                assertThatCode(() -> service.calculatePrice(o)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("Highlands check is case-insensitive")
            void highlandsCheckIsCaseInsensitive() {
                DeliveryOrder o = order(LONDON, "HIGHLANDS", false,
                    item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
                assertThatThrownBy(() -> service.calculatePrice(o))
                    .isInstanceOf(DeliveryPricingException.class);
            }
        }

        @Nested
        @DisplayName("2c. Order construction guard clauses")
        class OrderConstructionGuards {

            @Test
            @DisplayName("throws when order has more than 3 items")
            void throwsForMoreThanThreeItems() {
                assertThatThrownBy(() ->
                    new DeliveryOrder(LONDON, BIRMINGHAM, List.of(
                        item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS),
                        item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS),
                        item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS),
                        item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS)
                    ), false)
                ).isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("throws when item list is empty")
            void throwsForEmptyItemList() {
                assertThatThrownBy(() ->
                    new DeliveryOrder(LONDON, BIRMINGHAM, List.of(), false)
                ).isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("throws when pickup address is blank")
            void throwsForBlankPickupAddress() {
                assertThatThrownBy(() ->
                    new DeliveryOrder("  ", BIRMINGHAM,
                        List.of(item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS)), false)
                ).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    // =========================================================================
    // 3. Manchester–Leeds Surcharge
    // =========================================================================

    @Nested
    @DisplayName("3. Manchester–Leeds Surcharge (25%)")
    class ManchesterLeedsSurcharge {

        @Test
        @DisplayName("applies 25% surcharge Manchester → Leeds")
        void appliesSurchargeFromManchesterToLeeds() {
            // £4.00 * 1.25 = £5.00
            DeliveryOrder o = order(MANCHESTER, LEEDS, false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("applies 25% surcharge Leeds → Manchester (route is symmetric)")
        void appliesSurchargeFromLeedsToManchester() {
            // £4.00 * 1.25 = £5.00
            DeliveryOrder o = order(LEEDS, MANCHESTER, false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("does NOT apply surcharge for unrelated routes")
        void doesNotApplySurchargeForOtherRoutes() {
            // £4.00 — no surcharge
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("4.00");
        }

        @Test
        @DisplayName("does NOT apply surcharge Manchester → Manchester (same city)")
        void doesNotApplySurchargeWithinSameCity() {
            // Validation will block same pickup=recipient, so this is caught first.
            assertThatThrownBy(() ->
                order(MANCHESTER, MANCHESTER, false, item(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS))
            ).isInstanceOf(DeliveryPricingException.class);
        }

        @Test
        @DisplayName("surcharge is case-insensitive for city names")
        void surchargeIsCaseInsensitive() {
            DeliveryOrder o = order("manchester", "LEEDS", false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("applies surcharge to multi-item order")
        void appliesSurchargeToMultiItemOrder() {
            // £4.00 + £7.00 = £11.00 * 1.25 = £13.75
            DeliveryOrder o = order(MANCHESTER, LEEDS, false,
                item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("13.75");
        }
    }

    // =========================================================================
    // 4. Discounts
    // =========================================================================

    @Nested
    @DisplayName("4. Discounts")
    class Discounts {

        @Nested
        @DisplayName("4a. 3 Same Day items → 5% discount")
        class SameDayBulkDiscount {

            @Test
            @DisplayName("applies 5% discount when all 3 items are Same Day")
            void appliesFivePercentDiscountForThreeSameDayItems() {
                // £4.00 + £7.00 + £9.00 = £20.00 * 0.95 = £19.00
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY),
                    item(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("19.00");
            }

            @Test
            @DisplayName("does NOT apply 5% discount when only 2 of 3 items are Same Day")
            void doesNotApplyForMixedDeliveryTypes() {
                // £4.00 + £7.00 + £3.00 = £14.00 (3 items → 2% = £13.72)
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY),
                    item(PackageType.LARGE_PARCEL, DeliveryType.TWO_DAYS));
                // 2% discount applies instead: £14.00 * 0.98 = £13.72
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("13.72");
            }

            @Test
            @DisplayName("does NOT apply 5% discount for only 2 Same Day items")
            void doesNotApplyForOnlyTwoItems() {
                // 2 items — neither volume discount applies
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("11.00");
            }
        }

        @Nested
        @DisplayName("4b. 3 items to same address → 2% discount")
        class ThreeItemSameAddressDiscount {

            @Test
            @DisplayName("applies 2% discount for 3 items when not all Same Day")
            void appliesTwoPercentDiscountForThreeItems() {
                // £1.00 + £2.50 + £3.00 = £6.50 * 0.98 = £6.37
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.TWO_DAYS),
                    item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS),
                    item(PackageType.LARGE_PARCEL, DeliveryType.TWO_DAYS));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("6.37");
            }

            @Test
            @DisplayName("does NOT apply 2% when 3 Same Day items exist (5% takes precedence)")
            void fivePercentTakesPrecedenceOverTwoPercent() {
                // All 3 Same Day: £4+£7+£9=£20 → 5% discount → £19.00 (NOT 2% giving £19.60)
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY),
                    item(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("19.00");
            }

            @Test
            @DisplayName("no volume discount for fewer than 3 items")
            void noVolumeDiscountForTwoItems() {
                // £4.00 + £7.00 = £11.00 — no discount
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("11.00");
            }
        }

        @Nested
        @DisplayName("4c. Premium client → 7.5% discount (excluding Large Parcels)")
        class PremiumClientDiscount {

            @Test
            @DisplayName("applies 7.5% discount on Documents for premium client")
            void appliesPremiumDiscountOnDocuments() {
                // £4.00 - (£4.00 * 0.075) = £4.00 - £0.30 = £3.70
                DeliveryOrder o = order(LONDON, BIRMINGHAM, true,
                    item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("3.70");
            }

            @Test
            @DisplayName("applies 7.5% discount on Small Parcel for premium client")
            void appliesPremiumDiscountOnSmallParcel() {
                // £7.00 - (£7.00 * 0.075) = £7.00 - £0.525 = £6.475 → £6.48
                DeliveryOrder o = order(LONDON, BIRMINGHAM, true,
                    item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("6.48");
            }

            @Test
            @DisplayName("does NOT apply premium discount on Large Parcel")
            void doesNotApplyPremiumDiscountOnLargeParcel() {
                // £9.00 — no premium discount on Large Parcel
                DeliveryOrder o = order(LONDON, BIRMINGHAM, true,
                    item(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("9.00");
            }

            @Test
            @DisplayName("applies premium discount only to eligible items in a mixed order")
            void appliesPremiumDiscountOnlyToEligibleItems() {
                // Documents £4.00 (-7.5% = £3.70) + Large Parcel £9.00 (no discount) = £12.70
                DeliveryOrder o = order(LONDON, BIRMINGHAM, true,
                    item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                    item(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("12.70");
            }

            @Test
            @DisplayName("non-premium client gets no discount")
            void nonPremiumClientGetsNoDiscount() {
                DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                    item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
                assertThat(service.calculatePrice(o)).isEqualByComparingTo("4.00");
            }
        }
    }

    // =========================================================================
    // 5. Combined / Integration Scenarios
    // =========================================================================

    @Nested
    @DisplayName("5. Combined Scenarios")
    class CombinedScenarios {

        @Test
        @DisplayName("premium + 3 Same Day items: 5% volume discount then 7.5% premium on eligible items")
        void premiumAnd3SameDayItems() {
            /*
             * Items: Documents £4, Small Parcel £7, Large Parcel £9 → subtotal £20
             * 3 Same Day → 5% discount: £20 * 0.95 = £19.00
             *
             * Premium discount on non-Large Parcel items (at original rates):
             *   Docs: £4 * 0.075 = £0.30
             *   Small: £7 * 0.075 = £0.525
             *   Total premium deduction: £0.825
             *
             * Final: £19.00 - £0.825 = £18.175 → £18.18
             */
            DeliveryOrder o = order(LONDON, BIRMINGHAM, true,
                item(PackageType.DOCUMENTS,    DeliveryType.SAME_DAY),
                item(PackageType.SMALL_PARCEL, DeliveryType.SAME_DAY),
                item(PackageType.LARGE_PARCEL, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("18.18");
        }

        @Test
        @DisplayName("Manchester–Leeds surcharge combined with premium discount")
        void manchesterLeedsWithPremiumDiscount() {
            /*
             * Documents Same Day: £4.00
             * Manchester → Leeds surcharge: £4.00 * 1.25 = £5.00
             * Premium discount: £5.00 - (£4.00 * 0.075) = £5.00 - £0.30 = £4.70
             */
            DeliveryOrder o = order(MANCHESTER, LEEDS, true,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("4.70");
        }

        @Test
        @DisplayName("Manchester–Leeds surcharge with 3-item 2% discount")
        void manchesterLeedsWithThreeItemDiscount() {
            /*
             * £1.00 + £2.50 + £3.00 = £6.50
             * M-L surcharge: £6.50 * 1.25 = £8.125
             * 3 items (Two Day) → 2% discount: £8.125 * 0.98 = £7.9625 → £7.96
             */
            DeliveryOrder o = order(MANCHESTER, LEEDS, false,
                item(PackageType.DOCUMENTS,    DeliveryType.TWO_DAYS),
                item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS),
                item(PackageType.LARGE_PARCEL, DeliveryType.TWO_DAYS));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("7.96");
        }

        @Test
        @DisplayName("single Two Day item with no extra rules produces base rate")
        void singleTwoDayItemNoExtras() {
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("2.50");
        }

        @Test
        @DisplayName("all discounts and surcharges combined: premium, 3 Two-Day items, Manchester–Leeds")
        void allRulesCombined() {
            /*
             * Small Parcel TWO_DAYS x3: £2.50 * 3 = £7.50
             * Manchester–Leeds: £7.50 * 1.25 = £9.375
             * 3 items (not all Same Day) → 2% discount: £9.375 * 0.98 = £9.1875
             * Premium on Small Parcel x3 (at base rate each): 3 * (£2.50 * 0.075) = £0.5625
             * Final: £9.1875 - £0.5625 = £8.625 → £8.63
             */
            DeliveryOrder o = order(MANCHESTER, LEEDS, true,
                item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS),
                item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS),
                item(PackageType.SMALL_PARCEL, DeliveryType.TWO_DAYS));
            assertThat(service.calculatePrice(o)).isEqualByComparingTo("8.63");
        }
    }

    // =========================================================================
    // 6. Rate Table Flexibility (supports bi-annual rate changes)
    // =========================================================================

    @Nested
    @DisplayName("6. Custom Rate Table (bi-annual rate changes)")
    class CustomRateTable {

        @Test
        @DisplayName("service uses injected rates correctly")
        void usesInjectedRates() {
            // Override Same Day Documents to £10.00
            var customRates = new DeliveryRates(
                java.util.Map.of(
                    DeliveryType.SAME_DAY, java.util.Map.of(
                        PackageType.DOCUMENTS,    new BigDecimal("10.00"),
                        PackageType.SMALL_PARCEL, new BigDecimal("7.00"),
                        PackageType.LARGE_PARCEL, new BigDecimal("9.00")
                    ),
                    DeliveryType.TWO_DAYS, java.util.Map.of(
                        PackageType.DOCUMENTS,    new BigDecimal("1.00"),
                        PackageType.SMALL_PARCEL, new BigDecimal("2.50"),
                        PackageType.LARGE_PARCEL, new BigDecimal("3.00")
                    )
                )
            );

            var customService = new DeliveryPricingService(customRates);
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));

            assertThat(customService.calculatePrice(o)).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("default service and custom service can price the same order differently")
        void defaultAndCustomDifferentPrices() {
            DeliveryOrder o = order(LONDON, BIRMINGHAM, false,
                item(PackageType.DOCUMENTS, DeliveryType.SAME_DAY));

            BigDecimal defaultPrice = service.calculatePrice(o);

            var elevated = new DeliveryRates(
                java.util.Map.of(
                    DeliveryType.SAME_DAY, java.util.Map.of(
                        PackageType.DOCUMENTS,    new BigDecimal("6.00"),
                        PackageType.SMALL_PARCEL, new BigDecimal("7.00"),
                        PackageType.LARGE_PARCEL, new BigDecimal("9.00")
                    ),
                    DeliveryType.TWO_DAYS, java.util.Map.of(
                        PackageType.DOCUMENTS,    new BigDecimal("1.00"),
                        PackageType.SMALL_PARCEL, new BigDecimal("2.50"),
                        PackageType.LARGE_PARCEL, new BigDecimal("3.00")
                    )
                )
            );
            var customService = new DeliveryPricingService(elevated);
            BigDecimal customPrice = customService.calculatePrice(o);

            assertThat(customPrice).isGreaterThan(defaultPrice);
        }
    }
}
