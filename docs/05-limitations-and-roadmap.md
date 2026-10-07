# Limitations and Roadmap

This document lists everything that is **not implemented**, **known to be incomplete**, or **deliberately simplified** in LedgerLite. It is kept separate from the module references so that those documents describe what exists, and this one describes what does not.

Each item has a priority:

- **High**: affects correctness or security of money or identity. Must be fixed before any real use.
- **Medium**: affects robustness, consistency, or operability.
- **Low**: quality-of-life or polish.

Each item also has a type:

- **Not implemented**: a feature that is absent.
- **Known issue**: a feature exists but behaves incorrectly in an edge case.
- **Simplification**: a deliberate shortcut for this project, documented as such.

---

## 1. Authentication (see [01-auth.md](01-auth.md))

| Priority | Type | Item | Notes |
|---|---|---|---|
| High | Not implemented | Token revocation and logout | An issued JWT stays valid until it expires (one hour). A refresh-token flow with revocation is the standard fix. |
| High | Not implemented | Rate limiting on `/auth/login` | Without it, password guessing is unlimited. |
| High | Simplification | Secret key in `application.yaml` | The default is for local development only. Production must provide `JWT_SECRET_KEY` from the environment. |
| Medium | Not implemented | Email verification and password reset | Accounts are active immediately after registration. |
| Medium | Not implemented | Account lockout after repeated failures | Complements rate limiting. |
| Low | Known issue | `/auth/me` returns plain text | A JSON body would match the rest of the API. |

## 2. Accounts and money movement (see [02-accounts.md](02-accounts.md))

| Priority | Type | Item | Notes |
|---|---|---|---|
| High | Not implemented | Double-entry ledger | A transfer changes two balances and stores one transfer row. A bank also writes one debit row and one credit row per movement, so every balance can be explained from history. |
| High | Not implemented | Transaction history endpoint | Deposits and withdrawals leave no record, so a balance cannot be reconstructed. Depends on the ledger. |
| High | Known issue | Same idempotency key with a different body | The first result is returned without checking the body. The request should be rejected with `422` when the body differs. |
| High | Known issue | Two concurrent requests with the same key | Both can pass the existence check. One then fails on the unique constraint and returns `500`. The fix is to catch the violation and return the stored result. |
| High | Not implemented | Idempotency for deposits and withdrawals | A retried deposit or withdrawal moves money again. |
| High | Known issue | Amount precision not validated | An amount with more than four decimals is accepted, and the database rounds it on save. The fix is `@Digits(integer = 15, fraction = 4)` on request DTOs. |
| Medium | Known issue | Account number collision is not retried | The unique constraint rejects a duplicate, but the code does not generate another number. |
| Medium | Known issue | Missing user returns `500` | If a valid token refers to a deleted user, `IllegalStateException` is thrown. It should map to `401`. |
| Medium | Simplification | Owner is loaded eagerly | `@ManyToOne` defaults to eager fetching. `FetchType.LAZY` is preferable for list endpoints. |
| Medium | Not implemented | Daily limits, fees, minimum balance | Real accounts have these rules. None are implemented. |
| Low | Known issue | Account opening returns plain text | Should return an `AccountResponse` JSON body. |

## 3. Loans (see [03-loans.md](03-loans.md))

| Priority | Type | Item | Notes |
|---|---|---|---|
| High | Simplification | Credit score is simulated | No bureau or partner is called. The score is derived from the email hash. |
| High | Not implemented | Approved loans are not disbursed | Approval does not credit any account. Connecting approval to the ledger is required for the feature to be real. |
| High | Not implemented | Manual review | Real lenders review some applications by hand. The `UNDER_REVIEW` state is only transient. |
| Medium | Not implemented | Repayment schedule, interest, due dates | A loan has no lifecycle after approval. |
| Medium | Not implemented | Timeout and retry for the partner call | A slow partner would block the request. |
| Medium | Simplification | Decision rule is hard-coded | The thresholds (650 and 50,000) should move to configuration. |
| Low | Known issue | Illegal state move returns `500` | `IllegalStateException` from `moveTo` is a programming error. It cannot happen through the public API, but it should be covered by tests. |

## 4. Testing (see [04-testing.md](04-testing.md))

| Priority | Type | Item | Notes |
|---|---|---|---|
| High | Not implemented | Concurrency tests | Locking is not proven by parallel requests. A test with two threads transferring from one account would prove it. |
| High | Not implemented | Loan integration tests | The loan endpoints were verified by hand, not in code. |
| Medium | Not implemented | Validation error tests | The `400` responses are not asserted in code. |
| Medium | Not implemented | Test for a reused key with a different body | Covers the known idempotency weakness above. |
| Medium | Not implemented | Coverage report | No measurement of which code is exercised. JaCoCo would provide it. |
| Low | Known issue | Mockito runtime warnings | Mockito attaches an agent at runtime. Declaring it as a Maven agent removes the warnings. |

## 5. Platform and operations

| Priority | Type | Item | Notes |
|---|---|---|---|
| High | Simplification | Local database credentials in `compose.yaml` | Development values only. |
| Medium | Not implemented | API documentation (OpenAPI and Swagger UI) | Would make the endpoints self-describing. |
| Medium | Not implemented | Metrics and structured logs | Only the health endpoint is exposed. |
| Medium | Not implemented | API versioning | All endpoints live under the unversioned root. |
| Low | Not implemented | Pagination on list endpoints | Lists return everything. Fine at this size, not at scale. |

---

## Roadmap

The order below reflects dependencies, not difficulty.

1. **Security hardening.** Rate limiting on login, validation of amount precision, and handling of a deleted user's token.
2. **Idempotency completion.** Compare request bodies, handle concurrent duplicates, and extend keys to deposits and withdrawals.
3. **Double-entry ledger.** One debit and one credit row per movement, plus a transaction history endpoint.
4. **Loan disbursement.** Approved loans credit a target account through the ledger.
5. **Concurrency tests.** Parallel transfers, duplicate keys, and deadlock avoidance under load.
6. **Operations.** OpenAPI, metrics, and coverage. (CI is already in place: tests run on every push and pull request.)

Each step should ship with tests and an update to the matching module document.
