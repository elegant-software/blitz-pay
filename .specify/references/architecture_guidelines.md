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

## Ownership and Changes
- Any change that affects module boundaries or exposed APIs MUST be reflected in this document.

