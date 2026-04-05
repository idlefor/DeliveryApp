package com.bigcourier.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.bigcourier.exception.DeliveryPricingException;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DeliveryOrder} record validation.
 */
@DisplayName("DeliveryOrder")
class DeliveryOrderTest {

    private static final String ADDRESS_A = "London";
    private static final String ADDRESS_B = "Bristol";
    private static final DeliveryItem SAMPLE_ITEM =
        new DeliveryItem(PackageType.DOCUMENTS, DeliveryType.TWO_DAYS);

    @Nested
    @DisplayName("Construction guard clauses")
    class ConstructionGuards {

        @Test
        @DisplayName("creates valid order with 1 item")
        void createsValidOrderWithOneItem() {
            assertThatCode(() ->
                new DeliveryOrder(ADDRESS_A, ADDRESS_B, List.of(SAMPLE_ITEM), false)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("creates valid order with 3 items (max)")
        void createsValidOrderWithThreeItems() {
            assertThatCode(() ->
                new DeliveryOrder(ADDRESS_A, ADDRESS_B,
                    List.of(SAMPLE_ITEM, SAMPLE_ITEM, SAMPLE_ITEM), false)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws for 4 items (exceeds max)")
        void throwsForFourItems() {
            assertThatThrownBy(() ->
                new DeliveryOrder(ADDRESS_A, ADDRESS_B,
                    List.of(SAMPLE_ITEM, SAMPLE_ITEM, SAMPLE_ITEM, SAMPLE_ITEM), false)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("3");
        }

        @Test
        @DisplayName("throws for empty item list")
        void throwsForEmptyItemList() {
            assertThatThrownBy(() ->
                new DeliveryOrder(ADDRESS_A, ADDRESS_B, List.of(), false)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for null item list")
        void throwsForNullItemList() {
            assertThatThrownBy(() ->
                new DeliveryOrder(ADDRESS_A, ADDRESS_B, null, false)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for blank pickup address")
        void throwsForBlankPickupAddress() {
            assertThatThrownBy(() ->
                new DeliveryOrder("  ", ADDRESS_B, List.of(SAMPLE_ITEM), false)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for blank recipient address")
        void throwsForBlankRecipientAddress() {
            assertThatThrownBy(() ->
                new DeliveryOrder(ADDRESS_A, "", List.of(SAMPLE_ITEM), false)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("item list is immutable after construction")
        void itemListIsImmutable() {
            var mutableList = new java.util.ArrayList<>(List.of(SAMPLE_ITEM));
            var order = new DeliveryOrder(ADDRESS_A, ADDRESS_B, mutableList, false);
            mutableList.add(SAMPLE_ITEM); // mutate original list

            assertThat(order.items()).hasSize(1); // order is unaffected
        }
    }

    @Nested
    @DisplayName("DeliveryItem guard clauses")
    class DeliveryItemGuards {

        @Test
        @DisplayName("throws for null package type")
        void throwsForNullPackageType() {
            assertThatThrownBy(() ->
                new DeliveryItem(null, DeliveryType.TWO_DAYS)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for null delivery type")
        void throwsForNullDeliveryType() {
            assertThatThrownBy(() ->
                new DeliveryItem(PackageType.DOCUMENTS, null)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
