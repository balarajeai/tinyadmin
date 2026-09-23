# TinyAdmin Agent & Engineer Directory Ownership

This document establishes directory ownership for TinyAdmin implementation consistent with engineering governance. For complete engineering processes and guidelines, see [balarajeai/engineering](https://github.com/balarajeai/engineering).

## Directory Ownership

- **cloud/** → Backend Engineer (Issue #18)
- **agent/** → Agent implementer (Issue #19)
- **web/** → Frontend Engineer (Issue #20)
- **tests/** → QA Engineer (Issue #21)
- **deploy/** → Platform Engineer
- **docs/** → Authoritative planning; do not reopen unless genuine blocker

## Governance Principles

- GitHub is system of record; preserve Issues #1–#8 decisions
- No product code in bootstrap PRs; specialists initialize applications inside their directories
- Component owners are responsible for initializing their own application frameworks and dependencies
