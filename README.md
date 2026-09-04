# Caladon

Backend for a browser strategy MMO. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design decisions.

## Modules

| module  | contents                                                                 |
|---------|--------------------------------------------------------------------------|
| `users` | users, roles, JWT authentication (`/api/v1/auth/*`), Flyway migrations   |
| `app`   | the runnable Spring Boot application; depends on every feature module    |

## Requirements

- JDK 17+, Maven 3.9+
- Docker (for the local PostgreSQL and for the integration tests)

## Run locally

```bash
docker compose up -d          # PostgreSQL on localhost:5433 (db/user/password: caladon)
mvn package -DskipTests
java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

Configuration (all optional for local development): `CALADON_DB_URL`, `CALADON_DB_USER`,
`CALADON_DB_PASSWORD`, `CALADON_JWT_SECRET` (at least 32 bytes; **must** be set outside local dev).

## Test

```bash
mvn test
```

The API tests in `users` start a throwaway PostgreSQL via Testcontainers and are skipped when Docker
is not available.

## Try the API

```bash
B=http://localhost:8080/api/v1/auth
curl -i -X POST $B/register -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","nickname":"george","password":"password1"}'
curl -s -X POST $B/login -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"password1"}'
curl -s -X POST $B/refresh -H 'Content-Type: application/json' -d '{"refreshToken":"<jwt>"}'
curl -i -X POST $B/logout -H 'Authorization: Bearer <access jwt>' \
  -H 'Content-Type: application/json' -d '{"refreshToken":"<jwt>"}'
```
