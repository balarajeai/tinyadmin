# TinyAdmin QA Documentation

This directory contains quality assurance and verification strategy documents for TinyAdmin V1.

## Documents

### [V1 Verification Strategy](./v1-verification-strategy.md)
**Status:** Draft (Issue #7)  
**Purpose:** Lean evidence contract for safe V1 implementation start

Defines:
- Test level selection criteria (unit, integration, API, E2E)
- Security invariant verification approach
- First vertical slice checklist (Unlock User end-to-end)
- Negative testing and failure-mode requirements
- Audit, secrets, and FA-CONN-BLAST verification
- Evidence standards and release-blocking principles

**Not:** A complete test suite, full traceability matrix, or enterprise QA encyclopedia.

## Evidence artifacts

Test evidence (screenshots, recordings, reports) should be stored in:
- `docs/qa/evidence/` (for manual verification artifacts like QA-EVIDENCE-ENV-PROD-UX)
- CI job logs and artifacts (for automated test evidence)

## Quick links

- Product requirements: [docs/product/v1-requirements.md](../product/v1-requirements.md)
- Security requirements: [docs/security/v1-security-requirements.md](../security/v1-security-requirements.md)
- Threat model: [docs/security/v1-threat-model.md](../security/v1-threat-model.md)
- Architecture: [docs/architecture/v1-system-architecture.md](../architecture/v1-system-architecture.md)
- Engineering governance: [balarajeai/engineering](https://github.com/balarajeai/engineering)
