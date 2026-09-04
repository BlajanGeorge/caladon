# Caladon — Architecture Decisions

A Kotlin, multi-module Maven project for a browser strategy MMO (Grepolis / Tribal Wars style):
players build cities across worlds, raise troops, and send them to attack or support other cities.

This document records locked architecture decisions, to be handed to an implementation agent.
It grows as we design more features. **Status: work in progress — only Users & Authentication is settled so far.**

---

## Tech stack

- **Backend**: Kotlin, as a **multi-module Maven** project.
- **Storage database**: **PostgreSQL**.

- **Framework**: Spring Boot 3.5 (Spring MVC, Spring Security, Spring Data JPA / Hibernate).
- **Schema migrations**: Flyway, SQL files owned by the module that owns the tables.
- **JWT**: jjwt, HMAC-SHA signed with a single server secret (`caladon.jwt.secret`).
- **Module layout**: one Maven module per feature (`users` first), plus an `app` module that only
  assembles them into the runnable Spring Boot application. Feature modules live under
  `com.caladon.<feature>` and are picked up by component scanning from `com.caladon`.
- **Tests**: JUnit 5; API tests run against a real PostgreSQL via Testcontainers.

More of the stack (caching, messaging, real-time delivery) will be decided later.

---

## Users & Roles

Two **global** roles (a user has exactly one role, the same across every world):

- **PLAYER** — a human player. Registers an account, logs in, may play on multiple worlds
  simultaneously, owns cities, etc. No access to administration commands.
- **ADMINISTRATOR** — a management user. Can create new worlds, open/close registration for a
  world, ban players, and view reports and player conversations.

The concrete PLAYER and ADMINISTRATOR operations are deliberately **not** defined yet — they will be
specified once the game features exist.

---

## Database — `users` table

| column     | notes                                              |
|------------|----------------------------------------------------|
| `id`       | primary key                                        |
| `role`     | `PLAYER` or `ADMINISTRATOR`                         |
| `email`    | **unique**                                         |
| `password` | stored as a hash (bcrypt/argon2), never plaintext  |
| `nickname` | **unique**                                         |

`email` and `nickname` are each unique across the whole table (players and administrators alike).

---

## Authentication

- **Register**: the user submits the required data. Rejected if the email or nickname already
  exists. On success the user is redirected to login (no auto-login).
- **Login**: with **email + password**. On success the server returns a JWT **access token** and a
  JWT **refresh token**.
  - **access token**: valid **1 hour**. Claims: `sub` = user id, `role`. No other custom claims.
  - **refresh token**: valid **1 week**.
- **Refresh**: the UI, on any `401`, retries using the refresh token to obtain a new access token.
  The refresh token itself is not rotated — it stays valid for its week.
- **Logout**: invalidates the refresh token.
- When the refresh token also expires (or was revoked), the user is logged out and must log in again.

### Refresh token storage

Refresh tokens are **persisted server-side** (DB table or Redis) so they can be revoked before
expiry. Logout removes/marks the token invalid; `/refresh` is rejected if the presented refresh
token is not present/valid in that store.

---

## API — Authentication

Base path: `/api/v1/auth` (and `/api/v1/users`).

### POST `/api/v1/auth/register`
```json
// request
{ "email": "a@b.com", "nickname": "george", "password": "..." }
```
- `201` — empty body (UI redirects to login).
- `409` — `{ "error": "EMAIL_TAKEN" }` or `{ "error": "NICKNAME_TAKEN" }`.

### POST `/api/v1/auth/login`
```json
// request
{ "email": "a@b.com", "password": "..." }
```
- `200` — `{ "accessToken": "jwt...", "refreshToken": "jwt...", "nickname": "george" }`
- `401` — `{ "error": "INVALID_CREDENTIALS" }`

### POST `/api/v1/auth/refresh`
```json
// request
{ "refreshToken": "jwt..." }
```
- `200` — `{ "accessToken": "jwt..." }` (access token only; refresh token unchanged)
- `401` — `{ "error": "INVALID_REFRESH_TOKEN" }` (UI logs the user out)

### POST `/api/v1/auth/logout`
Requires `Authorization: Bearer <access token>`.
```json
// request
{ "refreshToken": "jwt..." }
```
- `204` — refresh token invalidated in the store.

_No `/me` endpoint for now; revisit later if the UI needs it._

### Implementation notes (users module)

- Registration always creates a `PLAYER`; how administrators get created is not decided yet.
- Registration input is validated: email format, nickname 3–20 chars of `[A-Za-z0-9_]`,
  password 8–72 chars. Failures return `400` `{ "error": "VALIDATION_ERROR", "details": { field: message } }`.
- Email and nickname uniqueness is case-insensitive; email is stored lowercased.
- Refresh tokens are stored in the `refresh_tokens` table keyed by the JWT `jti`; the JWT carries
  `typ: "refresh"` so the two token kinds can never be used in each other's place.
- Unauthenticated access to a protected endpoint returns `401` `{ "error": "UNAUTHORIZED" }`;
  the JWT filter grants the authority `ROLE_<role>` for future admin-only endpoints.
