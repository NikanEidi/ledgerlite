# Technical Documentation

Reference documents for each module of LedgerLite. Each document describes what the module does, how its parts connect, the endpoints and error responses, the database schema, and the reasons behind the main decisions.

The documents are written to be read while working on or reviewing the code.

| Module | Document | Status |
|---|---|---|
| Auth | [01-auth.md](01-auth.md) | Done |
| Accounts | [02-accounts.md](02-accounts.md) | Opening, listing, and reading accounts done. Balance operations planned |

For a learning-oriented walkthrough of the same modules, see the [learning notes](../learning/README.md).

## How to read a module document

Each document uses the same sections:

1. **Responsibilities**: what the module must do.
2. **Endpoints**: the public contract.
3. **Data model**: the database schema as an entity-relationship diagram.
4. **Component structure**: the classes and their dependencies.
5. **Sequence diagrams**: the order of calls for each flow.
6. **Error handling**: which exception produces which status.
7. **Design decisions**: the choices and their reasons.
8. **Known limits**: what is not yet handled.
