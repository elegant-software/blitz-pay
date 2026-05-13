# ADR-0002: Merchant capabilities are stored as a platform-managed catalogue

Merchant capabilities such as `PRE_ORDER`, `WALK_IN_ORDERING`, `DEFERRED_PAYMENT`, and `APPOINTMENT_BOOKING` are platform-managed values selected by a merchant, not hard-coded boolean columns on the merchant record. We chose a capability master table plus a merchant-to-capability join table because the set is controlled by the platform, applies uniformly across all branches of a merchant, and must be extensible without schema changes for each new capability.
