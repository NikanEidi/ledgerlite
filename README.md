# LedgerLite

A digital-bank core built as a REST API with Java 21 and Spring Boot 4. The design follows how a bank's backend is usually structured: a layered architecture, hashed credentials, token-based authentication, versioned database migrations, row-level ownership checks, and standard error responses.

This is a personal project built step by step to practice Java and Spring Boot for backend roles in banking and fintech. Every feature listed as done is implemented and tested.

## Status

| Module | Status |
|---|---|
| Auth: register, login, JWT-protected endpoints | Done |
| Accounts: open, list, read one (with ownership check) | Done |
| Accounts: deposit, withdraw, balance history | Planned |
| Transfers between accounts | Planned |
| Loan applications with a partner credit-score client | Planned |

## Documentation

- [Technical reference](docs/README.md): architecture, schema, endpoints, sequence diagrams, and design decisions for each module.
- [Learning notes](learning/README.md): one concept per lesson, with the reason, the code, and common mistakes.

## Tech stack

- Java 21, Spring Boot 4.1.1, Maven
- Spring Web MVC for REST controllers
- Spring Data JPA with Hibernate 7 for persistence
- Spring Security with the built-in Nimbus JWT support (no extra JWT library)
- PostgreSQL 17 with Flyway for versioned migrations
- Jakarta Bean Validation for request validation
- Docker Compose for the local database
- Spring Boot Actuator for health checks

## Project structure

```
src/main/java/dev/nikan/ledgerlite/
  auth/       register, login, JWT service, auth DTOs and exceptions
  account/    Account entity, AccountType, repository, service, controller, DTOs
  user/       User entity, Role, UserRepository
  config/     SecurityConfig (JWT beans, password encoder, filter chain), JwtProperties
  common/     GlobalExceptionHandler (RFC 9457 ProblemDetail responses)
src/main/resources/
  application.yaml
  db/migration/
    V1__create_app_user.sql
    V2__create_account.sql
docs/         technical reference per module
learning/     learning notes per module
```

Every request follows the same path through three layers:

```
Controller  ->  Service  ->  Repository  ->  PostgreSQL
(HTTP)          (rules)      (data access)
```

## Security

- Passwords are hashed with BCrypt and never stored or logged in plain text.
- Authentication is stateless: an HS256 JWT valid for one hour, with no server-side sessions.
- A new user's role is always set to `CUSTOMER` on the server. The registration request has no role field.
- Login returns the same error for an unknown email and a wrong password.
- Account ownership is enforced inside the database query. Another user's account returns `404`, the same as a missing account.
- The database enforces its own rules as well: unique emails, unique account numbers, valid roles and account types, non-negative balances, and foreign keys.
- Errors use the standard RFC 9457 format and never include stack traces.

## Running locally

Requirements: Java 21, Docker. The Maven wrapper (`./mvnw`) is included.

```bash
# 1. Start PostgreSQL (mapped to host port 5433)
docker compose up -d

# 2. Start the application. Flyway runs the migrations on startup.
./mvnw spring-boot:run
```

The application runs on `http://localhost:8080`.

## API

All endpoints except the ones marked public require the header `Authorization: Bearer <token>`.

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Create a user |
| POST | `/auth/login` | Public | Receive an access token |
| GET | `/auth/me` | Token | Return the authenticated email |
| POST | `/accounts` | Token | Open a `CHECKING` or `SAVINGS` account |
| GET | `/accounts` | Token | List the authenticated user's accounts |
| GET | `/accounts/{id}` | Token | Read one account owned by the user |
| GET | `/actuator/health` | Public | Health check |

### Example

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"nikan@test.com","password":"password123","fullName":"Nikan Eidi"}'

# Log in and store the token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"nikan@test.com","password":"password123"}' \
  | python3 -c "import sys, json; print(json.load(sys.stdin)['accessToken'])")

# Open a checking account
curl -X POST http://localhost:8080/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"CHECKING"}'

# List accounts
curl http://localhost:8080/accounts -H "Authorization: Bearer $TOKEN"
```

## Author

Nikan Eidi
[github.com/NikanEidi](https://github.com/NikanEidi) · [nikanvision.dev](https://nikanvision.dev) · [LinkedIn](https://www.linkedin.com/in/nikan-eidi-03476232b)
