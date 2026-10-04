# LedgerLite — Design Notes

These notes document the system design of LedgerLite as it's built, part by part, in a teaching-style pairing session (Nikan + Claude). Each part covers one completed module: an ERD, a class diagram, and sequence diagrams for its real flows — all diagrams use [Mermaid](https://mermaid.js.org/), so they render directly on GitHub.

## Parts

| # | Part | Status | Notes |
|---|------|--------|-------|
| 1 | [Auth (Register, Login, JWT)](01-auth-module.md) | ✅ Done | [01-auth-module.md](01-auth-module.md) |
| 2 | Accounts (open account, balance, history) | ⏳ Planned | added once built |
| 3 | Transfers & Loan Applications | ⏳ Planned | added once built |

## Why notes exist per module, not all at once

The whole system isn't built yet. Writing notes only for what's real and tested keeps the documentation trustworthy — a diagram here always matches code that actually runs, not a plan that might change.
