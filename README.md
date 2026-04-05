# Big Courier – Parcel Delivery Pricing

A clean-room implementation of the Big Courier Company's parcel delivery pricing system, written in **Java 21** with **Maven** and **JUnit 5**.

---

## Project Structure

```
parcel-delivery/
├── pom.xml
└── src/
    ├── main/java/com/bigcourier/
    │   ├── exception/
    │   │   └── DeliveryPricingException.java   # Business rule violation
    │   ├── model/
    │   │   ├── DeliveryItem.java               # Record: package + delivery type
    │   │   ├── DeliveryOrder.java              # Record: full order (1–3 items)
    │   │   ├── DeliveryType.java               # Enum: SAME_DAY | TWO_DAYS
    │   │   └── PackageType.java                # Enum: DOCUMENTS | SMALL_PARCEL | LARGE_PARCEL
    │   └── service/
    │       ├── DeliveryRates.java              # Configurable rate table
    │       └── DeliveryPricingService.java     # All pricing logic
    └── test/java/com/bigcourier/
        ├── model/
        │   └── DeliveryOrderTest.java          # Record guard clause tests
        └── service/
            └── DeliveryPricingServiceTest.java # Full business rule tests
```

---

## Rate Table

| Delivery  | Documents | Small Parcel | Large Parcel |
|-----------|-----------|--------------|--------------|
| Same Day  | £4.00     | £7.00        | £9.00        |
| Two Days  | £1.00     | £2.50        | £3.00        |

---

## Business Rules Implemented

| # | Rule |
|---|------|
| 1 | Base rates from the rate table |
| 2 | Same Day is **not available** for Highlands & Islands |
| 3 | Pickup address must **differ** from recipient address |
| 4 | Manchester ↔ Leeds routes carry a **25% surcharge** |
| 5 | Premium clients get **7.5% off**, except on Large Parcels |
| 6 | 3 Same Day items → **5% discount** (takes priority over rule 7) |
| 7 | 3 items → **2% discount** (only when rule 6 does not apply) |
| 8 | Rates are injected via `DeliveryRates`, supporting **bi-annual changes** |

---

## Running Tests

```bash
mvn test
```

---

## Design Decisions

- **`BigDecimal` throughout** — avoids floating-point rounding errors on monetary values.
- **`DeliveryRates` is injected** — decouples the pricing logic from the rate table, so rates can change (twice a year per spec) without touching `DeliveryPricingService`.
- **Records for model types** — immutable by default; `DeliveryOrder` makes a defensive copy of the item list.
- **Nested `@DisplayName` tests** — each business rule has its own `@Nested` class for clarity and independent test runs.
- **`DeliveryPricingException` vs `IllegalArgumentException`** — `IllegalArgumentException` is for programmer errors (null fields, too many items); `DeliveryPricingException` is for business rule violations detected at pricing time (Highlands restriction, same address).
