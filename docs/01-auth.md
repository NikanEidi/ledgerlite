# Auth Module: Technical Reference

**Status:** Done and covered by tests.
**Packages:** `auth`, `user`, `config`, `common`

This document describes how authentication works in LedgerLite: registration, login, token issuance, and how protected endpoints check the token. For a step-by-step explanation of the same code, see [learning/01-auth.md](../learning/01-auth.md).

---

## 1. Responsibilities

- Register a user with an email, a password, and a full name.
- Store only a BCrypt hash of the password, never the raw password.
- Log a user in and issue a signed JSON Web Token (JWT) that is valid for one hour.
- Require a valid bearer token on every endpoint except the public ones.
- Return every error in the RFC 9457 `application/problem+json` format.

## 2. Endpoints

| Method | Path | Access | Success | Failure cases |
|---|---|---|---|---|
| POST | `/auth/register` | Public | `201 Created`, body is `UserResponse` | `400` invalid input, `409` email already used |
| POST | `/auth/login` | Public | `200 OK`, body is `LoginResponse` | `400` invalid input, `401` wrong email or password |
| GET | `/auth/me` | Bearer token | `200 OK`, plain text with the subject email | `401` missing, malformed, or expired token |
| GET | `/actuator/health` | Public | `200 OK` | none |

## 3. Data model

```mermaid
erDiagram
    APP_USER {
        UUID id PK
        VARCHAR email UK "unique, not null"
        VARCHAR password_hash "BCrypt hash, not null"
        VARCHAR full_name "not null"
        VARCHAR role "CHECK: CUSTOMER or ADMIN"
        TIMESTAMPTZ created_at "default now()"
    }
```

Migration: `src/main/resources/db/migration/V1__create_app_user.sql`.

## 4. Component structure

```mermaid
flowchart TB
    subgraph web["Web layer"]
        AC["AuthController"]
        GEH["GlobalExceptionHandler"]
    end
    subgraph service["Service layer"]
        AS["AuthService"]
        JS["JwtService"]
    end
    subgraph data["Data layer"]
        UR["UserRepository"]
        U["User entity"]
    end
    subgraph config["Configuration"]
        SC["SecurityConfig<br/>beans and filter chain"]
        JP["JwtProperties"]
    end

    AC --> AS
    AS --> UR
    AS --> JS
    AS -. "PasswordEncoder bean" .-> SC
    JS -. "JwtEncoder and JwtDecoder beans" .-> SC
    SC --> JP
    UR --> U
    AC -. "exceptions are handled by" .-> GEH
```

Solid arrows are direct dependencies (constructor injection). Dashed arrows are beans supplied by Spring.

## 5. Sequence diagrams

### 5.1 Register

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant AC as AuthController
    participant AS as AuthService
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant DB as PostgreSQL

    C->>AC: POST /auth/register {email, password, fullName}
    AC->>AC: validate with @NotBlank, @Email, @Size
    AC->>AS: register(request)
    AS->>UR: existsByEmail(email)
    UR->>DB: SELECT ... WHERE email = ?
    DB-->>UR: false
    AS->>PE: encode(password)
    PE-->>AS: BCrypt hash
    AS->>UR: save(new User with role CUSTOMER)
    UR->>DB: INSERT INTO app_user
    AS-->>AC: User
    AC-->>C: 201 Created, UserResponse (no password)
```

### 5.2 Login

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant AC as AuthController
    participant AS as AuthService
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant JS as JwtService

    C->>AC: POST /auth/login {email, password}
    AC->>AS: login(request)
    AS->>UR: findByEmail(email)
    alt email not found
        AS-->>AC: InvalidCredentialsException
    else email found
        UR-->>AS: User
        AS->>PE: matches(raw password, stored hash)
        alt password wrong
            AS-->>AC: InvalidCredentialsException
        else password correct
            AS->>JS: generateToken(email)
            JS-->>AS: signed JWT
            AS-->>AC: LoginResponse
            AC-->>C: 200 OK, accessToken, tokenType Bearer
        end
    end
```

Both failure branches return the same message. This prevents user enumeration: an attacker cannot tell which emails are registered.

### 5.3 Calling a protected endpoint

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SF as SecurityFilterChain
    participant JD as JwtDecoder
    participant AC as Controller

    C->>SF: request with Authorization: Bearer token
    SF->>JD: verify signature and expiry
    alt token invalid or missing
        JD-->>SF: failure
        SF-->>C: 401 with WWW-Authenticate error
    else token valid
        JD-->>SF: Jwt (subject, issuer, exp)
        SF->>AC: request with Jwt as principal
        AC-->>C: 200 OK
    end
```

The controller never parses the token itself. It receives an already verified `Jwt` through `@AuthenticationPrincipal`, and uses `jwt.getSubject()` to know who is calling.

## 6. Error handling

| Exception | HTTP status | Raised when |
|---|---|---|
| `EmailAlreadyUsedException` | 409 Conflict | registering an email that already exists |
| `InvalidCredentialsException` | 401 Unauthorized | unknown email or wrong password |
| `MethodArgumentNotValidException` | 400 Bad Request | a request field fails validation |

**Why a global handler is needed.** When no handler catches an exception, Spring Boot forwards the request internally to `/error`. That path is not public, so the security filter rejects the forward with a misleading `403`. The global handler catches the exception first, so the forward never happens.

## 7. Configuration

```yaml
security:
  jwt:
    secret-key: ${JWT_SECRET_KEY:...local development value...}
```

- `JwtProperties` reads the key through `@ConfigurationProperties`.
- HS256 needs at least 32 bytes.
- The default value is for local development only. Production must set `JWT_SECRET_KEY` in the environment.

## 8. Token format

| Claim | Value |
|---|---|
| `iss` | `ledgerlite` |
| `sub` | user email |
| `iat` | issue time |
| `exp` | issue time plus one hour |
| header `alg` | `HS256` |

## 9. Design decisions

| Decision | Reason |
|---|---|
| UUID primary keys | IDs cannot be guessed or enumerated |
| BCrypt through `DelegatingPasswordEncoder` | Standard one-way hash. The `{bcrypt}` prefix allows a future algorithm change |
| Role set on the server | The request has no role field, so a client cannot register as ADMIN |
| Same message for unknown email and wrong password | Prevents user enumeration |
| Stateless JWT, CSRF disabled | No cookies or sessions, so CSRF does not apply |
| Spring's built-in Nimbus JWT support | One fewer third-party dependency |
| Flyway with `ddl-auto: none` | Schema changes are versioned and reviewed like code |
| DTOs for input and output | The entity, including `passwordHash`, is never serialized |
| RFC 9457 `ProblemDetail` | One error format across the whole API |

## 10. Scope and limitations

Features that are not implemented, and behaviors that are known to be incomplete, are listed in one place: [05-limitations-and-roadmap.md](05-limitations-and-roadmap.md), section 1.
