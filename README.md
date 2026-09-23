# TinyAdmin

TinyAdmin provides controlled Safe Actions for support and operations teams without exposing customer database credentials. It enables secure, audited administrative operations through a centralized control plane and customer-side agents.

## Repository Layout

- **cloud/** - TinyAdmin Cloud control plane
- **agent/** - Customer-side TinyAdmin Agent
- **web/** - TinyAdmin Web application
- **tests/** - Cross-component/E2E verification
- **deploy/** - Runtime/deployment assets
- **docs/** - Product, architecture, security and QA documentation

## Documentation

See `docs/` for authoritative product planning, architecture decisions, and security documentation (Issues #1–#8).

## Implementation Milestone

The first implementation milestone is the **Unlock User PostgreSQL vertical slice** (Issues #18–#21), which delivers end-to-end functionality for PostgreSQL user unlock operations.
