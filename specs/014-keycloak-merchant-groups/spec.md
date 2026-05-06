# Feature Specification: Keycloak Merchant & Branch Group Sync

**Feature Branch**: `014-keycloak-merchant-groups`  
**Created**: 2026-05-06  
**Status**: Draft  
**Input**: User description: "when merchant and branch is getting created I need structure as Keycloak group like /merchants /merchant_1001 /branch_2001 /branch_2002 /merchant_1002 /branch_3001 which include branch or merchant code With group attributes: merchant_id = 1001, merchant_name = ABC Store"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Merchant Creation Triggers Keycloak Group (Priority: P1)

When a new merchant is created in BlitzPay, a corresponding Keycloak group is automatically created under the top-level `/merchants` group. The group is named `merchant_<merchant_code>` (e.g., `merchant_1001`) and carries the merchant's identity attributes.

**Why this priority**: This is the foundation. Without a merchant group, branches cannot be organised under their parent, and access control is impossible.

**Independent Test**: Create a merchant via the merchant registration flow and verify a matching Keycloak group `/merchants/merchant_1001` exists with the correct attributes — this alone delivers value by establishing the IAM identity for the merchant.

**Acceptance Scenarios**:

1. **Given** a merchant with code `1001` and name `ABC Store` is successfully created, **When** the system processes the merchant creation event, **Then** a Keycloak group `/merchants/merchant_1001` is created with attributes `merchant_id = 1001` and `merchant_name = ABC Store`.
2. **Given** a Keycloak group `/merchants/merchant_1001` already exists, **When** the system attempts to create the same merchant group, **Then** no duplicate group is created and the existing group attributes are updated to match the current merchant data.
3. **Given** Keycloak is temporarily unavailable, **When** a merchant is created, **Then** the merchant record is persisted and the Keycloak group creation is retried automatically until it succeeds.

---

### User Story 2 - Branch Creation Triggers Keycloak Sub-Group (Priority: P2)

When a new branch is created under a merchant, a corresponding Keycloak sub-group is automatically created as a child of the merchant's group. The sub-group is named `branch_<branch_code>` (e.g., `branch_2001`) and carries the branch's identity attributes.

**Why this priority**: Branch-level group membership enables fine-grained access control per branch. Merchants without branch groups cannot assign staff to specific branches.

**Independent Test**: With `/merchants/merchant_1001` already present, create a branch with code `2001` and verify `/merchants/merchant_1001/branch_2001` exists with correct attributes.

**Acceptance Scenarios**:

1. **Given** merchant `1001` has a Keycloak group `/merchants/merchant_1001`, **When** branch `2001` is created under that merchant, **Then** a Keycloak sub-group `/merchants/merchant_1001/branch_2001` is created with attributes `branch_id = 2001`, `branch_name = <branch name>`, `merchant_id = 1001`.
2. **Given** the parent merchant group does not yet exist in Keycloak, **When** a branch is created, **Then** the system ensures the parent merchant group is created first before adding the branch sub-group.
3. **Given** a Keycloak sub-group `/merchants/merchant_1001/branch_2001` already exists, **When** the branch creation is re-triggered, **Then** no duplicate is created and the sub-group attributes are updated.

---

### User Story 3 - Merchant/Branch Data Changes Sync to Keycloak (Priority: P3)

When an existing merchant or branch has its name or identifying attributes updated, the corresponding Keycloak group attributes are updated to stay in sync.

**Why this priority**: Without attribute sync, Keycloak group data becomes stale, causing incorrect access decisions and reporting.

**Independent Test**: Update the name of merchant `1001` to `ABC Store Premium` and verify the Keycloak group attribute `merchant_name` reflects the new value.

**Acceptance Scenarios**:

1. **Given** a merchant's name is changed, **When** the update is persisted, **Then** the `merchant_name` attribute on the corresponding Keycloak group is updated within a reasonable time.
2. **Given** a branch's name is changed, **When** the update is persisted, **Then** the `branch_name` attribute on the corresponding Keycloak sub-group is updated.

---

### Edge Cases

- What happens when Keycloak is unreachable at the time of merchant/branch creation?
- How does the system handle a merchant code that contains characters invalid in Keycloak group names?
- What happens if a merchant is deleted — should the Keycloak group be removed, archived, or left intact?
- What if two concurrent branch creation events race for the same parent group?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST automatically create a Keycloak group named `merchant_<merchant_code>` as a child of the top-level `/merchants` group whenever a new merchant is successfully created.
- **FR-002**: The system MUST set the following attributes on a merchant Keycloak group: `merchant_id` (merchant's unique identifier) and `merchant_name` (merchant's display name).
- **FR-003**: The system MUST automatically create a Keycloak sub-group named `branch_<branch_code>` as a child of the corresponding merchant group whenever a new branch is successfully created.
- **FR-004**: The system MUST set the following attributes on a branch Keycloak sub-group: `branch_id`, `branch_name`, and `merchant_id` (inherited parent context).
- **FR-005**: The system MUST ensure the top-level `/merchants` root group exists in Keycloak before any merchant group is created; it MUST be created if absent.
- **FR-006**: The system MUST ensure the parent merchant group exists before creating any branch sub-group, creating it if necessary.
- **FR-007**: The system MUST NOT create duplicate Keycloak groups; if a group with the same path already exists, it MUST update its attributes instead.
- **FR-008**: The system MUST update Keycloak group attributes when merchant name or branch name changes are persisted.
- **FR-009**: The system MUST retry Keycloak group operations if Keycloak is temporarily unavailable, without blocking the merchant or branch creation response.
- **FR-010**: The Keycloak group hierarchy MUST follow the exact path pattern: `/merchants/merchant_<code>/branch_<code>`.

### Key Entities

- **Merchant**: Business entity identified by a unique merchant code and name; maps 1:1 to a Keycloak group under `/merchants`.
- **Branch**: Operational location of a merchant, identified by a branch code; maps 1:1 to a Keycloak sub-group under its parent merchant group.
- **Keycloak Group**: IAM group in Keycloak carrying identity attributes for a merchant or branch; path-addressable via `/merchants/merchant_<code>` or `/merchants/merchant_<code>/branch_<code>`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Keycloak merchant group is created within 5 seconds of a successful merchant creation event under normal operating conditions.
- **SC-002**: A Keycloak branch sub-group is created within 5 seconds of a successful branch creation event under normal operating conditions.
- **SC-003**: 100% of successfully created merchants and branches have a corresponding Keycloak group with correct attributes after eventual consistency is reached.
- **SC-004**: When Keycloak is temporarily unavailable, group creation succeeds via retry without any manual intervention in 99% of cases.
- **SC-005**: No duplicate Keycloak groups exist for any merchant or branch at any point in time.

## Assumptions

- The top-level `/merchants` root group may not pre-exist in Keycloak; the system is responsible for creating it if absent.
- Merchant codes and branch codes are numeric or alphanumeric strings safe for use in Keycloak group names.
- Merchant and branch deletions are out of scope for this feature; no group removal logic is required.
- The Keycloak Admin REST API (or equivalent admin SDK) is available and the system has credentials with sufficient privileges to create/update groups.
- Group creation is triggered by domain events (e.g., merchant created, branch created) published within the existing Spring Modulith event infrastructure.
- Keycloak connectivity failures are transient; a durable retry mechanism (not a manual process) is sufficient.
- Branch codes are unique within their merchant scope; globally they may overlap across merchants (hence the hierarchical structure).
