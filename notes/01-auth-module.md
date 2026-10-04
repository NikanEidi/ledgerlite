# Part 1 — Auth Module (Register, Login, JWT)

**Status:** ✅ Complete and tested end-to-end.
**Packages:** `dev.nikan.ledgerlite.auth`, `dev.nikan.ledgerlite.user`, `dev.nikan.ledgerlite.config`, `dev.nikan.ledgerlite.common`

## 1. What this module does

- Lets a new user register with an email, password, and full name.
- Hashes the password with BCrypt before it ever touches the database.
- Lets a registered user log in and receive a short-lived JWT.
- Protects every endpoint except `/auth/register`, `/auth/login`, and `/actuator/health` — any other request needs a valid `Authorization: Bearer <token>` header.
- Returns clean, standardized errors (RFC 9457 `ProblemDetail`) instead of leaking stack traces or generic 500s.

## 2. ERD — Database schema

Only one table exists so far (`V1__create_app_user.sql`). It will grow as Accounts, Transfers, and Loans are built.

```mermaid
erDiagram
    APP_USER {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR full_name
        VARCHAR role
        TIMESTAMPTZ created_at
    }
```

Notes:
- `id` is a UUID (not a sequential integer) so account IDs are never guessable.
- `email` has a `UNIQUE` constraint — enforced by the database itself, not only by application code.
- `role` has a `CHECK (role IN ('CUSTOMER', 'ADMIN'))` constraint — same idea, a second line of defense.
- `password_hash` never stores a raw password — only a BCrypt hash (`{bcrypt}$2a$10$...`).

## 3. Class diagram

This reflects the real classes and their actual dependencies (constructor injection), not an idealized version.

```mermaid
classDiagram
    class User {
        -UUID id
        -String email
        -String passwordHash
        -String fullName
        -Role role
        -Instant createdAt
        +getId() UUID
        +getEmail() String
        +getPasswordHash() String
        +getFullName() String
        +getRole() Role
        +getCreatedAt() Instant
    }

    class Role {
        <<enumeration>>
        CUSTOMER
        ADMIN
    }

    class UserRepository {
        <<interface>>
        +findByEmail(String) Optional~User~
        +existsByEmail(String) boolean
    }

    class RegisterRequest {
        <<record>>
        +String email
        +String password
        +String fullName
    }

    class LoginRequest {
        <<record>>
        +String email
        +String password
    }

    class UserResponse {
        <<record>>
        +UUID id
        +String email
        +String fullName
        +Role role
        +Instant createdAt
        +from(User)$ UserResponse
    }

    class LoginResponse {
        <<record>>
        +String accessToken
        +String tokenType
        +of(String)$ LoginResponse
    }

    class AuthService {
        -UserRepository userRepository
        -PasswordEncoder passwordEncoder
        -JwtService jwtService
        +register(RegisterRequest) User
        +login(LoginRequest) LoginResponse
    }

    class AuthController {
        -AuthService authService
        +register(RegisterRequest) ResponseEntity~UserResponse~
        +login(LoginRequest) ResponseEntity~LoginResponse~
        +me(Jwt) ResponseEntity~String~
    }

    class JwtService {
        -JwtEncoder jwtEncoder
        -JwtDecoder jwtDecoder
        +generateToken(String) String
        +decodeToken(String) Jwt
    }

    class SecurityConfig {
        +passwordEncoder() PasswordEncoder
        +jwtSecretKey(JwtProperties) SecretKey
        +jwtEncoder(SecretKey) JwtEncoder
        +jwtDecoder(SecretKey) JwtDecoder
        +securityFilterChain(HttpSecurity) SecurityFilterChain
    }

    class JwtProperties {
        -String secretKey
        +getSecretKey() String
        +setSecretKey(String)
    }

    class EmailAlreadyUsedException
    class InvalidCredentialsException

    class GlobalExceptionHandler {
        +handleEmailAlreadyUsed(EmailAlreadyUsedException) ProblemDetail
        +handleInvalidCredentials(InvalidCredentialsException) ProblemDetail
    }

    User "1" --> "1" Role : has
    UserRepository ..> User : manages
    AuthService --> UserRepository : uses
    AuthService --> JwtService : uses
    AuthService ..> RegisterRequest : reads
    AuthService ..> LoginRequest : reads
    AuthService ..> User : creates
    AuthService ..> LoginResponse : builds
    AuthService ..> EmailAlreadyUsedException : throws
    AuthService ..> InvalidCredentialsException : throws
    AuthController --> AuthService : uses
    AuthController ..> UserResponse : builds
    JwtService --> SecurityConfig : keys/beans from
    SecurityConfig --> JwtProperties : reads secret from
    GlobalExceptionHandler ..> EmailAlreadyUsedException : catches
    GlobalExceptionHandler ..> InvalidCredentialsException : catches
```

## 4. Sequence diagram — Register

```mermaid
sequenceDiagram
    participant C as Client
    participant SEC as SecurityFilterChain
    participant AC as AuthController
    participant AS as AuthService
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant DB as PostgreSQL

    C->>SEC: POST /auth/register (JSON body)
    SEC->>SEC: path is permitAll -> pass through
    SEC->>AC: forward request
    AC->>AC: validate RegisterRequest (@NotBlank, @Email, @Size)
    AC->>AS: register(request)
    AS->>UR: existsByEmail(email)
    UR->>DB: SELECT ... WHERE email = ?
    DB-->>UR: false
    UR-->>AS: false
    AS->>PE: encode(rawPassword)
    PE-->>AS: bcryptHash
    AS->>UR: save(new User(...))
    UR->>DB: INSERT INTO app_user (...)
    DB-->>UR: saved row (with generated id)
    UR-->>AS: User
    AS-->>AC: User
    AC-->>C: 201 Created + UserResponse (no password)
```

## 5. Sequence diagram — Login

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthService
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant JS as JwtService

    C->>AC: POST /auth/login (email, password)
    AC->>AS: login(request)
    AS->>UR: findByEmail(email)
    alt user not found
        UR-->>AS: empty
        AS-->>AC: throw InvalidCredentialsException
    else user found
        UR-->>AS: User
        AS->>PE: matches(rawPassword, user.passwordHash)
        alt password does not match
            PE-->>AS: false
            AS-->>AC: throw InvalidCredentialsException
        else password matches
            PE-->>AS: true
            AS->>JS: generateToken(user.email)
            JS-->>AS: signed JWT string
            AS-->>AC: LoginResponse(accessToken, "Bearer")
            AC-->>C: 200 OK + { accessToken, tokenType }
        end
    end
```

Note: whether the email doesn't exist or the password is wrong, the client gets the exact same error message ("Invalid email or password") — this prevents an attacker from figuring out which emails are registered (a "user enumeration" attack).

## 6. Sequence diagram — Calling a protected endpoint (`GET /auth/me`)

```mermaid
sequenceDiagram
    participant C as Client
    participant SEC as SecurityFilterChain
    participant JD as JwtDecoder
    participant AC as AuthController

    C->>SEC: GET /auth/me (Authorization: Bearer <token>)
    SEC->>JD: decode & verify signature + expiry

    alt no token / malformed / bad signature
        JD-->>SEC: invalid
        SEC-->>C: 401 Unauthorized (RFC 6750 error detail)
    else token valid
        JD-->>SEC: Jwt (claims: sub, iss, iat, exp)
        SEC->>AC: forward request, inject Jwt as principal
        AC-->>C: 200 OK "You are authenticated as: <email>"
    end
```

## 7. Error handling flow

Any `RuntimeException` thrown by a controller method is intercepted by `GlobalExceptionHandler` (`@RestControllerAdvice`) before Spring Boot's default `/error` machinery ever sees it, and turned into an RFC 9457 `application/problem+json` response:

| Exception | HTTP status | When |
|---|---|---|
| `EmailAlreadyUsedException` | 409 Conflict | Registering with an email that's already taken |
| `InvalidCredentialsException` | 401 Unauthorized | Login with an unknown email or a wrong password |

**A real gotcha we hit:** before `GlobalExceptionHandler` existed, an uncaught exception was silently forwarded by Spring Boot to `/error` — but `/error` wasn't in the security allowlist, so Spring Security rejected that internal forward with a confusing bare `403 Forbidden`, hiding the real problem. Catching exceptions ourselves, before they ever reach `/error`, fixed it.

## 8. Key design decisions (and why)

| Decision | Why |
|---|---|
| UUID primary keys | Unguessable — an attacker can't enumerate users by incrementing an ID |
| BCrypt via `DelegatingPasswordEncoder` | Industry-standard one-way hashing; the `{bcrypt}` prefix allows upgrading algorithms later without breaking existing users |
| Role hardcoded to `CUSTOMER` server-side | The client can never register itself as `ADMIN` — `RegisterRequest` doesn't even have a `role` field |
| Same error for "email not found" and "wrong password" | Prevents user enumeration attacks |
| JWT via Spring Security's built-in `JwtEncoder`/`JwtDecoder` (Nimbus) | No extra JWT library needed — one less dependency to secure and maintain |
| Stateless JWT auth, CSRF disabled | CSRF protects cookie/session-based apps; a stateless bearer-token API isn't vulnerable to it |
| Flyway for schema, `ddl-auto: none` | The schema is versioned and reviewable like code — Hibernate never silently changes it |
| DTOs (`RegisterRequest`, `UserResponse`, ...) instead of exposing `User` directly | Full control over what the API accepts and returns; `passwordHash` can never leak |
| `@RestControllerAdvice` + `ProblemDetail` (RFC 9457) | One consistent, standard error format across the whole API |
