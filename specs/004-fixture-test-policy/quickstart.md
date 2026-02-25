# Quickstart: Fixture-Based Testing Policy

## Goal

Apply the repository fixture standard to reusable business test data, starting with invoice-related tests.

## Steps

1. Identify tests that repeat the same business sample data or hard-coded text.
2. Move the canonical shared scenario into `src/test/resources/testdata/` using the repository fixture naming pattern.
3. Add or revise typed support helpers in `src/test/kotlin/com/elegant/software/blitzpay/support/` so tests can consume the fixture without duplicating parsing logic.
4. Keep assertions readable by storing reusable expectations with the canonical scenario and keeping one-off literals inline only when policy allows.
5. Update affected tests under `src/test/kotlin/com/elegant/software/blitzpay/payments/invoice/` and any adjacent test packages that reuse the same scenario.
6. Add repository policy text to `CONSTITUATION.md` so contributors and reviewers know when shared fixtures are required and when exceptions are acceptable.

## Validation Example

1. Load the canonical invoice scenario from `src/test/resources/testdata/invoice/canonical-invoice.json`.
2. Reuse it in both `InvoiceControllerTest` and `ZugferdInvoiceServiceTest`.
3. Express optional sections such as bank account details, footer text, logo, and single-line-item cases through loader-backed variants instead of duplicating the whole fixture file.

## Verification

Run:

```bash
./gradlew test
```

Review outcomes:

- Reusable business scenario data is no longer repeated inline across the revised tests
- Fixture location and naming are obvious from the repository structure
- Small edge-case variants remain readable and do not duplicate the entire canonical fixture
- `CONSTITUATION.md` explains the fixture policy and reviewer expectations
