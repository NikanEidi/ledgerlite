# Learning Notes 1: Authentication

These notes explain the Auth module one concept at a time. Each lesson gives the idea, the reason for it, the code that applies it, a common mistake, and a sentence you can use in an interview.

The technical reference for the same module is in [docs/01-auth.md](../docs/01-auth.md).

## Lesson map

| Lesson | Concept | Where it appears |
|---|---|---|
| 1 | Layers and dependency injection | `AuthController`, `AuthService` |
| 2 | Request and response objects (DTOs and records) | `RegisterRequest`, `UserResponse`, `LoginResponse` |
| 3 | Validation before business logic | `@Valid`, `@NotBlank`, `@Email`, `@Size` |
| 4 | Password hashing | `PasswordEncoder` bean in `SecurityConfig` |
| 5 | Entities and repositories | `User`, `UserRepository` |
| 6 | Tokens (JWT) | `JwtService`, `JwtProperties` |
| 7 | The security filter chain | `SecurityConfig.securityFilterChain` |
| 8 | Standard error responses | `GlobalExceptionHandler`, `ProblemDetail` |

---

## Lesson 1: Layers and dependency injection

**Idea.** Split the code by responsibility. The controller translates HTTP into method calls. The service makes the decisions. The repository reads and writes the database.

**Why.** Each layer can change without touching the others. The service can be tested without HTTP or a database.

**Code.**
```java
public AuthService(UserRepository userRepository,
                   PasswordEncoder passwordEncoder,
                   JwtService jwtService) { ... }
```

The service declares what it needs. It does not create those objects with `new`. Spring creates them and passes them in. This is dependency injection (DI), and it is how inversion of control (IoC) is implemented in Spring.

**Common mistake.** Writing `new UserRepository()` inside a service. That bypasses Spring, so the object is not managed and cannot be replaced in tests.

**Interview line.** "Services receive their dependencies through constructor injection, so each class declares what it needs and tests can substitute fakes."

---

## Lesson 2: Request and response objects (DTOs and records)

**Idea.** The shape of data entering or leaving the API is separate from the database entity.

**Why.** The entity `User` contains `passwordHash`. If we returned it directly, the hash would leak. A DTO lists exactly the fields that are safe to send.

**Code.**
```java
public record UserResponse(UUID id, String email, String fullName, Role role, Instant createdAt) {
    public static UserResponse from(User user) { ... }
}
```

A `record` is a class for fixed data. The compiler generates the constructor, accessors (`email()`, not `getEmail()`), `equals`, `hashCode`, and `toString`. All fields are `final`.

**Common mistake.** Returning the entity from a controller because it "already has the fields". This exposes internal fields and couples the API to the database schema.

**Interview line.** "I never expose entities over the API. Input and output DTOs are records, so the API contract is explicit and immutable."

---

## Lesson 3: Validation before business logic

**Idea.** Check the shape of the input at the boundary, before any business rule runs.

**Why.** Invalid input should fail fast with a clear message. The service should only receive data that already passed basic checks.

**Code.**
```java
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String fullName) {}
```

and in the controller:
```java
public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request)
```

`@Valid` triggers the checks. If one fails, Spring throws `MethodArgumentNotValidException` before the method body runs.

**Why 72?** BCrypt ignores everything after 72 bytes, so a longer password would give a false sense of security.

**Common mistake.** Forgetting `@Valid`. The annotations on the record are then ignored silently.

**Interview line.** "Input is validated at the edge with Bean Validation, so the service only handles data that already has the right shape."

---

## Lesson 4: Password hashing

**Idea.** Store a one-way hash of the password, never the password itself.

**Why.** If the database leaks, the attacker gets hashes, not passwords. A one-way function cannot be reversed.

**Code.**
```java
String hashed = passwordEncoder.encode(request.password());   // on register
boolean ok = passwordEncoder.matches(raw, user.getPasswordHash());  // on login
```

We never compare with `equals`. A hash is salted and different every time, so `matches` re-hashes the raw password with the stored salt and compares.

`DelegatingPasswordEncoder` stores hashes with a prefix such as `{bcrypt}`. That lets us move to a stronger algorithm later without breaking existing users.

**Common mistake.** Logging the raw password or the hash. Neither should ever appear in logs.

**Interview line.** "Passwords are hashed with BCrypt through DelegatingPasswordEncoder. Verification uses matches, and the algorithm can be upgraded later because the hash carries its own prefix."

---

## Lesson 5: Entities and repositories

**Idea.** An entity is a Java class mapped to a table. A repository is an interface that Spring implements for database access.

**Why.** You work with objects, and the framework generates the SQL.

**Code.**
```java
@Entity
@Table(name = "app_user")
public class User { @Id ... private UUID id; @Column(nullable = false, unique = true) private String email; ... }

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
```

`JpaRepository` provides `save`, `findById`, and `delete` without code. The method `findByEmail` is implemented by Spring from its name.

**Why no setters on `User`?** Once created, the object is only changed through its constructor. Hibernate sets the fields through reflection when it loads a row, so a protected no-argument constructor is required.

**Common mistake.** Forgetting the no-argument constructor. Hibernate cannot create the object and fails at startup.

**Interview line.** "I use Spring Data JPA repositories. Derived query methods are generated from their names, and entities are immutable after construction."

---

## Lesson 6: Tokens (JWT)

**Idea.** After login, the server returns a signed token. The client sends it back with each request, and the server checks the signature instead of looking up a session.

**Why.** The server stores nothing per user, so it scales across many instances. This is called stateless authentication.

**Structure.** A JWT has three parts separated by dots: a header (algorithm), claims (data about the user and times), and a signature.

**Code.**
```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("ledgerlite")
        .issuedAt(now)
        .expiresAt(now.plus(1, ChronoUnit.HOURS))
        .subject(subjectEmail)
        .build();
```

HS256 is symmetric: the same secret signs and verifies. This is simple but means every service that verifies tokens must hold the secret.

**Common mistake.** Putting secrets or passwords inside claims. Claims are only signed, not encrypted, so anyone can read them.

**Interview line.** "Login issues an HS256 JWT with a one-hour expiry. The token is stateless, and the subject carries the user's email."

---

## Lesson 7: The security filter chain

**Idea.** Every request passes through a chain of filters before it reaches a controller. The filter chain decides which requests are allowed.

**Why.** Security rules live in one place, not in every controller.

**Code.**
```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/register", "/auth/login", "/actuator/health").permitAll()
        .anyRequest().authenticated())
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
```

Rules are checked from top to bottom. The first match wins, so the catch-all `anyRequest()` must come last.

**Why CSRF is disabled.** CSRF attacks exploit cookies that browsers send automatically. Our API uses an explicit bearer header, so that attack does not apply.

**Common mistake.** Using a wildcard such as `/auth/**` as a public path. Then `/auth/me` would also be public.

**Interview line.** "Access rules are declared in one SecurityFilterChain. The API is stateless, so CSRF is disabled and the resource server validates bearer tokens on every protected request."

---

## Lesson 8: Standard error responses

**Idea.** Every error should have the same shape, and the status code should describe what went wrong.

**Why.** Clients can handle errors predictably. Stack traces and internal details should never reach the client.

**Code.**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handle(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }
}
```

`ProblemDetail` is Spring's implementation of RFC 9457. The response uses `Content-Type: application/problem+json`.

**Why this was needed.** Without a handler, an exception is forwarded to `/error`, which is not public. The security filter then returns `403`, which hides the real error. A handler catches the exception before that forward happens.

**Common mistake.** Returning `500` for every exception. That tells the client nothing useful and hides validation problems.

**Interview line.** "Errors are mapped centrally in a RestControllerAdvice to RFC 9457 problem details, so every endpoint returns the same error format."
