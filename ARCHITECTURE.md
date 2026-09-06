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
| `created_at` | account creation timestamp                       |

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

---

## Worlds

### What a world is

A **world** is an independent game server (instance) for players: its own map, its own players,
its own cities and its own state. A player may play on several worlds at once; nothing is shared
between worlds except the user account itself.

- **Capacity**: a world is sized for **~100 players** at the current map size (soft cap; the exact
  registration open/close rules are not settled yet). Long-term target: **~20 city slots per
  player**, so capacity scales with the map.
- **Size**: the map is a **square measured in unit tiles** — currently **500 × 500 = 250,000 tiles**,
  a constant. Making the size configurable per world (bigger maps ⇒ more players) is planned for
  later. Coordinates are `(x, y)`; distances are Euclidean and flat (terrain does not affect
  movement).

### Lifecycle

An **administrator creates** a world. At creation it is **seeded with all of its components**
(terrain, city slots, barbarian villages — see generation, below). It starts in state **`DRAFT`**,
in which it is **not visible to players**. The administrator reviews it and, on approval, moves it to
**`PLAYABLE`** — live and visible, players may join. A later "ended/archived" state is expected but
not yet specified.

```
DRAFT (seeded, admin-only, invisible) --approve--> PLAYABLE (live, players join) --> (ended: TBD)
```

### Map layers

The map is rendered as two layers:

1. **Terrain** — the static cosmetic background. A grid of tile codes, currently:
   `GRASS`, `FOREST`, `LAKE`, `MOUNTAIN`. Terrain is not interactive and does not affect gameplay.
2. **Entities drawn over the terrain** — the gameplay objects, stored separately from the terrain:
   - **city slots** — the only tiles where a city can be founded (occupiable);
   - **player cities** — a founded city occupying a slot;
   - **barbarian villages** — a separate entity that can be farmed but **never occupied or
     conquered**.

### Persistence shape (direction, not final schema)

- **World** and its **terrain** are static per world. They may live in one table or two — splitting
  the terrain out is **not mandatory**. The terrain grid is stored compactly (a byte-per-tile blob,
  ~244 KB per world, further compressed by Postgres), loaded once and cached, then sliced per
  viewport in memory.
- **City slots**, **player cities** and **barbarian villages** are stored as **separate entities**
  (rows), queried by viewport with a range query on `(world_id, x, y)`, and drawn over the terrain
  background.

### Generation

Everything is generated **once, at world creation**, from a random seed drawn on the spot. The seed
is not kept and worlds are **not regenerable**: the generated result is persisted in full, and
nothing is recomputed per request.

1. **Terrain (background).** For each of the 250,000 tiles, pick a terrain code using **noise**
   (Perlin/Simplex) so features form contiguous patches rather than scattered pixels. Compute two
   smooth fields `elevation(x,y)` and `moisture(x,y)` in `0..1`, then classify:
   - `elevation > 0.80` → `MOUNTAIN`
   - `elevation < 0.15` → `LAKE`
   - else `moisture > 0.70` → `FOREST`
   - else → `GRASS`

   Thresholds are tuned so ~60–70% of tiles are `GRASS` (the only terrain eligible for slots). The
   result is the terrain grid, saved as the per-world blob.

2. **City slots.** Target ~4–5k slots (plenty for ~1000 players), clearly spaced apart, only on
   `GRASS`, via a **jittered grid**: split the map into `C × C` cells (`C` = **5**); in each cell take one
   candidate at the cell origin plus a random in-cell offset (the jitter). A candidate becomes a
   slot only if it sits on **open grass** (every tile within 2 is `GRASS`, so no mountain/forest/lake
   sprite overhangs it) and is at least **4 tiles** from every slot accepted so far; otherwise the
   cell is skipped.

3. **Barbarian villages.** A second jittered-grid pass (offset so it interleaves with the slots),
   over remaining `GRASS` tiles, rejecting any candidate closer than the minimum distance `D` to a
   city slot or another barbarian village. Seeded to a target density so every populated area has
   farms within reach.

4. **Cosmetic scatter (optional).** Isolated trees/rocks on remaining `GRASS`, derived from a
   high-frequency noise — purely visual, not necessarily stored.

The world stays in `DRAFT` after generation until an administrator approves it.

### Entities

Only per-world columns are stored; anything identical across all worlds (map size 500×500 for now, world
speed, ~1000 player cap) is a **constant in config**, not a column.

**`worlds`** — the world plus its terrain (kept together; splitting terrain out is not required):
```
worlds(
  id,
  name          UNIQUE,
  state         'DRAFT' | 'PLAYABLE' | 'ENDED',
  terrain       BYTEA,                 -- byte-per-tile grid, ~244 KB, TOAST-compressed
  created_by    FK users,
  created_at
)
```

**`city_slot`** — the only tiles where a city can be founded. Occupancy is not stored here; it is
derived from `city` (a slot is occupied iff a `city` references it):
```
city_slot(
  id,
  world_id      FK worlds,
  x, y          smallint,
  UNIQUE(world_id, x, y),
  INDEX(world_id, x, y)
)
```

**`city`** — a founded city, sitting on one slot:
```
city(
  id,
  world_id      FK worlds,
  slot_id       FK city_slot UNIQUE,   -- one city per slot; occupancy link
  owner_user_id FK users,
  name,
  points        int default 0,
  created_at,
  INDEX(world_id, owner_user_id)
)
-- resources / buildings are a later feature, in their own tables
```

**`barbarian_village`** — separate entity, farmable, never occupied or conquered (no owner):
```
barbarian_village(
  id,
  world_id      FK worlds,
  x, y          smallint,
  UNIQUE(world_id, x, y),
  INDEX(world_id, x, y)
)
-- loot / level fields come with the farming feature
```

**`world_membership`** — a player's enrolment in a world (explicit: used to count the registration
cap and to list a player's worlds, independent of whether they currently own cities):
```
world_membership(
  world_id      FK worlds,
  user_id       FK users,
  joined_at,
  PRIMARY KEY (world_id, user_id)
)
```

**Viewport query** — the client asks for what it can see; the server range-queries entities:
```sql
SELECT * FROM city_slot
WHERE world_id = ? AND x BETWEEN ?ax AND ?bx AND y BETWEEN ?ay AND ?by;   -- LEFT JOIN city for occupancy
-- same range query on barbarian_village
```
Terrain for the viewport is sliced in memory from the cached per-world blob.

### API — Worlds & Map

All endpoints require a valid access token. The `/admin/*` endpoints additionally require role
`ADMINISTRATOR`.

#### Administrator

**POST `/api/v1/admin/worlds`** — create a world. Generation (terrain + slots + barbarian villages)
runs **synchronously**; the world is returned in `DRAFT`.
```json
// request
{ "name": "Caladon I" }
// 201
{ "id": 1, "name": "Caladon I", "state": "DRAFT" }
// 409 -> { "error": "NAME_TAKEN" }
```

**GET `/api/v1/admin/worlds`** — all worlds, including `DRAFT`.
```json
// 200
[ { "id": 1, "name": "Caladon I", "state": "DRAFT", "players": 0, "createdAt": "..." } ]
```

**POST `/api/v1/admin/worlds/{id}/approve`** — `DRAFT → PLAYABLE`.
```json
// 200
{ "id": 1, "name": "Caladon I", "state": "PLAYABLE" }
// 409 -> { "error": "WORLD_NOT_DRAFT" }
```

#### Player

**GET `/api/v1/worlds`** — `PLAYABLE` worlds only. `joined=true` → the UI shows “Play”; `false` →
“Join” (which enrols and grants a starting city).
```json
// 200
[ { "id": 1, "name": "Caladon I", "joined": true },
  { "id": 2, "name": "Caladon II", "joined": false } ]
```

**POST `/api/v1/worlds/{id}/join`** — enrol in the world: creates `world_membership` and founds the
player's **first city on a free slot at the frontier** (the outer edge of the populated area, so new
players cluster together as the world grows outward).
```json
// 200
{ "worldId": 1, "startCity": { "id": 99, "x": 128, "y": 240, "name": "...", "points": 0 } }
// 409 -> { "error": "ALREADY_JOINED" } | { "error": "WORLD_NOT_PLAYABLE" }
```
`WORLD_NOT_PLAYABLE` covers any non-playable state (DRAFT/ENDED) — the reason is not distinguished;
such worlds are not listed in the UI anyway.

**GET `/api/v1/worlds/{id}/map?startX=&startY=&endX=&endY=`** — everything inside a rectangle.
```json
// 200
{
  "startX": 120, "startY": 230, "endX": 122, "endY": 231,
  "terrain": [ 0,0,1, 0,2,0 ],
  "slots":      [ { "x": 120, "y": 230 } ],
  "cities":     [ { "id": 99, "x": 121, "y": 231, "name": "Thal", "points": 120, "owner": "george" } ],
  "barbarians": [ { "x": 122, "y": 230 } ]
}
```
- **`terrain`** — one terrain code per tile of the rectangle, flat **row-major** from `(startX,startY)`.
  Codes: `0=GRASS, 1=FOREST, 2=LAKE, 3=MOUNTAIN`. For element `i`, with `width = endX-startX+1`:
  `x = startX + (i % width)`, `y = startY + (i / width)`.
- **`slots`** — **free** (unoccupied) city slots only; occupied ones appear under `cities`.
- **`cities`** — founded cities (occupied slots), with what's needed to draw the sprite + label.
- **`barbarians`** — barbarian villages.
- Terrain is sliced in memory from the cached per-world blob; entities come from range queries on
  `(world_id, x, y)`.

**GET `/api/v1/worlds/{id}/cities/mine`** — the caller's cities in that world (today: the start
city; a list so it survives multi-city play).
```json
// 200
[ { "id": 99, "x": 128, "y": 240, "name": "george's city", "points": 0 } ]
// 403 -> { "error": "NOT_JOINED" }   404 -> { "error": "WORLD_NOT_FOUND" } (also for DRAFT worlds)
```

**GET `/api/v1/worlds/mine`** — worlds the player is enrolled in.
```json
// 200
[ { "id": 1, "name": "Caladon I" } ]
```

**GET `/api/v1/worlds/{id}/cities/mine`** — the player's cities in this world (one at first, more
later). Used by City view on “Play”, and to centre the map on entry.
```json
// 200
[ { "id": 99, "x": 128, "y": 240, "name": "george's city", "points": 0 } ]
```

_Client rendering note: the visible viewport is 30×30 tiles; the client fetches a larger padded
window (~90×90) around it via `/map`, caches it, and refetches as the camera nears the cache edge.
This is a client concern — `/map` itself stays a plain rectangle query._

### Implementation notes (worlds module)

- **Administrators** are created by hand (`UPDATE users SET role = 'ADMINISTRATOR' ...`); there is
  no bootstrap mechanism in code.
- **Terrain** noise is scaled to ~20 tiles per feature (3 fBm octaves), so mountains, forests and
  lakes come as many patches of roughly 10–25 tiles mixed in with the slots, not a few huge blobs.
  Thresholds are applied to rank-normalised noise fields, so they are exact map fractions: **mountains 12%, lakes 10%, forest ~12%, GRASS ≈ 66%**. With a slot cell of `C = 5`, a
  grass buffer of 2 and a minimum slot distance of 4 this yields **~2.5k slots**. Barbarian villages
  use a cell of 9, the same buffer and `D = 3`, giving **~500 villages** per world. Cosmetic scatter is not implemented.
- **Frontier** for the start city: the free slot nearest the centre of mass of the world's cities
  (the map centre for an empty world), at least 2 tiles from every existing city. Joins are
  serialized per world with a row lock on `worlds`. If no slot qualifies: `409 WORLD_FULL`.
- The start city is named **`<nickname>'s city`**.
- **`/map`** rectangles are capped at **100 × 100** tiles; larger or out-of-range requests get
  `400 VALIDATION_ERROR` with per-parameter details. `/map` on a `DRAFT` world is served to
  administrators only; players get `404 WORLD_NOT_FOUND`. Any authenticated user may view the map of
  a `PLAYABLE` world, joined or not.
- The ~1000-player cap is **not enforced** yet (open/close rules are not settled).
- Terrain is cached in process memory per world; slots/cities/villages are range-queried per request.

---

## UI (frontend)

A separate **single-page app (SPA)** consuming the APIs above.

### Session & tokens

- On login, `accessToken`, `refreshToken` and `nickname` are stored in **localStorage** (v1
  simplicity).
- Every API call sends `Authorization: Bearer <accessToken>`.
- On any `401`, the client calls `/auth/refresh` with the stored refresh token, gets a new access
  token, and retries the request once. If `/auth/refresh` itself returns `401`, clear localStorage
  and go to Login.
- Logout: `POST /auth/logout` with the refresh token, clear localStorage, go to Login.

### Screens & navigation

```
Register → Login → Lobby → (Join / Play) → City view → [Map] → Map
```
The **Admin panel** (create/approve worlds) exists but is deferred and not specified here yet.

**Register** — fields `email`, `nickname`, `password` (+ client-only confirm). Client validation
mirrors the server (email format, nickname 3–20 `[A-Za-z0-9_]`, password 8–72); submit disabled
until valid. `POST /auth/register` → `201` → toast and redirect to Login. `409` →
`EMAIL_TAKEN`/`NICKNAME_TAKEN` as an inline field error; `400 VALIDATION_ERROR` → map `details` to
fields. Link to Login.

**Login** — `email`, `password`. `POST /auth/login` → store tokens + nickname → Lobby. `401` →
inline “Email or password is incorrect”. Link to Register.

**Lobby** — `GET /worlds`. Each world shows its name and one button: **Play** (`joined`) or **Join**
(`!joined`).
- Join → `POST /worlds/{id}/join` → `startCity` → City view.
- Play → City view (loads `/worlds/{id}/cities/mine`).
- Join errors `WORLD_NOT_PLAYABLE` / `ALREADY_JOINED` → toast and reload the list.
- Top bar (present on every post-login screen): nickname + Logout.
- Empty state when there are no playable worlds.

**City view (placeholder)** — shown after Play/Join for the chosen world. Displays the player's city
(name, coordinates, points) from `startCity` (join) or `/worlds/{id}/cities/mine` (play). A **Map**
button in the top-left corner opens the Map centred on that city. Top bar: world name, nickname,
Logout, Back to Lobby. This stays a stub until the city feature (resources/buildings) exists.

**Map view** — full-screen canvas.
- **Camera**: fractional centre tile `(camX, camY)`; visible area ~**30×30** tiles.
- **Rendering**: a canvas render loop (`requestAnimationFrame`) using a sprite atlas — draw terrain
  tiles from cache, then entities on top (free slots, cities, barbarian villages), then city labels
  (name + points). Fractional camera → sub-pixel offset for smooth panning. Canvas/WebGL, not
  DOM/SVG per tile.
- **Data & cache**: fetch a padded **~90×90** window around the camera via `/map`; cache terrain
  (flat array + bounds) and entities. Refetch a recentred window as the camera nears the cache edge,
  asynchronously — never blocking the render loop. Entities are refreshed by refetching the visible
  area on pan **and polling every 15 s** (live SSE push is a later improvement).
- **Interaction**: drag to pan (camera clamped to `[0..499]`); hover → tooltip (name/coords);
  **click on an entity → info panel only, no actions yet** — city (name, owner, points, coords),
  free slot (coords), barbarian village (coords).
- **Controls**: **Home** (recentre on your city), **Go to coordinate** (`x,y` input), Back to City,
  plus the top bar.

### Asset manifest & rendering

The map is drawn from a small **asset registry** keyed by BE type. Each key currently resolves to a
**procedural SVG/canvas placeholder** (low-poly, oblique, seeded from the tile so a given tile always
looks the same). Swapping in finished art later means pointing the same key at a **PNG sprite**
(oblique low-poly, transparent background, soft shadow, **no tile/cube base**) — no other code
changes. Everything sits on a flat tiled grass ground and is drawn **back-to-front** (painter's
order by tile `y`, then `x`).

**Terrain** — from the `/map` `terrain` codes:

| code | type | asset key | role |
|------|------|-----------|------|
| 0 | GRASS | `terrain.grass` | tiled ground texture (base layer) |
| 1 | FOREST | `terrain.forest` | object sprite drawn over the grass |
| 2 | LAKE | `terrain.lake` | object sprite drawn over the grass |
| 3 | MOUNTAIN | `terrain.mountain` | object sprite drawn over the grass |

**Entities** — from `/map` `slots` / `cities` / `barbarians`:

| entity | asset key | notes |
|--------|-----------|-------|
| free city slot | `entity.slot` | marker for a foundable, unoccupied spot |
| player city | `entity.city.t1` / `.t2` / `.t3` | tier chosen from `points` — placeholder thresholds: t1 `< 1000`, t2 `1000–4999`, t3 `≥ 5000` (tunable) |
| barbarian village | `entity.barbarian` | farmable, never occupied/conquered |

**Per-frame draw order:** grass ground → then FOREST/LAKE/MOUNTAIN object sprites and all entities in
a single depth-sorted pass by `(y, x)` → then city name + points labels on top.

Placeholders are the procedural set already prototyped (grass, forest, mountain, lake, slot, city
t1/t2/t3, barbarian camp); final art is dropped in per registry key without touching the renderer.
They live in **`web/assets/procedural-sprites.js`** (each generator returns SVG markup, keyed by the
registry names above; `cityTierKey(points)` picks the city tier) with **`web/assets/preview.html`** as
a standalone preview.

### Implementation notes (ui)

- **Stack**: React 18 + TypeScript + Vite, in `ui/` (outside Maven). `react-router-dom` for
  screens; no component library. Vite's dev server proxies `/api` to the backend, so there is no
  CORS configuration; production serving of `ui/dist` is not decided yet.
- **Placeholders** stay in `web/assets/procedural-sprites.js` (so `preview.html` keeps working
  standalone) and are imported through the `@assets` alias. `ui/src/map/assets.ts` is the registry:
  each key rasterises its SVG generator into an offscreen canvas at load time (several seeded
  variants per key, chosen per tile), and the renderer only ever sees those bitmaps — dropping in a
  PNG means changing that one entry.
- **Renderer**: Canvas 2D, tile = 48 CSS px, redraws only when dirty (camera/data change). Ground is
  the grass pattern scrolled with the camera; terrain objects and entities go through one
  `(y, x)`-sorted pass; labels last. Lakes are the one exception: contiguous lake tiles are painted
  as flat water with a shoreline on land-facing sides (the `terrain.lake` registry key is kept for
  a future PNG but unused by the placeholder).
- **Data**: `/map` window of 90×90 around the camera, refetched when the camera is within 15 tiles
  of a non-map edge; the visible area (+3 tiles) is refreshed 400 ms after a pan ends; the whole
  window is refetched every 15 s.
- **Join response** now carries `startCity.points` so the City view can show points without a
  second request.
- Admin panel: not built (deferred in the spec).

