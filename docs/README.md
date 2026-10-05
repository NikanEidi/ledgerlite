# Technical Documentation

This folder is the technical reference for LedgerLite. Each document describes what a module does, how its parts connect, its endpoints and errors, its data model, and the reasons behind its main design decisions.

For a step-by-step explanation of the same code, see the [learning notes](../learning/README.md).

## Index

| # | Document | Scope |
|---|---|---|
| 1 | [Authentication](01-auth.md) | registration, login, JWT issuance, protected endpoints |
| 2 | [Accounts and money movement](02-accounts.md) | accounts, deposits, withdrawals, transfers, locking, idempotency |
| 3 | [Loans](03-loans.md) | applications, state machine, credit-score boundary, decision rule |
| 4 | [Testing](04-testing.md) | test strategy, inventory, infrastructure, how to run |
| 5 | [Limitations and roadmap](05-limitations-and-roadmap.md) | everything not implemented or known to be incomplete, with priorities |

## Document structure

Every module document follows the same structure:

1. **Responsibilities**: what the module is required to do.
2. **Endpoints**: the public contract, with success and error responses.
3. **Data model**: tables, keys, and constraints.
4. **Component structure**: classes and their dependencies.
5. **Sequence diagrams**: the order of calls for each important flow.
6. **Error handling**: which exception produces which HTTP status.
7. **Design decisions**: each choice and its justification.
8. **Scope and limitations**: a pointer to the consolidated list in document 5.

Limitations are kept in one place on purpose. Module documents describe what exists. Document 5 describes what does not, and what should happen next.
