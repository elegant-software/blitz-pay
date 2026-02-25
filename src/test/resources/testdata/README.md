# Test Data Fixture Catalog

This directory contains reusable business scenarios for automated tests.

## Structure

- Store canonical scenarios under a domain directory such as `invoice/`
- Use file names that describe the business scenario, for example `canonical-invoice.json`
- Keep reusable business input and reusable expectations in the same fixture document

## Naming Rules

- Prefer `domain/scenario-name.json`
- Use one canonical fixture for the shared happy path before creating extra files
- Express small differences as loader-backed variants instead of copying the whole fixture

## Variant Rules

- Add a new fixture file only when the business scenario is materially different
- Keep one-off literals inline only when reuse would make the test harder to understand
- Name loader helper methods after the business difference they introduce

## Current Catalog

- `invoice/canonical-invoice.json`: canonical invoice scenario shared by controller and service tests
