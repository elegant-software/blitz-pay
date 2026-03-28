# Contributing

## Scope

This repository uses `CONTRIBUTING.md` as the source of truth for contributor workflow conventions, including Git commit structure. Human contributors and AI agents should follow the same rules.

## Architecture Principles

Contributors should preserve the project architecture described in `README.md`. In practice, that means:

- Model the system as Spring Modulith application modules organized by business capability, not technical layer. A module should own its API, internal implementation, persistence, and events within one feature package.
- Keep the `@SpringBootApplication` class as a bootstrap-only type. For Spring Modulith, the preferred layout is to place it in the root package `com.elegant.software.blitzpay` so direct sub-packages become module roots by default.
- If module detection cannot follow the default direct-subpackage rule, make the deviation explicit with Spring Modulith configuration and document it in `README.md`.
- Treat each module root package as the public API of that module. Put internal types in sub-packages such as `internal` and do not reference them from other modules.
- Prefer closed modules. Only use open modules as a temporary migration step, and document why the module cannot yet enforce encapsulation.
- In Kotlin, declare module metadata with a dedicated type annotated with `@org.springframework.modulith.PackageInfo` because Kotlin does not support `package-info.java`. Use that metadata type for `@ApplicationModule` and `@NamedInterface` declarations when needed.
- Use `@ApplicationModule(allowedDependencies = …)` for modules with important dependency constraints. Prefer explicit allowed dependencies over informal conventions once more than a few modules exist.
- Expose additional cross-module contracts through named interfaces instead of making entire implementation packages public.
- Prefer cross-module communication through published domain events and `@ApplicationModuleListener` or transactional event listeners instead of direct bean coupling when the interaction is asynchronous or secondary to the main use case.
- If a module starts depending on many beans from another module, treat that as an architecture smell and reconsider the boundary before adding more direct dependencies.
- Keep HTTP controllers, provider clients, persistence adapters, and startup/configuration code inside the module they belong to, but separate them from core business logic within that module.
- Use Kotlin idioms that work well with Spring: constructor injection, immutable `val` state by default, null-safety, and data classes for DTOs or configuration properties where appropriate.
- Do not use Kotlin `data class` indiscriminately for JPA entities; prefer regular classes for entity types unless the mapping semantics are explicitly safe.
- Keep configuration properties immutable where possible and prefer constructor-bound properties over scattered `@Value` injection.
- Maintain module verification tests with `ApplicationModules.of(...).verify()` and prefer `@ApplicationModuleTest` for integration tests that exercise a single module and its declared dependencies.
- Keep Spring Modulith documentation current. When module boundaries or dependencies change, regenerate and review the module diagrams and canvases produced under `build/spring-modulith-docs`.
- Keep request and response contracts stable once exposed; if an API change is required, update versioning behavior, module tests, and documentation together.
- Treat external provider integration details, credentials, and signing configuration as infrastructure concerns and keep them isolated behind module APIs.
- Update `README.md` and related docs when module boundaries, architecture rules, runtime prerequisites, environment variables, or integration behavior changes.

When a change would violate one of these principles, either redesign it to fit the existing architecture or update the architecture docs and implementation together as one deliberate change.

## File Naming

### Markdown files

- Use **lowercase kebab-case** for all regular documentation files: `api-versioning-guide.md`, `architecture-guidelines.md`.
- Use **UPPERCASE** only for well-known root-level convention files that tools and platforms recognize by name: `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `AGENTS.md`, `CLAUDE.md`, `LICENSE.md`.
- No spaces or underscores in file names.
- Apply the same rule to subdirectories that hold documentation (e.g. `reference/`, `docs/`).
- Files inside hidden tool directories (`.agents/`, `.codex/`, `.specify/`, `.claude/`) are managed by external tooling and are exempt from this convention.

## Commit Messages

Use semantic commit messages in the form:

```text
<type>: <summary>
```

Examples:

- `feat: add invoice status endpoint`
- `fix: correct invoice processing flow`
- `docs: update README environment setup`
- `refactor: simplify payment configuration`
- `chore: refresh Gradle tooling`

Prefer these commit types:

- `feat` for new user-visible capabilities
- `fix` for bug fixes or regressions
- `docs` for documentation-only changes
- `refactor` for structural changes without intended behavior change
- `chore` for maintenance, tooling, or non-feature housekeeping

Commit summaries should be short, imperative, and specific to the change.

## Squash Preference

Prefer a small, reviewable history. If a branch contains iterative fixup commits for one logical change, squash them before merging or pushing final review updates.

## Labels

If pull requests or issues use labels, keep them aligned with the commit intent and scope. Typical labels should reflect:

- change type, such as `feature`, `bug`, `docs`, or `chore`
- affected area, such as `payments`, `invoice`, `config`, `api`, or `build`

Do not invent new labels casually. Reuse the repository's existing label taxonomy when available.

## Versioning

When changes affect release notes, changelog generation, or version semantics, keep commit messages and PR descriptions clear enough to support downstream automation.

## Agent Guidance

AI agents working in this repository should:

- read this file before preparing commits
- prefer one semantic commit per logical change unless the user asks otherwise
- keep generated commit messages consistent with the conventions above
