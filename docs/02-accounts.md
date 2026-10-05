# Accounts Module: Technical Reference

**Status:** Done and covered by tests. Accounts, deposits, withdrawals, and transfers work end to end.
**Packages:** `account`, with dependencies on `user` and `common`

This document describes the accounts module: opening accounts, reading them, changing balances (deposit, withdrawal, transfer), and the rules that keep money correct. For a step-by-step explanation of the same code, see [learning/02-accounts.md](../learning/02-accounts.md).

---

## 1. Responsibilities

- Open a `CHECKING` or `SAVINGS` account for the authenticated user.
- List the user's accounts and read one of them.
- Deposit money into an owned account.
- Withdraw money from an owned account, only if the balance covers it.
- Transfer money from an owned account to any account by its number, exactly once per idempotency key.
- Store money as exact decimals and enforce non-negative balances in the database.

## 2. Endpoints

All endpoints require a bearer token. The owner is always taken from the token's subject, never from the request body.

| Method | Path | Request | Success | Failure cases |
|---|---|---|---|---|
| POST | `/accounts` | `{"type": "CHECKING"}` or `"SAVINGS"` | `201`, plain text `Account opened: <number>` | `400` invalid type, `401` no token |
| GET | `/accounts` | none | `200`, JSON array of `AccountResponse` | `401` no token |
| GET | `/accounts/{id}` | none | `200`, `AccountResponse` | `404` not found or not owned, `401` |
| POST | `/accounts/{id}/deposits` | `{"amount": 500.00}` | `200`, updated `AccountResponse` | `400` invalid amount, `404` not owned, `401` |
| POST | `/accounts/{id}/withdrawals` | `{"amount": 50.00}` | `200`, updated `AccountResponse` | `400` invalid amount, `404` not owned, `422` insufficient funds, `401` |
| POST | `/accounts/{id}/transfers` | `{"toAccountNumber": "...", "amount": 100.00}` plus header `Idempotency-Key` | `201`, `TransferResponse` | `400` same account or missing key, `404` recipient or source not found, `422` insufficient funds, `401` |

Note: the account-opening response is plain text. See [05-limitations-and-roadmap.md](05-limitations-and-roadmap.md).

## 3. Data model

```mermaid
erDiagram
    APP_USER ||--o{ ACCOUNT : "owns"
    APP_USER ||--o{ TRANSFER : "initiates"
    ACCOUNT ||--o{ TRANSFER : "debited in (from_account_id)"
    ACCOUNT ||--o{ TRANSFER : "credited in (to_account_id)"

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
        UUID owner_id FK
        VARCHAR account_number UK "10 digits"
        VARCHAR type "CHECKING or SAVINGS"
        NUMERIC balance "NUMERIC(19,4), CHECK balance >= 0"
        TIMESTAMPTZ created_at
    }

    TRANSFER {
        UUID id PK
        UUID initiator_id FK
        UUID from_account_id FK
        UUID to_account_id FK
        NUMERIC amount "NUMERIC(19,4), CHECK amount > 0"
        VARCHAR idempotency_key "unique per initiator"
        TIMESTAMPTZ created_at
    }
```

Migrations: `V2__create_account.sql` and `V3__create_transfer.sql`.

### Constraints that protect the data

| Table | Constraint | What it guarantees |
|---|---|---|
| `account` | primary key on `id` | each account has one identifier |
| `account` | foreign key `owner_id` | an account always belongs to an existing user |
| `account` | unique `account_number` | no two accounts share a number |
| `account` | check `type` | only `CHECKING` or `SAVINGS` |
| `account` | check `balance >= 0` | a balance can never be negative, even if the code has a bug |
| `transfer` | check `amount > 0` | zero or negative transfers are impossible |
| `transfer` | check `from_account_id <> to_account_id` | an account cannot transfer to itself |
| `transfer` | unique `(initiator_id, idempotency_key)` | a retried request cannot create a second transfer |

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
        AR["AccountRepository<br/>includes pessimistic lock"]
        TR["TransferRepository"]
        UR["UserRepository"]
    end
    subgraph model["Domain model"]
        ACC["Account<br/>debit and credit rules"]
        TRF["Transfer"]
        TYPE["AccountType"]
    end
    subgraph dto["DTOs (records)"]
        OAR["OpenAccountRequest"]
        DR["DepositRequest"]
        WR["WithdrawRequest"]
        TREQ["TransferRequest"]
        AREP["AccountResponse"]
        TRES["TransferResponse"]
    end

    CTRL --> SVC
    CTRL --> dto
    SVC --> AR
    SVC --> TR
    SVC --> UR
    AR --> ACC
    TR --> TRF
    ACC --> TYPE
    CTRL -. "exceptions go to" .-> GEH
```

## 5. Sequence diagrams

### 5.1 Deposit

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant CTRL as AccountController
    participant SVC as AccountService
    participant AR as AccountRepository
    participant ACC as Account
    participant DB as PostgreSQL

    C->>CTRL: POST /accounts/{id}/deposits {amount}
    CTRL->>SVC: deposit(email, id, amount)
    SVC->>AR: findByIdForUpdate(id) with row lock
    AR->>DB: SELECT ... FOR UPDATE
    DB-->>AR: account row (locked)
    SVC->>SVC: check owner matches caller
    SVC->>ACC: credit(amount)
    Note over ACC: balance = balance + amount
    SVC-->>CTRL: Account (flushed on commit)
    CTRL-->>C: 200 OK, AccountResponse
```

### 5.2 Withdrawal

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SVC as AccountService
    participant AR as AccountRepository
    participant ACC as Account

    C->>SVC: withdraw(email, id, amount)
    SVC->>AR: findByIdForUpdate(id)
    SVC->>SVC: check owner matches caller
    SVC->>ACC: debit(amount)
    alt balance is below amount
        ACC-->>SVC: InsufficientFundsException
        Note over SVC: transaction rolls back, nothing saved
        SVC-->>C: 422 Unprocessable Content
    else balance is enough
        ACC-->>SVC: balance reduced
        SVC-->>C: 200 OK, AccountResponse
    end
```

### 5.3 Transfer (with locking and idempotency)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SVC as AccountService
    participant TR as TransferRepository
    participant AR as AccountRepository
    participant DB as PostgreSQL

    C->>SVC: transfer(email, fromId, key, {toAccountNumber, amount})
    SVC->>TR: findByInitiatorIdAndIdempotencyKey(user, key)
    alt key already used
        TR-->>SVC: existing Transfer
        SVC-->>C: 201 with the same TransferResponse (no money moved)
    else key is new
        SVC->>AR: findByAccountNumber(toAccountNumber)
        alt recipient not found
            SVC-->>C: 404 Not Found
        else recipient found
            SVC->>SVC: reject if same account (400)
            SVC->>AR: lock both accounts in ascending id order
            AR->>DB: SELECT ... FOR UPDATE (first id)
            AR->>DB: SELECT ... FOR UPDATE (second id)
            SVC->>SVC: check source belongs to caller
            SVC->>SVC: from.debit(amount)
            alt funds insufficient
                SVC-->>C: 422, transaction rolls back
            else funds enough
                SVC->>SVC: to.credit(amount)
                SVC->>TR: save(new Transfer)
                TR->>DB: INSERT INTO transfer
                SVC-->>C: 201 Created, TransferResponse
            end
        end
    end
```

## 6. Why locking is needed and how it is ordered

Two transfers can run at the same time. Without locks, both could read the same balance, both see enough money, and both withdraw it. The balance would then be wrong.

`PESSIMISTIC_WRITE` locks a row until the transaction ends. Any other transaction that wants the same row waits.

Locking two accounts creates a new risk, a **deadlock**:

```mermaid
flowchart LR
    subgraph T1["Transaction 1: A to B"]
        t1a["lock A"] --> t1b["wait for B"]
    end
    subgraph T2["Transaction 2: B to A"]
        t2a["lock B"] --> t2b["wait for A"]
    end
    t1b -. "held by" .-> t2a
    t2b -. "held by" .-> t1a
```

The fix is one global order. The service always locks the account with the smaller UUID first, whatever the direction of the transfer. Both transactions then want the same first lock, so one waits for the other instead of both waiting forever.

## 7. Error handling

| Exception | HTTP status | Raised when |
|---|---|---|
| `AccountNotFoundException` | 404 | the account does not exist, or belongs to someone else |
| `RecipientAccountNotFoundException` | 404 | no account has the destination number |
| `InsufficientFundsException` | 422 | a debit would make the balance negative |
| `SameAccountTransferException` | 400 | source and destination are the same account |
| `MethodArgumentNotValidException` | 400 | an amount is missing, zero, negative, or the type is invalid |

A foreign account returns `404`, the same as a missing one. A user who guesses another account's ID learns nothing.

## 8. Money representation

| Layer | Type | Reason |
|---|---|---|
| Database | `NUMERIC(19,4)` | exact decimal, 4 places after the point |
| Java | `BigDecimal` | exact arithmetic, no binary rounding |
| JSON response | decimal, scaled to 4 places | the same format everywhere (`setScale(4)`) |
| Forbidden | `double`, `float` | `0.1` cannot be represented exactly in binary |

## 9. Design decisions

| Decision | Reason |
|---|---|
| Owner comes from the JWT subject | a client cannot act on another user's account |
| Account number generated on the server | the client cannot choose or guess numbers |
| `SecureRandom` for account numbers | numbers are hard to predict |
| Ownership checked in the query or right after locking | a non-owner never gets a usable balance |
| Balance rules live in `Account.debit` and `credit` | no code path can change a balance around the rule |
| No setter for `balance` | the only ways to change it are the named methods |
| Pessimistic lock on transfers | the simplest correct choice when the same account is written by many requests |
| Lock order by UUID | prevents deadlock between opposite transfers |
| Idempotency key stored per initiator | a retry returns the original result, and two users cannot collide |
| `@Transactional` on every money method | a failure in the middle rolls back everything |
| `updatable = false` on owner, number, type, amounts of transfers | immutable facts stay immutable |

## 10. Scope and limitations

Features that are not implemented, and behaviors that are known to be incomplete, are listed in one place: [05-limitations-and-roadmap.md](05-limitations-and-roadmap.md), section 2.
