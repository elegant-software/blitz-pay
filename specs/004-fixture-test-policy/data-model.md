# Data Model: Fixture-Based Testing Policy

## Overview

This feature introduces a reusable test-data model for automated tests and a governance model for how contributors consume that data.

## Entities

### Fixture Scenario

**Purpose**: Represents one canonical business scenario that can be reused across multiple tests.

**Fields**:
- `scenarioId`: Stable identifier used in file names, helper names, and reviewer discussion
- `description`: Short plain-language explanation of the business scenario
- `domain`: Functional area the scenario belongs to, such as invoice generation
- `inputData`: Canonical business input used by tests
- `expectations`: Canonical values and conditions used for assertions
- `tags`: Optional labels such as `happy-path`, `edge-case`, or `document-rendering`

**Validation Rules**:
- Must have a stable identifier and human-readable description
- Must contain only non-sensitive representative data
- Must be understandable without inspecting unrelated tests
- Must be reusable by more than one test or represent a named canonical scenario

### Fixture Variant

**Purpose**: Represents a targeted deviation from a shared fixture scenario for one edge case or optional condition.

**Fields**:
- `baseScenarioId`: Identifier of the canonical fixture scenario being extended
- `variantName`: Short name describing the difference
- `changedFields`: Explicit list of changed business values or optional sections
- `reason`: Why the variant exists and why a new full fixture is unnecessary

**Validation Rules**:
- Must remain smaller in scope than creating a brand-new canonical scenario
- Must clearly communicate what changed from the base scenario
- Must not duplicate all base scenario fields unless the scenario is materially different

### Fixture Catalog

**Purpose**: Represents the repository structure used to discover and organize fixture scenarios.

**Fields**:
- `domainPath`: Test-data directory for a business domain
- `scenarioFiles`: Named fixture documents available in that domain
- `loaderEntryPoints`: Typed support functions or helpers used by tests

**Validation Rules**:
- Paths and names must follow one consistent repository pattern
- Each catalog entry must map clearly from fixture resource to typed test usage

### Testing Policy Rule

**Purpose**: Represents a repository governance rule for when fixtures are required, recommended, or optional.

**Fields**:
- `ruleId`: Stable policy reference
- `statement`: Normative rule text
- `appliesWhen`: Conditions that trigger the rule
- `exceptionCriteria`: Conditions under which inline literals are acceptable
- `reviewExpectation`: What reviewers should verify

**Validation Rules**:
- Must be specific enough for contributor and reviewer decisions
- Must not conflict with repository contribution and modularity rules

## Relationships

- A **Fixture Catalog** contains multiple **Fixture Scenarios**
- A **Fixture Variant** extends exactly one **Fixture Scenario**
- A **Testing Policy Rule** governs how contributors create or consume **Fixture Scenarios** and **Fixture Variants**

## State Considerations

### Fixture Scenario Lifecycle

1. Proposed as a reusable or canonical business scenario
2. Added to the fixture catalog with representative input and expectations
3. Referenced by one or more tests
4. Revised when the canonical scenario changes
5. Deprecated or removed only when no active tests require it

### Policy Lifecycle

1. Defined in `CONSTITUATION.md`
2. Applied during implementation and code review
3. Refined when repository practices evolve
