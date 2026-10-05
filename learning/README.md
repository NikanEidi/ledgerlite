# Learning Notes

These notes teach the ideas behind LedgerLite, one concept at a time. Each lesson shows the idea, why it matters, the code from this project that applies it, a common mistake, and an interview sentence.

Read the modules in this order. Each one builds on the ideas of the previous one.

| # | Module | Notes | Lessons | Main ideas |
|---|---|---|---|---|
| 1 | Auth | [01-auth.md](01-auth.md) | 9 | layers, DTOs, validation, hashing, JWT, security chain, error format |
| 2 | Accounts and money | [02-accounts.md](02-accounts.md) | 14 | exact money, ownership, locking, deadlock prevention, idempotency |
| 3 | Loans | [03-loans.md](03-loans.md) | 8 | state machines, interfaces for external services, one transaction |
| 4 | Testing | [04-testing.md](04-testing.md) | 9 | unit vs integration tests, Testcontainers, MockMvc |

## How to use these notes

1. Read one lesson at a time. Do not try to memorize the whole file.
2. Open the code that the lesson names, and find the line in the project.
3. Read the "common mistake" first. It shows what the lesson protects you from.
4. Say the interview sentence out loud. If it is hard to say, reread the idea.

For the technical reference, which describes the same code in more detail with diagrams, see [docs](../docs/README.md).
