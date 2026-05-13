# Data Model: Merchant Refactor

## Merchant

- **Purpose**: The operational business entity that owns branches, offerings, products, and orders.
- **Identifiers**:
  - `id`: internal unique identifier
  - `merchantCode`: stable system-generated business identifier
- **Core Fields**:
  - `merchantName`
  - `merchantStatus` (`ACTIVE`, `INACTIVE`)
  - `businessVerticalCode`
  - `website` (optional)
  - `email` (optional)
  - `phoneNumber` (optional)
  - `addressLine1` (optional)
  - `addressLine2` (optional)
  - `city` (optional)
  - `postalCode` (optional)
  - `country` (optional)
  - `latitude` (optional)
  - `longitude` (optional)
  - `geofenceRadiusMeters` (optional)
  - `googlePlaceId` (optional)
  - place enrichment fields (optional)
- **Relationships**:
  - Owns zero or more `Branch` records
  - Enables zero or more `MerchantOfferingAssignment` records
- **Validation Rules**:
  - `merchantCode` is system-generated and immutable
  - `merchantName` is required and editable
  - contact info, address, and location are optional
  - inactive merchant blocks new customer-facing branch flows

## Branch

- **Purpose**: A merchant’s operational location and the required anchor for every order.
- **Identifiers**:
  - `id`: internal unique identifier
  - `branchCode`: stable system-generated business identifier unique within a merchant
- **Core Fields**:
  - `merchantId`
  - `branchName`
  - `branchStatus` (simple operational state, at minimum `ACTIVE` / `INACTIVE`)
  - `addressLine1`
  - `addressLine2` (optional)
  - `city`
  - `postalCode`
  - `country`
  - `latitude`
  - `longitude`
  - `geofenceRadiusMeters`
  - `googlePlaceId` (optional)
  - place enrichment fields (optional)
  - `websiteOverride` (optional)
  - `emailOverride` (optional)
  - `phoneOverride` (optional)
- **Relationships**:
  - Belongs to exactly one `Merchant`
  - Owns zero or more branch-linked `Order` records
- **Validation Rules**:
  - `branchCode` is system-generated and immutable
  - `branchName` is required and editable
  - structured address is mandatory
  - geospatial location is mandatory
  - contact overrides are optional and, when absent, merchant-level contact remains effective

## MerchantOffering

- **Purpose**: Platform-managed catalogue entry representing a customer-facing merchant offering.
- **Identifiers**:
  - `code`
- **Core Fields**:
  - `code`
  - `displayName`
  - `active`
- **Seed Values**:
  - `PRE_ORDER`
  - `WALK_IN_ORDERING`
  - `DEFERRED_PAYMENT`
  - `APPOINTMENT_BOOKING`
- **Validation Rules**:
  - catalogue values are platform-managed, not merchant-defined

## MerchantOfferingAssignment

- **Purpose**: Associates a merchant with one enabled offering from the platform catalogue.
- **Core Fields**:
  - `merchantId`
  - `offeringCode`
  - `enabledAt`
- **Relationships**:
  - Belongs to exactly one `Merchant`
  - References exactly one `MerchantOffering`
- **Validation Rules**:
  - assignments are unique per merchant and offering
  - `DEFERRED_PAYMENT` may be assigned only if `PRE_ORDER` or `WALK_IN_ORDERING` is also assigned
  - assignments are merchant-wide and never branch-scoped in this feature

## Order

- **Purpose**: A branch-owned commercial transaction with explicit order type and optional deferred-payment selection.
- **Identifiers**:
  - `id`: internal unique identifier
  - `orderId`: external payment/reference identifier
- **Core Fields**:
  - `merchantId`
  - `branchId`
  - `orderType` (`PRE_ORDER`, `WALK_IN_ORDERING`)
  - `usesDeferredPayment` (boolean)
  - `status`
  - `creatorType`
  - `createdById`
  - `currency`
  - `totalAmountMinor`
  - `itemCount`
  - `createdAt`
  - `updatedAt`
  - `paidAt` (optional)
  - `lastPaymentRequestId` (optional)
  - `lastPaymentProvider` (optional)
- **Relationships**:
  - Belongs to exactly one `Merchant`
  - Belongs to exactly one `Branch`
  - Owns one or more `OrderItem` records
- **Validation Rules**:
  - `branchId` is mandatory
  - `orderType` is mandatory
  - requested `orderType` must be enabled by merchant offering assignment
  - `usesDeferredPayment = true` is valid only when merchant has `DEFERRED_PAYMENT` enabled
  - `WALK_IN_ORDERING` requires recent and sufficiently accurate customer geolocation accepted by backend validation
  - `PRE_ORDER` remains valid regardless of customer proximity

## OrderItem

- **Purpose**: Snapshot of product information captured at order creation.
- **Core Fields**:
  - `orderIdFk`
  - `merchantProductId`
  - `merchantId`
  - `branchId`
  - `productName`
  - `productDescription`
  - `quantity`
  - `unitPriceMinor`
  - `lineTotalMinor`
- **Validation Rules**:
  - all items in one order must belong to the same merchant and selected branch context

## State Transitions

## Merchant Status

- `ACTIVE` → `INACTIVE`
- `INACTIVE` → `ACTIVE`

## Branch Status

- `ACTIVE` → `INACTIVE`
- `INACTIVE` → `ACTIVE`

## Order Status

- Retains existing order payment lifecycle from the order module
- New merchant/branch status gates apply to new customer actions only
- Existing orders are not invalidated solely because merchant or branch later becomes inactive

## Migration Mapping

## Merchant

- Existing onboarding-oriented parent records migrate into `Merchant`
- Existing onboarding statuses map as follows:
  - `ACTIVE`, `MONITORING` → `ACTIVE`
  - `DRAFT`, `SUBMITTED`, `VERIFICATION`, `SCREENING`, `RISK_REVIEW`, `DECISION_PENDING`, `SETUP`, `ACTION_REQUIRED`, `REJECTED` → `INACTIVE`

## Branch

- Existing branch rows receive generated `branchCode`
- Existing active/inactive semantics map into simplified operational `branchStatus`

## Order

- Existing orders receive explicit `orderType`
  - merchant-created orders → `WALK_IN_ORDERING`
  - shopper-created orders → `PRE_ORDER`
