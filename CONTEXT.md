# Blitz Pay — Domain Glossary

## Merchant

A business entity onboarded onto the Blitz Pay platform. Represented by a `MerchantApplication` aggregate. A merchant may have one or more **Branches**.

## Branch

A physical or logical location belonging to a Merchant (e.g. a second outlet of a restaurant chain). Branches inherit the Merchant's vertical. A Branch does **not** have its own vertical.

## MerchantVertical

The industry classification of a Merchant. Determines what service capabilities may be offered to that merchant in the future (e.g. appointment booking for `BARBER_SHOP`, table reservation for `RESTAURANT`). Verticals are platform-managed and stored in the `merchant_verticals` table — not a compile-time enum — so new verticals can be introduced without a code deployment.

The `business_type` column on `merchant_applications` stores the vertical code and references `merchant_verticals.code`.

**Seeded verticals:** `RESTAURANT`, `CAFE`, `BAR`, `BARBER_SHOP`, `BEAUTY_SALON`, `RETAIL`, `GYM`, `PHARMACY`, `ICE_CREAM_SHOP`.

> `MerchantVertical` is distinct from legal entity type (LLC, Limited Company). Legal entity classification is not modelled in Blitz Pay.

## BusinessProfile

An embeddable value object on `MerchantApplication` holding the merchant's legal business name, registration number, operating country, primary address, and `MerchantVertical` (stored as `business_type`).
