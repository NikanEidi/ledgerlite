# Accounts Module: Technical Reference

**Status:** Opening, listing, and reading accounts are done and tested. Balance changes (deposit, withdraw, transfer) are planned.
**Packages:** `account`, with dependencies on `user` and `common`

This document describes the Accounts module as it currently exists. For a step-by-step explanation intended for learning, see [learning/02-accounts.md](../learning/02-accounts.md).

---

## 1. Responsibilities

- Open a bank account for the authenticated user.
- List the authenticated user's accounts.
- Return one account by ID, but only if it belongs to the authenticated user.
- Store money as exact decimal values, never as floating-point numbers.
- Enforce ownership, uniqueness, and non-negative balances in the database.

## 2. Endpoints

All endpoints require a bearer token. The owner is always taken from the token's subject, never from the request.

| Method | Path | Request body | Success | Failure cases |
|---|---|---|---|---|
| POST | `/accounts` | `{"type": "CHECKING" or "SAVINGS"}` | `201 Created`, plain text `Account opened: <number>` | `400` invalid type, `401` no token |
| GET | `/accounts` | none | `200 OK`, JSON array of `AccountResponse` | `401` no token |
| GET | `/accounts/{id}` | none | `200 OK`, one `AccountResponse` | `404` not found or not owned, `401` no token |

Note: the POST response is currently plain text. Changing it to return an `AccountResponse` JSON body is a planned improvement.

## 3. Data model

```mermaid
erDiagram
    APP_USER ||--o{ ACCOUNT : "owns"

    APP_USER {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR full_name
        VARCHAR role
        TIMESTAMPTZ created_at
    }

    ACCOUNT {
        UUID id PK
        UUID owner_id FK "references app_user.id"
        VARCHAR account_number UK "10 digits"
        VARCHAR type "CHECKING or SAVINGS"
        NUMERIC balance "numeric(19,4), CHECK balance >= 0"
        TIMESTAMPTZ created_at
    }
```

Migration: `src/main/resources/db/migration/V2__create_account.sql`.

| Constraint | Column | What it guarantees |
|---|---|---|
| Primary key | `account.id` | Each account has one unique identifier |
| Foreign key | `account.owner_id` | An account always points to an existing user |
| Unique | `account.account_number` | No two accounts share a number |
| Check | `account.type` | Only the two allowed types are stored |
| Check | `account.balance` | The balance can never be negative |
| Index | `account.owner_id` | Listing one user's accounts is fast |

## 4. Component structure

```mermaid
flowchart TB
    subgraph web["Web layer"]
        CTRL["AccountController"]
        GEH["GlobalExceptionHandler"]
    end
    subgraph service["Service layer"]
        SVC["AccountService"]
    end
    subgraph data["Data layer"]
        AR["AccountRepository"]
        UR["UserRepository"]
        ACC["Account entity"]
        USR["User entity"]
    end
    subgraph dto["DTOs"]
        REQ["OpenAccountRequest"]
        RES["AccountResponse"]
    end

    CTRL --> SVC
    CTRL --> REQ
    CTRL --> RES
    SVC --> AR
    SVC --> UR
    AR --> ACC
    UR --> USR
    ACC --> USR
    CTRL -. "throws to" .-> GEH
```

## 5. Sequence diagrams

### 5.1 Open an account

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant CTRL as AccountController
    participant SVC as AccountService
    participant UR as UserRepository
    participant AR as AccountRepository
    participant DB as PostgreSQL

    C->>CTRL: POST /accounts (Bearer token, type)
    CTRL->>CTRL: validate type is not null
    CTRL->>SVC: openAccount(jwt.subject, type)
    SVC->>UR: findByEmail(email)
    UR->>DB: SELECT app_user
    DB-->>UR: User
    SVC->>SVC: generate 10-digit account number
    SVC->>AR: save(new Account(owner, number, type))
    AR->>DB: INSERT INTO account
    DB-->>AR: row with generated id
    SVC-->>CTRL: Account
    CTRL-->>C: 201 Created, "Account opened: number"
```

### 5.2 List my accounts

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant CTRL as AccountController
    participant SVC as AccountService
    participant UR as UserRepository
    participant AR as AccountRepository

    C->>CTRL: GET /accounts (Bearer token)
    CTRL->>SVC: listAccounts(jwt.subject)
    SVC->>UR: findByEmail(email)
    UR-->>SVC: User
    SVC->>AR: findByOwnerId(user.id)
    AR-->>SVC: List of Account
    SVC-->>CTRL: List of Account
    CTRL->>CTRL: map each Account to AccountResponse
    CTRL-->>C: 200 OK, JSON array
```

### 5.3 Read one account (ownership check)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant CTRL as AccountController
    participant SVC as AccountService
    participant UR as UserRepository
    participant AR as AccountRepository
    participant GEH as GlobalExceptionHandler

    C->>CTRL: GET /accounts/{id} (Bearer token)
    CTRL->>SVC: getAccount(jwt.subject, id)
    SVC->>UR: findByEmail(email)
    UR-->>SVC: User
    SVC->>AR: findByIdAndOwnerId(id, user.id)
    alt account exists and belongs to user
        AR-->>SVC: Account
        SVC-->>CTRL: Account
        CTRL-->>C: 200 OK, AccountResponse
    else not found or owned by someone else
        AR-->>SVC: empty
        SVC-->>CTRL: AccountNotFoundException
        CTRL-->>GEH: exception
        GEH-->>C: 404 Not Found, ProblemDetail
    end
```

The query uses both `id` and `owner_id` in one `WHERE` clause. A user who guesses another user's account ID receives the same `404` as for an ID that does not exist.

## 6. Error handling

| Exception | Handler | HTTP status | Raised when |
|---|---|---|---|
| `AccountNotFoundException` | `GlobalExceptionHandler` | 404 | account missing or not owned by the caller |
| `MethodArgumentNotValidException` | `GlobalExceptionHandler` | 400 | `type` is missing or not `CHECKING` or `SAVINGS` |
| `IllegalStateException` | none yet | 500 | the authenticated user is missing from the database |

The last row is a known gap. It occurs only if a valid token refers to a user that was deleted after the token was issued.

## 7. Money representation

| Layer | Type | Reason |
|---|---|---|
| Database | `NUMERIC(19,4)` | Exact decimal with 4 places |
| Java | `BigDecimal` | Exact arithmetic |
| JSON | decimal number, shown as `0.0000` | Keeps the fixed scale |
| Forbidden | `double`, `float` | Binary floating point cannot represent `0.1` exactly |

## 8. Design decisions

| Decision | Reason |
|---|---|
| Owner comes from the JWT subject | The client cannot open an account for another user |
| Account number generated on the server | The client cannot choose or guess numbers |
| `SecureRandom` for account numbers | Numbers are hard to predict |
| `findByIdAndOwnerId` | Ownership is enforced in the query, not in code after the fetch |
| `404` for foreign accounts | Hides whether another user's account exists |
| `updatable = false` on owner, number, type | An account cannot change owner or type after creation |
| `balance` has no setter | Balance changes only through domain methods (planned) |
| `@Transactional(readOnly = true)` on reads | Signals read-only work to the database and to Hibernate |
| `FetchType` left at default for `owner` | Documented as a possible optimization (see Known limits) |

## 9. Known limits

- **Account number collisions.** A duplicate number is rejected by the `UNIQUE` constraint, but the code does not retry. A user would see a `500` in that rare case.
- **Eager loading of owner.** `@ManyToOne` defaults to eager fetching. Switching to `FetchType.LAZY` is planned when list performance matters.
- **No balance operations yet.** Deposit, withdraw, and transfer are not implemented, so `balance` is always zero.
- **Concurrency for money.** Balance changes will need locking (optimistic with `@Version`, or pessimistic) before transfers are added.
- **Plain-text open response.** The POST response will become a JSON `AccountResponse`.
