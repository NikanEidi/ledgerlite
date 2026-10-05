# Learning Notes 2: Accounts

These notes explain the Accounts module one concept at a time. Each lesson gives the idea, the reason for it, the code that applies it, a common mistake, and a sentence you can use in an interview.

The technical reference for the same module is in [docs/02-accounts.md](../docs/02-accounts.md).

## Lesson map

| Lesson | Concept | Where it appears |
|---|---|---|
| 1 | Money is not a `double` | `balance` in `Account`, `NUMERIC(19,4)` in V2 |
| 2 | Relationships and foreign keys | `owner` in `Account`, `owner_id` in V2 |
| 3 | Fields that never change | `updatable = false` |
| 4 | Enums stored as text | `AccountType` |
| 5 | Queries generated from method names | `findByOwnerId`, `findByIdAndOwnerId` |
| 6 | Ownership and the 404 rule | `AccountService.getAccount` |
| 7 | Transactions and read-only work | `@Transactional` |
| 8 | Random account numbers | `SecureRandom` in `AccountService` |
| 9 | Safe output with a DTO | `AccountResponse.from` |

---

## Lesson 1: Money is not a `double`

**Idea.** A `double` stores binary fractions. Many decimal values, such as `0.1`, cannot be represented exactly.

**Why it matters.** `0.1 + 0.2` gives `0.30000000000000004` in Java. Over thousands of transactions the errors add up to real money.

**Code.**
```sql
balance NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (balance >= 0)
```
```java
@Column(nullable = false, precision = 19, scale = 4)
private BigDecimal balance = BigDecimal.ZERO;
```

`NUMERIC(19,4)` stores 19 digits in total, 4 of them after the decimal point. `BigDecimal` does exact decimal arithmetic. The two must agree, which is why `precision` and `scale` are repeated in the Java field.

**Common mistake.** Using `double` because it is convenient. It works in tests and fails in production.

**Interview line.** "I store money as NUMERIC in the database and BigDecimal in Java, never as floating point, so arithmetic stays exact."

---

## Lesson 2: Relationships and foreign keys

**Idea.** An account belongs to one user. A user can have many accounts. This is a many-to-one relationship, from the account's side.

**Why it matters.** A foreign key lets the database reject an account whose owner does not exist. The rule is enforced even if application code has a bug.

**Code.**
```sql
owner_id UUID NOT NULL REFERENCES app_user (id)
```
```java
@ManyToOne(optional = false)
@JoinColumn(name = "owner_id", nullable = false, updatable = false)
private User owner;
```

The SQL defines the database rule. The Java annotations describe the same relationship to Hibernate.

**Diagram.**
```
app_user (1) ----- (many) account
   id  <--------  owner_id
```

**Common mistake.** Storing `owner_id` as a plain `UUID` with no foreign key. Then nothing stops orphan accounts.

**Interview line.** "Accounts reference their owner through a foreign key, so the database guarantees referential integrity."

---

## Lesson 3: Fields that never change

**Idea.** Some facts about an account should never change after creation: who owns it, its number, and its type.

**Why it matters.** If an account could silently change from checking to savings, the audit trail would be meaningless.

**Code.**
```java
@Column(name = "account_number", nullable = false, unique = true, updatable = false)
private String accountNumber;
```

`updatable = false` tells Hibernate never to include the column in an `UPDATE` statement. The database still allows it, so this is an application-level guard.

**Balance is different.** Balance must change, so it is updatable. It has no setter, so it can only change through methods that enforce the business rules (planned).

**Common mistake.** Adding a setter for every field out of habit. Then any code can change anything.

**Interview line.** "Identity fields like owner and account number are immutable. Balance changes only through domain methods, never through a public setter."

---

## Lesson 4: Enums stored as text

**Idea.** A fixed set of values, such as account types, is an `enum` in Java and a `CHECK` constraint in SQL.

**Code.**
```java
public enum AccountType { CHECKING, SAVINGS }
```
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, updatable = false)
private AccountType type;
```
```sql
type VARCHAR(20) NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS'))
```

`EnumType.STRING` stores the name, so the database holds `CHECKING`. If the stored value were the ordinal number (0 or 1), reordering the enum would silently corrupt existing data.

**Common mistake.** Leaving out the argument, `@Enumerated`, which makes JPA default to `EnumType.ORDINAL` (the position number). That breaks on reordering.

**Interview line.** "Enums are persisted as strings with a matching CHECK constraint, so reordering the Java enum cannot corrupt stored data."

---

## Lesson 5: Queries generated from method names

**Idea.** Spring Data reads a method name and builds the query.

**Code.**
```java
List<Account> findByOwnerId(UUID ownerId);
Optional<Account> findByIdAndOwnerId(UUID id, UUID ownerId);
```

| Part of the name | Meaning |
|---|---|
| `find` | SELECT |
| `By` | start of the WHERE clause |
| `Owner` then `Id` | `owner.id`, following the relationship |
| `And` | AND between conditions |

The second method becomes `SELECT ... WHERE id = ? AND owner_id = ?`. Its return type is `Optional`, because the row may not exist.

**Common mistake.** Making names too long. Past a few conditions, a `@Query` annotation with explicit SQL is clearer.

**Interview line.** "Derived query methods are generated from the method name, and I return Optional when a row may be absent."

---

## Lesson 6: Ownership and the 404 rule

**Idea.** Every read must check that the requested resource belongs to the caller. This class of bug is called IDOR (insecure direct object reference).

**The bad version.**
```java
Account account = accountRepository.findById(id).orElseThrow();   // any user can read any account
```

**The correct version.**
```java
accountRepository.findByIdAndOwnerId(accountId, owner.getId())
        .orElseThrow(() -> new AccountNotFoundException(accountId));
```

The check is part of the query. There is no window where the wrong account is loaded and then rejected.

**Why 404 and not 403?** A `403 Forbidden` tells the caller "this account exists, you cannot have it". A `404` tells them nothing. Both cases return the same response, so an attacker cannot probe for IDs.

**Common mistake.** Fetching by ID and then comparing owners in Java. It works, but it is easy to forget the check in one endpoint.

**Interview line.** "I enforce ownership inside the query itself and return 404 for accounts the caller does not own, so the API does not reveal whether other IDs exist."

---

## Lesson 7: Transactions and read-only work

**Idea.** A transaction groups several database operations so that they all succeed or all fail.

**Code.**
```java
@Transactional
public Account openAccount(String ownerEmail, AccountType type) { ... }

@Transactional(readOnly = true)
public List<Account> listAccounts(String ownerEmail) { ... }
```

`@Transactional` on the method wraps everything inside it in one database transaction. If an exception escapes the method, the transaction rolls back. `readOnly = true` tells the database and Hibernate that nothing will be written, which allows some optimizations.

**Why it matters later.** A transfer updates two balances. Both updates must happen together, or neither may happen.

**Common mistake.** Putting `@Transactional` on a private method. Spring proxies cannot intercept private methods, so the annotation has no effect.

**Interview line.** "Service methods that change data run in a transaction and roll back on failure. Read-only methods are marked readOnly."

---

## Lesson 8: Random account numbers

**Idea.** The account number is something a person might see and type. It must be hard to guess, and it must be unique.

**Code.**
```java
private static final SecureRandom RANDOM = new SecureRandom();

private String generateAccountNumber() {
    long number = 1_000_000_000L + Math.floorMod(RANDOM.nextLong(), 9_000_000_000L);
    return String.valueOf(number);
}
```

- `SecureRandom` is cryptographically strong. `java.util.Random` is predictable.
- `Math.floorMod` never returns a negative value, so the number is always 10 digits.
- Uniqueness is enforced by the database `UNIQUE` constraint.

**Known gap.** If a collision happens, the insert fails and the user sees a `500`. A retry loop would fix it, and is listed in the reference.

**Common mistake.** Using `Math.random()` or `new Random()` for anything security-related.

**Interview line.** "Account numbers come from SecureRandom and the database enforces uniqueness. A collision is rare, and I know it is not yet retried."

---

## Lesson 9: Safe output with a DTO

**Idea.** The API returns `AccountResponse`, not the `Account` entity.

**Code.**
```java
public record AccountResponse(UUID id, String accountNumber, AccountType type,
                              BigDecimal balance, Instant createdAt) {
    public static AccountResponse from(Account account) { ... }
}
```

The `owner` field is absent on purpose. Serializing the entity would include the full `User`, including `passwordHash`.

**Common mistake.** Returning the entity and adding `@JsonIgnore` on individual fields. One missed annotation leaks data.

**Interview line.** "Responses are DTOs that list fields explicitly, so an entity change cannot accidentally expose new data."

---

## What is intentionally not built yet

- Deposits, withdrawals, and transfers.
- Concurrency control for balances (`@Version` or pessimistic locks).
- A JSON response for account creation.
- Lazy loading of the owner.

These come in the next phase, with their own learning notes.
