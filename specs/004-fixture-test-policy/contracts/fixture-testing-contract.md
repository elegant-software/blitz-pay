# Contract: Fixture Testing Standard

## Purpose

Define the internal contract between reusable test fixtures, test support helpers, automated tests, and repository policy for the fixture-based testing standard.

## Contract Scope

This contract applies to reusable business scenario data consumed by automated tests in this repository. It does not apply to one-off primitive literals that are clearer inline and are explicitly allowed by policy.

## Resource Contract

- Reusable business scenarios MUST be stored under `src/test/resources/testdata/`
- Fixture names MUST communicate domain and scenario intent
- Fixture files MUST represent canonical scenarios, not opaque dumps of incidental data
- Fixture files MUST not include secrets, production identifiers, or environment-specific values

## Scenario Shape Contract

Each canonical fixture scenario MUST provide:

- A stable scenario identity
- Canonical business input data
- Canonical expected values or outcomes required by more than one test
- Human-readable meaning that a reviewer can understand without tracing unrelated test code

## Variant Contract

- Small differences from a canonical scenario SHOULD be expressed as variants of a shared fixture
- A new standalone fixture file SHOULD be created only when the scenario meaningfully differs from the canonical base
- Variant helpers MUST make changed fields explicit in test code or support code

## Test Usage Contract

- Tests covering the same business scenario MUST consume the shared fixture instead of re-declaring the same business text and sample values
- Web-slice tests MAY reuse the same canonical fixture while asserting protocol-specific behavior
- Service-level tests MAY reuse the same canonical fixture while asserting domain-specific rendering or transformation behavior
- Inline literals MAY remain when the data is one-off, trivial, and reuse would reduce readability

## Review Contract

The repository policy recorded in `CONSTITUATION.md` must remain consistent with this contract.

Reviewers should reject a change as non-compliant when:

- Reusable business text is duplicated across multiple tests without justification
- A new fixture file duplicates an existing scenario with only trivial differences
- Fixture naming or placement obscures domain meaning
- Sensitive or unrealistic sample data is introduced without reason

Reviewers should accept a justified exception when:

- The value is intentionally one-off and local to a single assertion
- Reuse would make the test harder to understand than a small inline literal
- The test documents a unique edge case that does not warrant a canonical shared scenario
