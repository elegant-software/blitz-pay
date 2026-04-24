# MCP Server Best Practices

Authoritative reference for documenting and designing Model Context Protocol (MCP) servers in this project.
Use this alongside `CONSTITUTION.md` when adding or changing MCP-facing capabilities.

---

## 1. Treat MCP Like a Published Contract

MCP servers do not have a single Swagger-equivalent standard document, but they do expose a standard protocol contract through:

- server capabilities
- tool schemas
- resource definitions
- prompt definitions

Any MCP tool, resource, or prompt exposed by this repository is a public integration surface and must be documented with the same discipline as an HTTP API.

---

## 2. Minimum Documentation Required

For every MCP server or MCP-facing module, document:

- purpose of the server
- transport and runtime assumptions
- authentication and authorization model
- exposed tools with input and output examples
- exposed resources and URI patterns
- exposed prompts and required arguments
- error behavior and validation rules
- rate limits, size limits, and timeout expectations

If a capability is intended for internal use only, state that explicitly.

---

## 3. Tool Documentation Format

Each tool should describe:

- tool name
- one-sentence purpose
- required arguments
- optional arguments
- input constraints and validation rules
- output shape
- failure modes
- example request and example response

Prefer stable, explicit field names. Avoid overloaded parameters or ambiguous free-form inputs when a structured schema is practical.

---

## 4. Resource Documentation Format

For each resource or resource template, document:

- URI or URI template
- meaning of each template parameter
- MIME type
- whether content is text or binary
- intended audience
- refresh or caching expectations
- authorization requirements

Use predictable URI schemes and naming. Resource identifiers should remain stable over time.

---

## 5. Prompt Documentation Format

For each prompt, document:

- prompt name
- purpose
- required and optional arguments
- expected user workflow
- any embedded resources used
- safety or approval constraints

Prompts should be discoverable and narrowly scoped. Do not hide critical behavior in vague prompt names.

---

## 6. Schema and Compatibility Rules

- Prefer machine-readable schemas wherever MCP tooling supports them.
- Backward-incompatible changes to tool arguments or output fields must be treated as contract changes.
- Additive changes are preferred over breaking renames or semantic shifts.
- If behavior changes materially, update both docs and examples in the same change.

Do not rely on clients inferring meaning from implementation details.

---

## 7. Operational Best Practices

- Validate all inputs at the server boundary.
- Return clear, structured errors for invalid params, missing arguments, and internal failures.
- Log tool invocations and failures without leaking secrets or personal data.
- Define timeouts for network and database access.
- Keep long-running or side-effecting operations explicit in tool descriptions.

If a tool mutates data, the documentation must say so plainly.

---

## 8. Security Requirements

- Never expose secrets, credentials, or raw private configuration through tools or resources.
- Enforce least privilege for each capability.
- Document which capabilities are read-only and which can mutate state.
- Sanitize any user-controlled content that can flow into prompts, logs, or downstream systems.

For high-impact tools, document approval expectations and audit considerations.

---

## 9. Testing Expectations

Every MCP-facing capability should have tests for:

- valid request handling
- invalid input handling
- authorization or access checks where applicable
- stable output shape
- contract-sensitive edge cases

Where possible, keep example payloads and fixtures static and reviewable.

---

## 10. Recommended Repo Layout

When the repository grows MCP functionality, prefer keeping:

- implementation under the owning module
- contract tests near other integration tests
- human-readable MCP docs under `reference/` or feature-specific docs
- example payloads under `src/test/resources/`

Do not scatter MCP contract details across unrelated README files.

---

## 11. Practical Standard to Follow

The practical standard for MCP documentation is:

- official MCP protocol semantics for capabilities and message shapes
- machine-readable schemas for tools and resources
- repository-local human documentation for behavior, examples, and operational constraints

In other words: use MCP schema for discovery and validation, and use repository docs the way REST projects use OpenAPI plus handbook-style guidance.
