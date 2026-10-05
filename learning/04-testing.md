# Learning Notes 4: Testing Money Code

These notes explain how the tests in LedgerLite are organized, why they are split into two kinds, and how to read them. Money code is the place where a bug costs the most, so testing it well matters more than in most projects.

The technical reference for the test suite is in [docs/04-testing.md](../docs/04-testing.md).

## Lesson map

| Lesson | Concept | Where it appears |
|---|---|---|
| 1 | Unit tests and integration tests | `AccountTest`, `AccountFlowIntegrationTest` |
| 2 | Test one behavior per test method | every test class |
| 3 | Readable assertions | AssertJ `assertThat` |
| 4 | Comparing money correctly | `isEqualByComparingTo` |
| 5 | Parameterized tests | `LoanStatusTest` |
| 6 | Real database in tests | Testcontainers, `TestcontainersConfiguration` |
| 7 | Testing HTTP without a server | `MockMvc` |
| 8 | Testing the rule that matters most | idempotent transfer test |

---

## Lesson 1: Unit tests and integration tests

**Idea.** Tests come in two sizes:
- A **unit test** checks one class, with no Spring and no database. It runs in milliseconds.
- An **integration test** checks several parts together, usually with the real framework and a real database. It runs in seconds.

**Why both.** Unit tests catch logic errors fast and point to the exact line. Integration tests catch wiring errors: a wrong SQL type, a missing bean, a broken security rule.

```
            Integration tests (6)      slow, realistic
          Unit tests (8)               fast, precise
```

**Interview line.** "I keep most logic checks as fast unit tests, and I use integration tests with a real PostgreSQL to check the wiring and the full request flow."

---

## Lesson 2: One behavior per test

**Idea.** A test name should say what behavior is being checked, and the test should check only that behavior.

**Code.**
```java
@Test
void debitRejectsAmountGreaterThanBalance() { ... }

@Test
void debitDecreasesBalanceWhenFundsAreEnough() { ... }
```

When the first test fails, its name already tells you the rule that broke.

**Common mistake.** One test called `testAccount` that checks twelve things. When it fails, you do not know which one.

**Interview line.** "Each test checks one behavior and its name describes it, so a failure tells you exactly what broke."

---

## Lesson 3: Readable assertions

**Code.**
```java
assertThat(account.getBalance()).isEqualByComparingTo("60.00");
assertThatThrownBy(() -> account.debit(new BigDecimal("50.01")))
        .isInstanceOf(InsufficientFundsException.class);
```

AssertJ reads like a sentence: "assert that the balance is equal to 60." When it fails, the message shows the actual and expected values.

**Interview line.** "I use AssertJ, because its assertions read like sentences and produce clear failure messages."

---

## Lesson 4: Comparing money correctly

**Idea.** In `BigDecimal`, `100.00` and `100.0000` are different objects with different scale. `equals` says they differ, even though the value is the same.

**Code.**
```java
assertThat(account.getBalance()).isEqualByComparingTo("100.00");   // correct
// assertThat(account.getBalance()).isEqualTo(new BigDecimal("100.00"));  // fails if the scale is 4
```

`isEqualByComparingTo` compares the numeric value only.

**Why this matters for the tests.** The database returns `100.0000`, while the request had `100.00`. A test with `isEqualTo` would fail for a formatting reason, not a logic error.

**Interview line.** "For money I compare BigDecimal values numerically, because scale differences are a formatting detail, not a difference in amount."

---

## Lesson 5: Parameterized tests

**Idea.** When the same check should run for several inputs, write it once and feed it the inputs.

**Code.**
```java
@ParameterizedTest
@EnumSource(value = LoanStatus.class, names = {"APPROVED", "REJECTED"})
void finalStatusesCannotMoveAnywhere(LoanStatus finalStatus) {
    for (LoanStatus next : LoanStatus.values()) {
        assertThat(finalStatus.canTransitionTo(next)).isFalse();
    }
}
```

The test runs once for `APPROVED` and once for `REJECTED`, and each run checks every possible target. The test name in the report shows which status failed.

**Interview line.** "For rules that apply to several values, I use parameterized tests, so the rule is written once and each value is reported separately."

---

## Lesson 6: A real database in tests

**Idea.** Some bugs only appear with a real database: a wrong column type, a missing index, a `CHECK` that rejects valid data, a lock that does not work.

**Code.**
```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
}
```

Testcontainers starts a real PostgreSQL 17 in Docker for the test run. `@ServiceConnection` points Spring at that container, so the application uses it without any extra configuration.

**Why the version is pinned.** `postgres:latest` changes over time. Pinning `17` means the tests behave the same today and next year.

**Common mistake.** Testing money logic only against an in-memory fake database. Such a database may accept things that PostgreSQL rejects.

**Interview line.** "My integration tests run against a real PostgreSQL 17 started by Testcontainers, so database constraints and migrations are tested the same way production uses them."

---

## Lesson 7: Testing HTTP without a server

**Idea.** `MockMvc` sends a request through the real controllers and the real security filter chain, without opening a network port.

**Code.**
```java
mvc.perform(post("/accounts/" + sourceId + "/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toAccountNumber\":\"" + targetNumber + "\",\"amount\":100.00}"))
        .andExpect(status().isCreated());
```

The request goes through the JWT check, the validation, the controller, the service, the database, and back. Only the network is skipped.

**Interview line.** "I test endpoints with MockMvc, so each request passes through the security chain and the real database without starting a server."

---

## Lesson 8: Testing the rule that matters most

**Idea.** Some behaviors are worth more tests than others. In a bank, the most important ones are: money is not created or lost, and a retry does not charge twice.

**The idempotency test.**
```java
String firstTransferId = transfer(token, sourceId, targetNumber, "100.00", key);
String replayedTransferId = transfer(token, sourceId, targetNumber, "100.00", key);

assertThat(replayedTransferId).isEqualTo(firstTransferId);
assertThat(balance(token, sourceId)).isEqualByComparingTo("400.00");
assertThat(balance(token, targetId)).isEqualByComparingTo("100.00");
```

The same request is sent twice. The test checks three things: the same transfer is returned, the source is charged once, and the target is credited once.

**Why this test is the most important.** It checks the promise the whole transfer design makes. If this test breaks, a customer could pay twice.

**Interview line.** "The most important test checks that a retried transfer with the same idempotency key returns the same transfer and does not move money a second time."

---

## Lesson 9: What the tests do not cover yet

Honest testing means knowing the gaps.

| Gap | Why it matters |
|---|---|
| No concurrency test | Locks are not proven by parallel requests |
| No loan integration test | Loan endpoints were checked by hand only |
| No validation-error test | Bad input responses are not asserted in code |
| No test for a reused key with a different body | A known weakness in the idempotency logic |

**Interview line.** "I can list what my tests do not cover yet, and I have a plan to add concurrency tests for the locking code."

---

## Summary

| Question | Answer in this project |
|---|---|
| Where are the fast checks? | `AccountTest`, `LoanStatusTest` (no Docker needed) |
| Where are the realistic checks? | `AccountFlowIntegrationTest` with PostgreSQL 17 |
| Which test matters most? | the idempotent transfer test |
| How is the database provided? | Testcontainers with `@ServiceConnection` |
| How is HTTP tested? | `MockMvc`, through the real security chain |
