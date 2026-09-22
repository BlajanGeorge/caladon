# Caladon — Architecture Decisions

A Kotlin, multi-module Maven project for a browser strategy MMO (Grepolis / Tribal Wars style):
players build cities across worlds, raise troops, and send them to attack or support other cities.

This document records locked architecture decisions, to be handed to an implementation agent.
It grows as we design more features. **Status: work in progress — Users & Authentication and Worlds are settled; Resources and Buildings are proposals awaiting confirmation.**

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

## Resources

**Status: proposed (design only, nothing implemented). Open questions are listed at the end.**

### The three resources

Every city has its own stock of exactly **three** resources. They are the single currency of the
game: buildings, troops, and (later) market trades are all paid in them. Alongside them, every
city has a **population** pool that buildings and troops are paid from (see *Population*, below).

| code    | name  | flavour                                        | produced by (see *Buildings*) |
|---------|-------|------------------------------------------------|-------------------------------|
| `WOOD`  | Wood  | timber for buildings and siege equipment       | Woodcutter                    |
| `STONE` | Stone | quarried stone for walls and stone buildings   | Stone Mine                    |
| `IRON`  | Iron  | smelted iron for weapons and armour            | Iron Mine                     |

- The set is **fixed and global** (same three on every world), so it is a Kotlin enum, not a table.
- Resources are **per city**, never per player: cities do not share a pool. Moving resources between
  cities is the market/trade feature, later.
- **Terrain does not affect production.** This keeps the existing decision that terrain is purely
  cosmetic. A forest next to a city gives no wood bonus. (Revisit only if we deliberately decide to
  make terrain matter.)

### Production model

Resources **accrue continuously** at a rate **per minute**. There is **no scheduled tick job**: the stock
is stored together with the instant it was last settled, and is settled **lazily** whenever the
city is read or a resource is spent:

```
elapsedMin = (now - settledAt) / 1min
stock[r]   = min(capacity, stock[r] + rate[r] * elapsedMin)     for each r
settledAt  = now
```

- `rate[r]` (per minute) is **derived, never stored**: `production[level]` of the resource
  building for `r` (Woodcutter / Stone Mine / Iron Mine), times the world speed.
- `capacity` is the same for all three resources and is **derived** from the Deposit's level
  (`capacity[level]`). Production above capacity is **lost**, not queued.
- `stock` keeps its **fractional part** (production does not round per settlement); the API and UI
  show `floor(stock)`.
- **World speed** is a per-world-set constant in config (1.0 for now), multiplying every rate.
- Settlement and spending happen **in one transaction under a row lock** on the city's resource
  row: settle → check `stock >= cost` for every resource → deduct → save. Insufficient funds are
  rejected atomically (`409 NOT_ENOUGH_RESOURCES`, with the shortfall per resource).

### Starting values (tunable constants)

| constant                | value   | note                                                 |
|-------------------------|---------|------------------------------------------------------|
| starting stock          | 500     | each resource, on founding a city                    |
| production at level 1   | 30/min  | each resource, from its level-1 building             |
| capacity at level 1     | 2000    | from the level-1 Deposit                             |
| world speed             | 1.0     |                                                      |

At these values a fresh city fills its Deposit from 500 to 2000 in **50 minutes** (from empty in
~67). This is a deliberately fast pace; the per-level tables (see *Buildings*) can slow it down.

### Population

Population is the **fourth thing a city spends**, next to the three resources, but it behaves
differently: it does **not accrue over time**. A city has a **population pool** that only the farm
fills; troops and buildings are **paid for out of that pool**, and what is left is the one number
that exists.

```
population  (stored)  -- people currently free in the city; 100 people, build a 10-person building → 90
```

- **Only "remaining" exists.** The city row holds the free population as a plain integer counter.
  Spending decrements it, refunds increment it, the farm credits it, and the value in the database
  *is* the answer — nothing is recomputed from building levels or troop counts. There is **no
  "max population" figure**: the ceiling a city can reach is simply what the farm gives at its
  highest level, and that is a property of the farm's level table, not something tracked or
  reported per city.
- **Only the farm increases population.** Completing farm level `n` credits the pool with
  `farmGain[n]`, a per-level lookup table in config. Nothing else — no terrain, no other building,
  no research, no time — adds population.
- **Troops spend it.** Every unit has a population cost, paid at recruitment. Recruiting 10 units
  costing 1 each decrements the pool by 10.
- **Buildings spend it.** Every building level has a population cost, cumulative over levels
  (upgrading from 3 to 4 costs `popCost(4)`). The farm's own levels cost population too; its
  tables are tuned so each level nets positive (`farmGain[n] > popCost(n)`).
- **Paid at order time.** Queueing a recruitment or a building level decrements the pool
  immediately, together with the resources, in **one transaction** under the city row lock: an
  order takes both or neither. Not enough population → `409 NOT_ENOUGH_POPULATION` (shortfall in
  the response). The counter can never go negative.
- **Refunds.** When troops are **destroyed** (in battle, or otherwise lost) their population
  **returns to the pool**: the battle resolution increments the home city's counter by
  `popPerUnit × unitsLost`. Cancelling a queued order refunds what it took. Troops away from the
  city (attacking, supporting, returning) stay paid for by their home city until they die or come
  back. Buildings are permanent for now (no demolition), so their population is never refunded.
- **Invariant** (checkable, not enforced at runtime):
  `population == Σ farmGain[1..farmLevel] - Σ buildings popCost - Σ troops popPerUnit × count`.
  Every write path above keeps it true; a periodic consistency check can recompute it to catch
  bugs.

**Starting values (tunable constants):** a new city starts at **Farm level 1** with `farmGain[1]`
free — **100** people. The level-1 buildings a city is founded with cost nothing, so all 100 are
free at founding. The gain table for higher levels is a later detail (see *Buildings*).

### Persistence

One row per city, in its own table (the `city` row stays the map/ownership record):

```
city_resources(
  city_id     PK, FK city ON DELETE CASCADE,
  wood        numeric(14,3) NOT NULL,
  stone       numeric(14,3) NOT NULL,
  iron        numeric(14,3) NOT NULL,
  settled_at  timestamptz  NOT NULL,         -- instant the stocks above were last settled to
  population  integer      NOT NULL CHECK (population >= 0)   -- free people, see Population
)
```

- Created together with the city (join / later: conquest or founding), with the starting stock
  and the starting population pool.
- `population` is the **remaining** free population, maintained by every spend/refund path
  (build order, recruit order, cancellation, troop losses, farm level completion). It is not
  time-based, so it takes no part in settlement.
- Rates and capacity are **not** columns: they are recomputed from building levels (later) or the
  base constants. Storing them would create a second source of truth that drifts when a building
  finishes.
- Three fixed columns rather than a `(city_id, resource, amount)` row per resource: the set never
  changes, every read wants all three, and a single row makes the settle-and-spend lock trivial.
- Resource spending is always the "settle then deduct" transaction above; nothing else writes the
  resource columns.
  Income from other sources (farming a barbarian village, incoming trade) is an **addition**
  through the same settle path, capped at capacity.

### API (shape, not final)

Resources are reported on the city, never as a free-standing list.

**GET `/api/v1/worlds/{id}/cities/{cityId}`** — city detail (owner only), including resources:
```json
// 200
{
  "id": 99, "name": "george's city", "x": 128, "y": 240, "points": 250,
  "resources": {
    "wood":  { "stock": 512, "ratePerMinute": 30 },
    "stone": { "stock": 512, "ratePerMinute": 30 },
    "iron":  { "stock": 512, "ratePerMinute": 30 },
    "capacity": 2000,
    "serverTime": "2026-09-22T19:00:00Z"
  },
  "population": 100,
  "buildings": [
    { "type": "FARM",       "level": 1, "points": 50 },
    { "type": "WOODCUTTER", "level": 1, "points": 50 },
    { "type": "STONE_MINE", "level": 1, "points": 50 },
    { "type": "IRON_MINE",  "level": 1, "points": 50 },
    { "type": "DEPOSIT",    "level": 1, "points": 50 }
  ]
}
// 403 -> { "error": "NOT_OWNER" }   404 -> { "error": "CITY_NOT_FOUND" }
```

- `stock` is already settled to `serverTime` and floored. The client shows it **exactly as
  returned** — no local ticking between polls (tried and rejected: numbers changing every second
  are distracting). It **re-fetches the city detail once per minute** (resources and population in
  the same response) and after any action that spends.
- `population` is the remaining free population, read straight from the row; the once-per-minute
  poll picks up changes made elsewhere (e.g. a battle).
- `/worlds/{id}/cities/mine` stays as it is (map/ownership only); the City view fetches the detail
  endpoint for the city it shows.
- Spending endpoints belong to the features that spend (build, recruit) and return the settled
  resources in their response so the client can re-sync without a second call.

### Implementation notes (resources, first slice)

- **Built**: `city_resources` (migration `V101`, backfilled for existing cities at 500/500/500 and
  100 population), the `Resource` enum, lazy settlement (`Settlement`, per minute, 3-decimal
  carry, capped) persisted under a `PESSIMISTIC_WRITE` lock on the row, the row created in the
  same transaction as the start city on join, and **`GET /worlds/{id}/cities/{cityId}`** (owner
  only; `403 NOT_OWNER`, `404 CITY_NOT_FOUND` for a missing city, a city of another world, a
  missing world, or a DRAFT world for a player). `stock` is floored in the response;
  `ratePerMinute` is a number (30.0 at world speed 1.0).
- **Not built yet**: buildings and levels — rate, capacity and starting population are the
  level-1 constants in `ResourceConstants` (30/min, 2000, 100). No spend path exists yet, so
  `NOT_ENOUGH_*` errors and the `buildings` field of the city detail are still to come.
- Tests: `SettlementTest` (pure) and `WorldApiTest` (Testcontainers) with a `@Primary` mutable
  `Clock` (`MutableClockConfig`) so settlement is exercised by advancing time — advances must stay
  under the 1 h access-token TTL because the JWT service shares that clock.

### Interplay with later features (so this design does not box them in)

- **Buildings**: see the *Buildings* section — three resource buildings set the per-resource rate,
  the Deposit sets capacity, the Farm adds population; every level costs resources **and**
  population and adds points.
- **Troops**: every unit costs resources and population to recruit; battle resolution refunds
  the population of destroyed units to their home city.
- **Barbarian villages / farming**: returning troops add loot through the settle path; loot above
  capacity is lost.
- **Market**: transfers between cities, subject to the receiver's capacity.
- **Points**: resource stock does **not** count toward city points; buildings do.
- **Hiding place / plunder protection**: not planned; add a fourth derived number later if needed.

### Open questions

1. Names: **Wood / Stone / Iron** (proposed) vs Wood / Clay / Iron (Tribal Wars) vs Wood / Stone /
   Silver (Grepolis). Purely naming; codes are the only thing that matters in the schema.
2. Should the three resources have **different** base rates or costs to give them character (e.g.
   iron scarcer, stone bulkier)? Proposed: identical base values now, differentiate through costs
   once buildings and troops exist.
3. Should a **DRAFT** world's cities accrue? Moot today (players cannot join a DRAFT world).
4. Population is paid at **order time** (proposed) vs at **completion**. Order-time is stricter
   and prevents over-queuing; completion-time feels more forgiving but needs a rule for what
   happens when the pool is empty when a queued item finishes.
5. Whether `numeric(14,3)` or `double precision` for the stock columns. `numeric` proposed for exact
   deductions; the fractional part is only ever production carry-over.

## Buildings

**Status: proposed (design only, nothing implemented). Exact per-level tables are a later detail.**

### The five starting buildings

Every city is founded with the same **five buildings, all at level 1**. They are the machinery
behind Resources and Population: each one has exactly one job.

| code         | working name | job (what its level drives)                       | level-1 value      | points at level 1 |
|--------------|--------------|---------------------------------------------------|--------------------|-------------------|
| `FARM`       | Farm         | **population** — each level adds people to the pool | +100 population   | 50                |
| `WOODCUTTER` | Woodcutter   | **wood** production rate                          | 30 wood / min      | 50                |
| `STONE_MINE` | Stone Mine   | **stone** production rate                         | 30 stone / min     | 50                |
| `IRON_MINE`  | Iron Mine    | **iron** production rate                          | 30 iron / min      | 50                |
| `DEPOSIT`    | Deposit      | **capacity** — max stock of each resource         | 2000 per resource  | 50                |

- The set is **fixed and global**: a Kotlin enum, not a table. More buildings (barracks, wall,
  market…) join the enum later; the model below does not change for them.
- A new city therefore starts with **250 points**, **100 free population**, **30/min** of each
  resource and a **2000** cap.

### Levels

Every building has a **level ≥ 1** and a **max level** (a constant per building type in config;
the same for all worlds). Raising a level is the only operation on a building.

For each building type there are four per-level lookup tables in config (constants, not rows):

| table                 | meaning                                                          |
|-----------------------|------------------------------------------------------------------|
| `cost[level]`         | wood / stone / iron to reach `level` (from `level-1`)             |
| `popCost[level]`      | population to reach `level`                                       |
| `points[level]`       | points the building is worth **at** `level` (cumulative, not per step) |
| `effect[level]`       | the building's job at `level`: `farmGain`, `production`, or `capacity` |

- **Level 1 is free.** The founding buildings cost no resources and no population; the tables
  start at level 2. `points[1] = 50` and `effect[1]` are the values in the table above.
- **Monotonic**: every table grows with level. The Farm's `farmGain[n]` is always larger than its
  own `popCost[n]`, so upgrading the Farm never loses population.
- **Points** are a property of the level, so a city's points are simply
  `Σ points[level(b)]` over its buildings. The `city.points` column is kept as a **maintained
  counter** updated on every level completion (exactly like population), and the same invariant
  check applies. Points drive the map's city tier art (t1/t2/t3 thresholds are unchanged).
- The exact numbers for every table beyond level 1 (growth curves, max levels, build times) are
  **out of scope here** and will be set when the feature is implemented and tuned.

### Level-up (the one operation)

```
upgrade(city, building):
  lock city row
  settle resources
  level = current + 1;  reject if level > maxLevel            -> 409 MAX_LEVEL
  need  = cost[level], popCost[level]
  reject if any resource short or population short            -> 409 NOT_ENOUGH_RESOURCES / NOT_ENOUGH_POPULATION
  deduct resources and population                             (paid at order time, as decided in Resources)
  complete:  level += 1; points += points[level] - points[level-1];
             if FARM: population += farmGain[level]
```

- **Build time / queue** is a later detail. The first implementation may complete the upgrade
  **instantly** inside the same transaction; when build times arrive, "complete" moves to the
  moment the timer ends, and everything before it stays exactly as above (cost already paid,
  cancel refunds it). This is why cost is paid at order time.
- The effect of a new level (faster production, larger cap, more people) applies **from
  completion**: resources are settled *before* the level changes, so the old rate covers the time
  up to that instant and the new rate the time after.
- A Deposit upgrade never destroys stock; a stock above the *old* cap cannot exist anyway.

### Persistence

One row per (city, building), created with the city:

```
city_building(
  city_id     FK city ON DELETE CASCADE,
  building    varchar(16)   -- enum code
  level       smallint NOT NULL CHECK (level >= 1),
  PRIMARY KEY (city_id, building)
)
```

- Five rows are inserted at founding (join), all at level 1, in the same transaction as the
  `city` and `city_resources` rows.
- Effects, costs and points are never stored: they are table lookups on `level`. The only
  maintained counters are `city.points`, `city_resources.population` and the resource stocks.
- Rows, not five level columns on `city`, so adding a building type later is an enum value plus
  a backfill, not a migration of the city table.

### API (shape, not final)

The city detail (`GET /api/v1/worlds/{id}/cities/{cityId}`, see Resources) carries the buildings
with their current level and points, as shown there.

**POST `/api/v1/worlds/{id}/cities/{cityId}/buildings/{building}/upgrade`** — raise one level.
Owner only. Returns the city's settled state so the client re-syncs from the response:
```json
// 200
{
  "building": { "type": "FARM", "level": 2, "points": 120 },
  "points": 320,
  "population": 130,
  "resources": { "wood": { "stock": 210, "ratePerMinute": 30 }, "...": "...", "capacity": 2000, "serverTime": "..." }
}
// 409 -> { "error": "NOT_ENOUGH_RESOURCES", "shortfall": { "wood": 90 } }
//        { "error": "NOT_ENOUGH_POPULATION", "shortfall": 12 }
//        { "error": "MAX_LEVEL" }
// 403 -> { "error": "NOT_OWNER" }   404 -> { "error": "CITY_NOT_FOUND" }
```

A read-only companion for the UI's building panel (what the next level costs and gives):
**GET `/api/v1/worlds/{id}/cities/{cityId}/buildings`** →
`[ { "type": "FARM", "level": 1, "maxLevel": 30, "points": 50, "effect": 100,
     "next": { "cost": { "wood": 90, "stone": 80, "iron": 70 }, "popCost": 5, "points": 120, "effect": 220 } }, … ]`
(`next` is absent at max level).

### Open questions

1. **Names.** Working names follow the conversation: Woodcutter, Stone Mine, Iron Mine, Deposit.
   Conventional genre names would be Timber Camp, Quarry, Iron Mine, Warehouse. Codes are what
   the schema and API use, so renaming later is display-only.
2. **Pace.** 30/min against a 2000 cap fills a fresh city in under an hour, which asks for
   frequent logins early on. Fine for now; the tables can flatten it.
3. **One upgrade at a time** per city, or a queue, once build times exist.

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
button in the top-left corner opens the Map centred on that city. This stays a stub until the
city feature (resources/buildings) exists.

**City view top bar (planned)** — the City view uses the **same HUD top bar as the Map** (the
`map-topbar` component: world name on the left; Profile icon and Account icon with its menu —
Back to Lobby, Logout — on the right), not the plain page top bar. It adds one thing: a
**resource strip** placed immediately **to the left of the Profile icon**, in the right-hand
group:

```
[ Caladon I ]                       [wood 512] [stone 512] [iron 512] [pop 236]  (profile) (account)
```

- Four items, in this order: **Wood, Stone, Iron, Population**, each an icon (registry keys
  `hud.wood`, `hud.stone`, `hud.iron`, `hud.population`) followed by the number. All four icons
  are **finished PNG art** already in the repo (see *Resource icons*, below). All four numbers
  are shown exactly as the server returned them and change only on refresh.
- Hover on a resource shows a tooltip with `+<ratePerMinute>/min` and the capacity; a stock at
  capacity is shown in a warning colour. Population has no tooltip.
- The strip is a self-contained component fed by the same city-detail data the City view already
  polls once per minute (see Resources); the top bar itself does not fetch. On the Map the strip
  is **not shown** for now (the Map bar stays as it is); the component is shared so it can be
  switched on there later with the same data.
- While the first fetch is in flight the strip renders its icons with `…` placeholders, so the bar
  does not jump when the numbers arrive.

**Resource icons** — four PNGs in `web/assets/sprites/`, one per strip item, in the same style
(a round medallion: hammered-metal disc, gold scrollwork rim, a gem in the top clasp; the subject
sits in the centre; transparent background):

| file                 | registry key     | motif                                              | gem   |
|----------------------|------------------|----------------------------------------------------|-------|
| `res-wood.png`       | `hud.wood`       | bundle of logs, leather strap                       | green |
| `res-stone.png`      | `hud.stone`      | pile of grey and tan boulders                       | red   |
| `res-iron.png`       | `hud.iron`       | two strapped iron ingots                            | blue  |
| `res-population.png` | `hud.population` | family relief (man, woman, child), wheat sheaf, house | amber |

- **Source size 1024 × 1024**, RGBA, edges fully transparent; ~1.8 MB each. They are far larger than
  the top bar needs (the strip draws them at roughly **28–32 CSS px**), so before shipping they
  should be **downscaled to 64 × 64** (retina-safe at 32 px) and re-exported, keeping the 1024
  originals under `web/assets/sprites/` as the source of truth, in line with the other sprites.
- The gem colour doubles as the item's **accent colour** in the UI (green = wood, red = stone,
  blue = iron, amber = population) — e.g. for the at-capacity warning tint and tooltip heading —
  so the four items read as distinct at a glance.
- Like every other sprite they are referenced through the `@assets` alias and the registry in
  `ui/src/map/assets.ts`; nothing outside the registry knows the file names.

_Planned (Resources section): the City view fetches `/worlds/{id}/cities/{cityId}` on entry and
then **polls it once per minute** for resources and population; between polls the numbers do not
change. Any spend action
replaces the displayed values with the ones in its response. Polling stops when the view is
left or the tab is hidden, and resumes with an immediate fetch._

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
  the grass pattern scrolled with the camera, with **no client-side decorative scatter** (bushes/rocks
  were tried and removed: they are not backend map data and only added noise); terrain objects and entities go through one
  `(y, x)`-sorted pass; labels last. Lakes are the one exception: contiguous lake tiles are painted
  as flat water with a shoreline on land-facing sides (the `terrain.lake` registry key is kept for
  a future PNG but unused by the placeholder).
- **Data**: `/map` window of 90×90 around the camera, refetched when the camera is within 15 tiles
  of a non-map edge; the visible area (+3 tiles) is refreshed 400 ms after a pan ends; the whole
  window is refetched every 15 s.
- **Join response** now carries `startCity.points` so the City view can show points without a
  second request.
- **City view HUD (built)**: `CityPage` uses `MapTopBar` with a `strip` slot rendered just before
  the Profile icon; `ResourceStrip` shows Wood / Stone / Iron / Population from
  `worldsApi.cityDetail`, re-fetched every 60 s, paused while `document.hidden` and re-fetched on
  return. All numbers are shown exactly as returned (a per-second local extrapolation was built
  and removed as distracting). Icons are 128 px copies (`hud-*.png`) of the 1024 px medallions,
  drawn at 70 px, the same size as the Profile and Account icons. The Map does not
  pass a strip. Buildings/levels are not shown yet.
- Admin panel: not built (deferred in the spec).

