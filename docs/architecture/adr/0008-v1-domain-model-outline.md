# ADR 0008: V1 Cloud domain model outline location

- **Status:** Proposed
- **Date:** 2026-09-19
- **Related issues:** #6 (governing), #2 (architecture), #3/#4/#5 (dependencies)

## Context

Issue #6 requires a shared domain vocabulary before schema freeze. Issue #2 fixed module boundaries and security properties but did not catalog entities.

## Decision

Publish the V1 Cloud control-plane domain model at [domain/v1-domain-model.md](../domain/v1-domain-model.md). TinyAdmin Cloud data is modeled for PostgreSQL. Customer PostgreSQL/MongoDB data remains outside Cloud schema aside from non-secret discovery cache. Protocol wire formats remain Issue #3; Security SRs remain Issues #4/#5.

## Consequences

Backend implements from the catalog; open `DEPENDS-#N` items block freezing of those columns only.
