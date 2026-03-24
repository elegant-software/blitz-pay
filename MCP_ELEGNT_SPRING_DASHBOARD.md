# MCP ELEGNT Spring Dashboard (Codex)

This document captures how we registered and used the `spring-dashboard` MCP server from Codex CLI.

## 1) Register MCP server in Codex

We added the server globally with:

```bash
codex mcp add spring-dashboard -- node /Users/mehdi/MyProject/elegent-spring-diagram/mcp-server/dist/stdio.js
```

Verify registration:

```bash
codex mcp list
```

Expected entry:
- Name: `spring-dashboard`
- Command: `node /Users/mehdi/MyProject/elegent-spring-diagram/mcp-server/dist/stdio.js`
- Status: `enabled`

## 2) Call MCP tools from Codex

We used `codex exec` and instructed the agent to call:
1. `get_current_diagram`
2. `set_current_diagram`

Example:

```bash
codex exec --ephemeral --json -C /Users/mehdi/MyProject/BlitzPay "Use the spring-dashboard MCP server. First call get_current_diagram, then call set_current_diagram with a valid payload."
```

## 3) Payload shape that worked

`set_current_diagram` expects argument:
- `payloadJson` (stringified JSON)

Important validation constraints discovered from server errors:
- Every entity must have `metadata.kind = "jpa-entity"`
- Required metadata: `packageName`, `tableName`, `idField`
- Relationship `metadata.relationType` must be one of:
  - `OneToOne`
  - `OneToMany`
  - `ManyToOne`
  - `ManyToMany`

For this project, the accepted payload represented:
- Entities: `MerchantApplication`, `MonitoringRecord`, `BusinessProfile`, `PrimaryContact`, `Person`, `SupportingMaterial`, `ReviewDecision`, `RiskAssessment`
- Relationships: 7 total (mapped into allowed JPA relation enums)

## 4) Useful troubleshooting

- If `codex mcp list` is empty, IntelliJ AI Assistant MCP config is not automatically shared with Codex CLI; register it with `codex mcp add`.
- If `codex exec` fails with network/DNS errors in sandbox, rerun with escalated permissions.
