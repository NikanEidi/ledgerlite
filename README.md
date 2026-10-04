# LedgerLite

A digital-bank core built as a REST API in **Java 21 + Spring Boot 4**, designed the way a real bank's backend would be: layered architecture, hashed credentials, JWT-based auth, versioned database migrations, and standardized error responses.

This is a personal learning project, built step by step (and explained line by line) to go deep on Java + Spring Boot for backend roles at banks and fintechs. Every feature listed as done below is real, working code — not a plan.

## Status

| Module | Status |
|---|---|
| **Auth** — register, login, JWT-protected endpoints | ✅ Done |
| **Accounts** — open account, balance, transaction history | ⏳ Planned |
| **Transfers** — safe money transfer between accounts | ⏳ Planned |
| **Loan Applications** — application workflow + partner credit-score client | ⏳ Planned |

Full design notes (ERDs, class diagrams, sequence diagrams) live in [`notes/`](notes/README.md).

## Tech stack

- **Java 21**, **Spring Boot 4.1.1**, Maven
- **Spring Web MVC** — REST controllers
- **Spring Data JPA** + **Hibernate 7** — persistence
- **Spring Security** (built-in Nimbus JWT support — no extra JWT library) — authentication & authorization
- **PostgreSQL** + **Flyway** — versioned schema migrations
- **Jakarta Bean Validation** — request validation
- **Docker Compose** — local PostgreSQL
- **Spring Boot Actuator** — health checks

## Architecture

Layered, organized by feature (not by technical type):

```
dev.nikan.ledgerlite
├── auth/      → RegisterRequest, LoginRequest/Response, AuthService, AuthController, JwtService, exceptions
├── user/      → User (entity), Role (enum), UserRepository
├── config/    → SecurityConfig (JWT beans, PasswordEncoder, filter chain), JwtProperties
└── common/    → GlobalExceptionHandler (RFC 9457 ProblemDetail)
```

Each request flows through the same three layers:

```
Controller  →  Service  →  Repository
(HTTP I/O)     (business rules)  (database)
```

See [`notes/01-auth-module.md`](notes/01-auth-module.md) for the full ERD, class diagram, and sequence diagrams.

## Security highlights

- Passwords are hashed with **BCrypt** (`DelegatingPasswordEncoder`) — never stored or logged in plain text.
- Authentication is **stateless JWT** (HS256, 1-hour expiry) — no server-side sessions.
- A new user's role is **always hardcoded server-side to `CUSTOMER`** — the registration payload has no `role` field, so privilege escalation at signup is impossible.
- Login returns the **same generic error** for "email not found" and "wrong password," preventing user-enumeration attacks.
- The database enforces its own rules too (`UNIQUE` email, `CHECK` on role) — a second line of defense beyond the application code.
- Errors are returned as standardized [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` responses, never raw stack traces.

## Running it locally

**Requirements:** Java 21, Maven (or use the bundled `./mvnw`), Docker.

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Run the app (Flyway migrations run automatically on startup)
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`.

## API endpoints

| Method | Path | Auth required | Description |
|---|---|---|---|
| `POST` | `/auth/register` | No | Create a new user account |
| `POST` | `/auth/login` | No | Log in, receive a JWT |
| `GET` | `/auth/me` | Yes (`Bearer` token) | Returns the authenticated user's identity |
| `GET` | `/actuator/health` | No | Health check |

### Example: register

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"nikan@test.com","password":"password123","fullName":"Nikan Eidi"}'
```

### Example: log in and call a protected endpoint

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"nikan@test.com","password":"password123"}' \
  | python3 -c "import sys, json; print(json.load(sys.stdin)['accessToken'])")

curl http://localhost:8080/auth/me -H "Authorization: Bearer $TOKEN"
```

## Author

**Nikan Eidi** — [github.com/NikanEidi](https://github.com/NikanEidi) · [nikanvision.dev](https://nikanvision.dev) · [LinkedIn](https://www.linkedin.com/in/nikan-eidi-03476232b)
