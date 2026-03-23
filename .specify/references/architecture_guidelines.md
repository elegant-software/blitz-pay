# Architecture Guidelines

## Purpose
This document defines the baseline architecture expectations for BlitzPay modules and their public APIs.

## Modularity (Spring Modulith)
- The system uses Spring Modulith to enforce modular boundaries.
- Each module MUST keep its domain types and services internal unless explicitly exposed via a module API.
- Cross-module access MUST go through published module APIs only; no direct access to internal packages.

## API Documentation (Swagger)
- Every module that exposes HTTP endpoints MUST publish Swagger/OpenAPI documentation.
- Each module MUST define an explicit API group name for its endpoints.
- API group names SHOULD be concise and avoid spaces to keep URLs stable.
- For implementation patterns (per-module grouping, version path rewriting, `@ConfigurationProperties`): see `reference/spring-boot-best-practices.md`.

## API Versioning
- Every module that exposes HTTP endpoints MUST use Spring Boot path-based API versioning.
- Public API paths MUST include the version segment in the request path (for example `/v1/...`).
- Modules MUST keep versioning behavior consistent with the application's path-based version resolver configuration.

## Ownership and Changes
- Any change that affects module boundaries or exposed APIs MUST be reflected in this document.
