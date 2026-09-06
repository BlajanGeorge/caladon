# Caladon

Backend for a browser strategy MMO. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design decisions.

## Modules

| module   | contents                                                                                     |
|----------|----------------------------------------------------------------------------------------------|
| `users`  | users, roles, JWT authentication (`/api/v1/auth/*`)                                          |
| `worlds` | world generation (terrain, city slots, barbarian villages), lifecycle, join, map viewport     |
| `app`    | the runnable Spring Boot application; depends on every feature module                        |
| `ui/`    | the single-page app (React + TypeScript + Vite); not a Maven module                          |
| `web/assets/` | procedural placeholder sprites (`procedural-sprites.js`) and a standalone `preview.html` |

Each feature module owns its Flyway migrations (`users`: V1–V99, `worlds`: V100–V199).

## Requirements

- JDK 17+, Maven 3.9+
- Docker (for the local PostgreSQL and for the integration tests)
- Node 22+ (see `ui/.nvmrc`) for the frontend

## Run locally

```bash
docker compose up -d          # PostgreSQL on localhost:5433 (db/user/password: caladon)
mvn package -DskipTests
java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

Configuration (all optional for local development): `CALADON_DB_URL`, `CALADON_DB_USER`,
`CALADON_DB_PASSWORD`, `CALADON_JWT_SECRET` (at least 32 bytes; **must** be set outside local dev).

## Run the UI

```bash
cd ui
npm install
npm run dev        # http://localhost:5173, proxies /api to the backend on :8080
```

## Test

```bash
mvn test                 # backend
cd ui && npm test        # frontend unit tests (vitest)
cd ui && npm run build   # typecheck + production bundle in ui/dist
```

The API tests in `users` start a throwaway PostgreSQL via Testcontainers and are skipped when Docker
is not available.

## Administrators

Registration always creates a `PLAYER`. To get an administrator, promote an account directly in
the database:

```sql
UPDATE users SET role = 'ADMINISTRATOR' WHERE email = 'admin@example.com';
```

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

# worlds (W = http://localhost:8080/api/v1, T = an access token)
curl -s -X POST $W/admin/worlds -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"name":"Caladon I"}'
curl -s -X POST $W/admin/worlds/1/approve -H "Authorization: Bearer $T"
curl -s $W/worlds -H "Authorization: Bearer $T"
curl -s -X POST $W/worlds/1/join -H "Authorization: Bearer $T"
curl -s "$W/worlds/1/map?startX=205&startY=205&endX=294&endY=294" -H "Authorization: Bearer $T"
```
