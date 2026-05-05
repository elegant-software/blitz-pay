# Feature Specification: Safe FK Deletion Strategy

**Feature Branch**: `013-safe-fk-deletion-strategy`
**Created**: 2026-05-05
**Status**: Draft
**Input**: User description: "I think on delete cascade as strategy is not safe pattern do research about best practices in spring boot JPA based applications and change the behavoir and make it as convention in liquibase refrence md file which we have"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Developer adds a parent-child table relationship (Priority: P1)

A developer needs to create a new table that has a foreign key to a parent table. Under the current convention, they might reach for `ON DELETE CASCADE` to handle cleanup automatically. Under the new convention, they consult the reference guide, declare a `RESTRICT` foreign key constraint in Liquibase, and implement explicit deletion logic in the owning module's service layer.

**Why this priority**: This is the primary guard rail. Every new migration must follow the convention, so it must be crystal-clear before anything else changes.

**Independent Test**: A reviewer can test this independently by opening a new migration that introduces an FK and verifying it uses `RESTRICT` (or no action, which is the PostgreSQL default) rather than `CASCADE`. No application runtime required.

**Acceptance Scenarios**:

1. **Given** a developer is creating `order_items` with an FK to `order_orders`, **When** they follow the reference guide, **Then** the FK constraint uses `RESTRICT` / `NO ACTION` and no `ON DELETE CASCADE` appears in the changeset.
2. **Given** a developer attempts to add `ON DELETE CASCADE` to a new FK, **When** a code reviewer checks the migration, **Then** the reviewer can point to the reference guide's prohibition and request a safe alternative.
3. **Given** a service needs to delete a parent record that has children, **When** the developer implements the deletion, **Then** the service explicitly deletes children first (in the correct module), then deletes the parent — no silent database-level cascade fires.

---

### User Story 2 — Developer migrates existing ON DELETE CASCADE constraints to RESTRICT (Priority: P2)

Existing migrations have applied `ON DELETE CASCADE` on `merchant_branches → merchant_applications` and on `order_items` / `order_payment_attempts → order_orders`. A developer remediates these by writing new Liquibase changesets that drop and recreate the constraints without cascade, and updates the owning service layers to handle child deletion explicitly.

**Why this priority**: Changing existing FK behaviour in applied migrations requires careful new changesets and complementary service changes. It is critical to eliminate the existing violations so the codebase is consistent with the new convention.

**Independent Test**: Can be tested by running the remediation changesets against a fresh database and verifying via `pg_constraint` that no FK in the `blitzpay` schema has `confdeltype = 'a'` (cascade). Then verify the service deletes children correctly when a parent is deleted.

**Acceptance Scenarios**:

1. **Given** the applied changeset `20260424-002-branch-merchant-fk-cascade.sql` added `ON DELETE CASCADE` to `fk_merchant_branches_application`, **When** the remediation changeset is applied, **Then** that FK is recreated as `RESTRICT` and merchant branch deletion is managed by the merchant service.
2. **Given** `order_items` and `order_payment_attempts` reference `order_orders` with `ON DELETE CASCADE`, **When** the remediation changesets are applied, **Then** those FKs become `RESTRICT` and the order service explicitly deletes line items and payment attempts before deleting the order.
3. **Given** a soft-delete or logical-delete approach is chosen for any entity, **When** the developer implements it, **Then** no physical `DELETE` bypasses Spring events and JPA lifecycle hooks.

---

### User Story 3 — Developer removes CascadeType.REMOVE / CascadeType.ALL from JPA entities (Priority: P3)

`MerchantApplication` uses `cascade = [CascadeType.ALL]` on `monitoringRecord`. Under the new convention, JPA-level cascade removal is replaced with explicit service-layer deletion or documented as an intentional exception for true aggregate roots.

**Why this priority**: JPA CascadeType.REMOVE is less dangerous than DB-level cascade (JPA lifecycle hooks still fire) but still bypasses Spring application events and module boundaries. This story closes the loop at the ORM layer.

**Independent Test**: Removing `CascadeType.REMOVE` and running all unit and contract tests confirms no implicit deletions break tests. A manual test that deletes a `MerchantApplication` verifies the `MonitoringRecord` is deleted by explicit service logic, not JPA cascade.

**Acceptance Scenarios**:

1. **Given** `MerchantApplication.monitoringRecord` uses `CascadeType.ALL`, **When** the entity is updated, **Then** `CascadeType.ALL` is replaced with `CascadeType.PERSIST, CascadeType.MERGE` only (no REMOVE), and the service handles orphan cleanup.
2. **Given** no `CascadeType.REMOVE` or `CascadeType.ALL` remains in any entity, **When** all tests run, **Then** all unit and contract tests pass without modification.

---

### Edge Cases

- What happens when a parent record must be deleted but children exist across multiple modules? The owning module must publish a domain event; other modules clean up their own data in response before the parent is removed.
- How does the system handle a deletion that fails mid-way (children deleted, parent delete fails)? Wrap the entire operation in a single transaction and let it roll back. Document this requirement in the service.
- What about soft deletes vs. physical deletes? The convention must address both: soft deletes (preferred for audit-sensitive data) and physical deletes (acceptable for transient/operational records) each have explicit guidance.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Liquibase reference guide (`reference/liquibase-best-practices.md`) MUST include a dedicated section on foreign key deletion strategies that prohibits `ON DELETE CASCADE` and prescribes `RESTRICT` / `NO ACTION` as the required default.
- **FR-002**: The reference guide MUST document the approved alternative patterns for handling parent record deletion: (a) explicit application-level child deletion in the owning module's service, (b) soft delete / `deleted_at` timestamp for audit-sensitive data, (c) `ON DELETE SET NULL` for nullable optional references where orphans are acceptable.
- **FR-003**: The reference guide MUST explain WHY `ON DELETE CASCADE` is prohibited — specifically that it silently bypasses JPA lifecycle hooks, Spring application events, and Spring Modulith module boundaries.
- **FR-004**: New Liquibase changesets MUST be written to remediate the two existing FK violations: `fk_merchant_branches_application` (currently CASCADE) and the two FKs in `order_items` and `order_payment_attempts` (currently CASCADE).
- **FR-005**: The merchant module service MUST be updated so that deleting a `MerchantApplication` explicitly deletes `merchant_branches` records in the same transaction before the application record is removed.
- **FR-006**: The order module service MUST be updated so that deleting an `order_orders` record explicitly deletes `order_items` and `order_payment_attempts` records in the same transaction.
- **FR-007**: `MerchantApplication.monitoringRecord` MUST drop `CascadeType.REMOVE` from the `@OneToOne` annotation; the owning service MUST handle `MonitoringRecord` deletion explicitly.
- **FR-008**: The reference guide MUST specify that `ON DELETE CASCADE` is only acceptable for Liquibase-managed audit/log tables where the child row has no business meaning without the parent (e.g., `push_delivery_attempt`) and only with explicit justification comment in the changeset.
- **FR-009**: All existing and future contract tests and unit tests MUST continue to pass after the remediation changesets and service changes are applied.

### Key Entities

- **Foreign Key Constraint**: A database-level referential integrity rule between two tables; the deletion behaviour (`RESTRICT`, `CASCADE`, `SET NULL`, `NO ACTION`) is the key concern.
- **Liquibase Changeset**: An immutable, versioned DDL unit. Existing applied changesets with CASCADE cannot be edited; new remediation changesets must be authored.
- **Spring Modulith Module Boundary**: Each direct sub-package of `com.elegant.software.blitzpay` owns its tables; deletion of owned records must go through the module's service layer so domain events are published.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero `ON DELETE CASCADE` clauses exist in the `blitzpay` schema FK constraints after remediation changesets are applied (verifiable via `pg_constraint` query).
- **SC-002**: Zero `CascadeType.REMOVE` or `CascadeType.ALL` annotations remain in any JPA entity, or each remaining one has an explicit documented justification.
- **SC-003**: The Liquibase reference guide includes a FK deletion strategy section with at least 3 approved patterns and a clear prohibition statement before the next feature is planned.
- **SC-004**: All existing unit and contract tests pass without modification after the changes.
- **SC-005**: A developer reading the reference guide can determine the correct FK deletion approach for a new table within 5 minutes without asking a teammate.

## Assumptions

- Physical deletion (rather than soft delete) is the primary concern for the remediation changesets; introducing soft deletes project-wide is out of scope but the convention should not preclude it.
- The `order` and `merchant` modules own their respective tables exclusively; no other module directly reads those tables in a way that would be broken by the FK change.
- The remediation will be implemented as new forward-only Liquibase changesets — the previously applied CASCADE changesets are left intact in history and new `ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ... RESTRICT` changesets are added.
- `ON DELETE SET NULL` on the nullable `merchant_branch_id` column in `order_orders` is acceptable and does not require remediation, as it is already nullable and no cascade is involved.
- Performance of explicit application-level deletion (vs. DB-level cascade) is acceptable given the expected data volumes in this project.