# AGENTS.md

## Purpose
This file defines collaboration and contribution conventions for agents working in this repository.

## Project Snapshot
- Stack: Java + Spring Boot + Gradle (Kotlin DSL)
- Build tool: `./gradlew`
- Main source path: `src/`

## Local Development
- Build: `./gradlew clean build`
- Run tests: `./gradlew test`
- Run app: `./gradlew bootRun`

## Contribution Rules
- Keep changes focused and minimal for the requested task.
- Prefer small, reviewable commits.
- Do not commit secrets, generated artifacts, or environment-specific files.
- Update docs when behavior or configuration changes.
- Treat `CONSTITUATION.md` as the governing repository policy for fixture-based testing and review expectations.

## Code Quality
- Follow existing style and structure.
- Add or update tests for behavior changes.
- Avoid unrelated refactors in the same change.

## Safety
- Never run destructive git commands unless explicitly requested.
- Do not overwrite user-authored changes without confirmation.
- Confirm risky operations before proceeding.

## Active Technologies
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux Test, Spring Modulith, Jackson Kotlin module, Mockito Kotlin, JUnit 5, Testcontainers (004-fixture-test-policy)
- Test resource files under `src/test/resources/` and repository documentation in Markdown (004-fixture-test-policy)

## Recent Changes
- 004-fixture-test-policy: Added Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux Test, Spring Modulith, Jackson Kotlin module, Mockito Kotlin, JUnit 5, Testcontainers
