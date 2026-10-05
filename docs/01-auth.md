# Auth Module: Technical Reference

**Status:** Done and tested end to end.
**Packages:** `auth`, `user`, `config`, `common`

This document describes what the Auth module does, how its parts connect, and why each design decision was made. For a step-by-step explanation intended for learning, see [learning/01-auth.md](../learning/01-auth.md).

---

## 1. Responsibilities

- Register a user with an email, a password, and a full name.
- Store only a BCrypt hash of the password, never the raw password.
- Log a user in and issue a signed JSON Web Token (JWT) valid for one hour.
- Require a valid bearer token on every endpoint except the public ones.
- Return all errors as RFC 9457 `application/problem+json` responses.

## 2. Endpoints

| Method | Path | Access | Success | Failure cases |
|---|---|---|---|---|
| POST | `/auth/register` | Public | `201 Created`, body is `UserResponse` | `400` invalid input, `409` email already used |
| POST | `/auth/login` | Public | `200 OK`, body is `LoginResponse` | `400` invalid input, `401` wrong email or password |
| GET | `/auth/me` | Bearer token | `200 OK`, text with the subject email | `401` missing, malformed, or expired token |
| GET | `/actuator/health` | Public | `200 OK` | none |

## 3. Data model

```mermaid
erDiagram
    APP_USER {
        UUID id PK
        VARCHAR email UK "unique, not null"
        VARCHAR password_hash "BCrypt hash, not null"
        VARCHAR full_name "not null"
        VARCHAR role "CHECK in CUSTOMER, ADMIN"
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
    AC -. "throws to" .-> GEH
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
    AC->>AC: validate @NotBlank @Email @Size
    AC->>AS: register(request)
    AS->>UR: existsByEmail(email)
    UR->>DB: SELECT ... WHERE email = ?
    DB-->>UR: false
    AS->>PE: encode(password)
    PE-->>AS: bcrypt hash
    AS->>UR: save(User with role CUSTOMER)
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
        AS->>PE: matches(raw, storedHash)
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

Both failure branches return the same error message. This prevents user enumeration.

### 5.3 Calling a protected endpoint

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SF as SecurityFilterChain
    participant JD as JwtDecoder
    participant AC as AuthController

    C->>SF: GET /auth/me with Authorization Bearer token
    SF->>JD: verify signature and expiry
    alt token invalid
        JD-->>SF: failure
        SF-->>C: 401 with WWW-Authenticate error
    else token valid
        JD-->>SF: Jwt (subject, issuer, exp)
        SF->>AC: request with Jwt principal
        AC-->>C: 200 OK
    end
```

## 6. Error handling

| Exception | Handler | HTTP status | Source |
|---|---|---|---|
| `EmailAlreadyUsedException` | `GlobalExceptionHandler` | 409 | `AuthService.register` |
| `InvalidCredentialsException` | `GlobalExceptionHandler` | 401 | `AuthService.login` |
| `MethodArgumentNotValidException` | `GlobalExceptionHandler` | 400 | request validation |

**Why a global handler is needed.** An exception that no handler catches is forwarded by Spring Boot to `/error`. The `/error` path is not on the public list, so the security filter rejects that internal request with a misleading `403`. The global handler catches the exception first, so the forward never happens.

## 7. Configuration

```yaml
security:
  jwt:
    secret-key: ${JWT_SECRET_KEY:...local development value...}
```

- The key is read by `JwtProperties` through `@ConfigurationProperties`.
- HS256 requires at least 32 bytes. Production must set `JWT_SECRET_KEY` from the environment.
- The default in the file is for local development only.

## 8. Token format

| Claim | Value |
|---|---|
| `iss` | `ledgerlite` |
| `sub` | user email |
| `iat` | issue time |
| `exp` | issue time plus 1 hour |
| header `alg` | `HS256` |

## 9. Design decisions

| Decision | Reason |
|---|---|
| UUID primary keys | IDs cannot be guessed or enumerated |
| BCrypt through `DelegatingPasswordEncoder` | Standard one-way hash. The `{bcrypt}` prefix allows a future algorithm change |
| Role set on the server | The request body has no `role` field, so a client cannot register as ADMIN |
| Same message for unknown email and wrong password | Prevents user enumeration |
| Stateless JWT, CSRF disabled | No cookies or sessions, so CSRF does not apply |
| Spring's built-in Nimbus JWT support | One fewer third-party dependency |
| Flyway with `ddl-auto: none` | Schema changes are versioned and reviewed like code |
| DTOs for input and output | The entity, including `passwordHash`, is never serialized |
| `ProblemDetail` (RFC 9457) | One error format across the whole API |

## 10. Known limits

- Access tokens cannot be revoked before they expire. A refresh-token flow would be needed for that.
- The JWT secret in the repository is for local development only.
