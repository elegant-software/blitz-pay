# CONSTITUTION.md

This file defines the governing conventions and non-negotiable rules for the blitz-pay repository. All contributors — human and AI — must follow these rules. Violations should be caught in code review or CI.

## Audience

Both human contributors and AI agents. When in doubt about a convention, this file takes precedence over informal patterns found elsewhere in the codebase.

## Language and Runtime

- **Kotlin** is the only application language. Do not introduce Java source files.
- Target **Java 25**. Do not use preview features unless explicitly agreed upon.
- Use idiomatic Kotlin: data classes, sealed types, extension functions, null safety. Avoid Java-style patterns (e.g., static utility classes, checked-exception wrappers).

## Module Boundaries

- The project follows **Spring Modulith** module-per-business-capability layout.
- Each direct sub-package of `com.elegant.software.blitzpay` is an application module.
- Internal types live in `internal` sub-packages and must never be referenced from other modules.
- Cross-module communication uses **domain events** (`ApplicationEventPublisher` / `@ApplicationModuleListener`), not direct bean injection.
- Expose cross-module contracts through `@NamedInterface` on dedicated `api` sub-packages.
- Module metadata uses `@PackageInfo`-annotated Kotlin types (not `package-info.java`).

## API Contracts

- All HTTP endpoints use URL-path versioning (`/v1/...`).
- Request and response shapes are stable once published. Breaking changes require a new version path and coordinated migration.
- Every new or changed endpoint must have a corresponding contract test in `src/contractTest/`.

## Testing Policy

- **Every behavior change must include tests.** No code is merged without covering tests.
- Unit tests (`src/test/kotlin`): JUnit 5 + Mockito Kotlin. Use `TestFixtureLoader` and JSON fixtures from `src/test/resources/testdata/` for deterministic, data-driven test scenarios.
- Contract tests (`src/contractTest/kotlin`): `WebTestClient`-based, run under the `contract-test` profile. External services (TrueLayer, database) are mocked — these tests validate HTTP contract shape, not business logic.
- Module verification: maintain `ApplicationModules.of(...).verify()` tests. Use `@ApplicationModuleTest` for single-module integration tests.
- **Tests must pass before committing.** Run `./gradlew check` (unit + contract tests) locally before pushing. Do not commit, merge, or push code with failing tests.
- Fixture files are checked into version control. Do not generate fixtures at test time; keep them static and reviewable.

## Persistence and Schema

- **Liquibase** owns all schema evolution. No schema change may go to staging or production via `ddl-auto: update` or `create`. See [`reference/liquibase-best-practices.md`](reference/liquibase-best-practices.md) for changelog structure, naming, and rollback rules.
- **Application objects live in the `blitzpay` schema, not `public`.** The `public` schema is reserved for PostgreSQL extensions. Liquibase's `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK` tables also live in `blitzpay` (configured via `spring.liquibase.default-schema` / `liquibase-schema`). JDBC connections set `search_path = blitzpay, public` so unqualified names resolve to app tables first and extension functions remain reachable.
- Hibernate `ddl-auto` must be `validate` (runtime) or `none` (tests). `update` is only tolerated on a developer laptop while Liquibase adoption is in progress; it must not reach CI.
- Every schema change — new table, new column, index, rename, backfill — is one or more Liquibase changesets under `src/main/resources/db/changelog/`. Every changeset includes a `-- rollback` directive.
- **Table names are prefixed with the leaf module name.** Each table is owned by exactly one Spring Modulith leaf module; its name begins with that module's identifier followed by an underscore (e.g., `push_device_registration`, `invoice_line_item`, `qrpay_request`). Cross-module table access is not permitted — consume data via the owning module's `api` surface.
- Index names follow the same prefix and mirror the table: `ix_{table}_{column}` for non-unique, `ux_{table}_{column}` for unique.
- Column names are `snake_case`; timestamp columns use `TIMESTAMPTZ` (never `TIMESTAMP`).

## Security

- Never commit secrets, credentials, private keys, or environment-specific configuration to the repository.
- TrueLayer credentials and signing keys are supplied via environment variables at runtime.
- Webhook endpoints must verify request signatures before processing payloads.
- Validate all external input at system boundaries (HTTP controllers, webhook handlers). Trust internal module APIs.

## Dependencies

- Dependency versions are managed through Gradle version catalogs or Spring Boot's dependency management plugin. Do not pin versions manually in module build files unless overriding is explicitly necessary.
- New dependencies require justification. Prefer the Spring ecosystem and existing libraries over adding new ones.
- Do not introduce libraries that duplicate functionality already provided by the stack (e.g., no additional HTTP clients when WebClient is available).

## Code Style

- Follow existing patterns and structure in the codebase. Consistency with neighbors takes priority over personal preference.
- Classes, functions, and variables use Kotlin naming conventions: `PascalCase` for types, `camelCase` for functions and properties, `UPPER_SNAKE_CASE` for constants.
- File naming: lowercase kebab-case for documentation (`api-versioning-guide.md`), UPPERCASE for well-known root files (`README.md`, `CONTRIBUTING.md`, `CONSTITUTION.md`).
- Keep controllers thin. Business logic belongs in service classes within the module.
- Configuration classes use `@ConfigurationProperties` with immutable data classes.

## Commit and Review

- Semantic commit messages: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`.
- One semantic commit per logical change. Squash fixup commits before merging.
- Every pull request must pass CI (`./gradlew check`) before merge.

## Coding Convention References

For technology-specific patterns and detailed best practices, refer to:

| Topic | Document |
|---|---|
| Architecture guidelines (module layout, dependency rules) | [`reference/architecture-guidelines.md`](reference/architecture-guidelines.md) |
| API versioning (URL-path strategy, version resolver, Swagger compat) | [`reference/api-versioning-guide.md`](reference/api-versioning-guide.md) |
| MCP servers (tools, resources, prompts, contract documentation) | [`reference/mcp-server-best-practices.md`](reference/mcp-server-best-practices.md) |
| Spring Boot (`@ConfigurationProperties`, injection, reactive stack) | [`reference/spring-boot-best-practices.md`](reference/spring-boot-best-practices.md) |
| Spring Modulith (module boundaries, events, verification) | [`reference/spring-modulith-best-practices.md`](reference/spring-modulith-best-practices.md) |
| Spring Data JPA (entities, repositories, transactions) | [`reference/spring-data-jpa-best-practices.md`](reference/spring-data-jpa-best-practices.md) |
| Liquibase (schema migrations, changeset conventions) | [`reference/liquibase-best-practices.md`](reference/liquibase-best-practices.md) |
| Docker (multi-stage builds, layer caching, `.dockerignore`) | [`reference/docker-best-practices.md`](reference/docker-best-practices.md) |
| Kubernetes ingress troubleshooting (kind, nginx, TLS, NodePort) | [`reference/k8s-ingress-troubleshooting/`](reference/k8s-ingress-troubleshooting/) |
| socat (install, run modes, debug `T` state, systemd, NodePort bridging) | [`reference/utils/socat-guide.md`](reference/utils/socat-guide.md) |

## Documentation

- Update `README.md`, `CONTRIBUTING.md`, and relevant `reference/` docs when module boundaries, environment variables, API contracts, or architecture rules change.
- Do not let documentation drift from implementation. Treat doc updates as part of the change, not a follow-up task.
