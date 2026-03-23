# Speckit Spec: Spring Cloud Contract Stub Support

## Goal
Enable Spring Cloud Contract producer-side verification and stub generation for this project so consumers can use generated stubs with stable API contracts.

## Compatibility constraints
- Spring Boot version in repo: `4.0.2`
- Spring Modulith version in repo: `2.0.1`
- Keep existing module boundaries and WebFlux stack unchanged.

## Plan
1. Add Spring Cloud Contract Gradle plugin and dependency management BOM import to align transitive versions.
2. Add verifier + stub runner test dependencies required for contract test generation and local verification.
3. Configure contract verifier to use `WebTestClient` and a shared base class.
4. Add an initial producer contract for `POST /v1/payments/request` endpoint.
5. Run Gradle contract generation/verification tasks and fix any configuration issues.

## Tasks
- [x] Configure build with Spring Cloud Contract plugin + BOM.
- [x] Add test dependencies for verifier and stubs.
- [x] Add `ContractVerifierBase` for generated tests.
- [x] Add first contract DSL file under test resources.
- [ ] Publish stubs from CI (follow-up task: wire `verifierStubsJar` artifact publishing).

## Non-goals
- Consumer-side test setup in separate services.
- Large refactors of existing controllers/modules.
