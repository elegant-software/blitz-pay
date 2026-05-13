# ADR-0001: Merchant vertical is DB-driven, not a Kotlin enum

**Date:** 2026-05-13  
**Status:** Accepted

## Context

Merchants need an industry classification (`MerchantVertical`) that will eventually drive which service capabilities are available to them (appointment booking for barber shops, table reservations for restaurants). The existing `business_type` column on `merchant_applications` held a free-form string used inconsistently for legal entity type (`"LLC"`, `"LIMITED_COMPANY"`) and industry category (`"RETAIL"`). That field was unused in any business logic and has been repurposed.

Two options were considered for representing the controlled vocabulary:

1. **Kotlin enum `MerchantVertical`** — compile-time safety, consistent with other enums in the codebase (`MerchantPaymentChannel`, `MerchantOnboardingStatus`).
2. **`merchant_verticals` table** — verticals are platform-managed rows; `business_type` stores the code as a FK reference.

## Decision

Verticals are stored in a `merchant_verticals` table (`code`, `display_name`, `active`). The `business_type` column on `merchant_applications` references `merchant_verticals.code`.

A Kotlin enum may be introduced later once the set of verticals stabilises.

## Rationale

Adding a new vertical (e.g. `PET_GROOMER`) should not require a code deployment. The platform operator must be able to introduce or retire verticals via a Liquibase changeset alone. A Kotlin enum would require a code change, build, and release for each new vertical.

## Consequences

- New verticals: add a Liquibase changeset to `merchant_verticals`, no code change required.
- Application code that needs to branch on vertical (future capability routing) must query or cache the table rather than switching on a compile-time constant.
- The legal entity type previously stored in `business_type` (`"LLC"`, `"LIMITED_COMPANY"`) is dropped — it was confirmed unused in any business logic.
