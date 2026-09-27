-- Troop movements: one row per leg on the road (ARCHITECTURE.md -> Army -> Movements).
-- origin_city_id is always the home city, target_city_id the other end; `direction` says which way the
-- troops are flying, so a leg that turns around keeps its row and its id.
CREATE TABLE city_movement (
    id             BIGSERIAL   PRIMARY KEY,
    world_id       BIGINT      NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    origin_city_id BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    target_city_id BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    kind           VARCHAR(16) NOT NULL,
    direction      VARCHAR(16) NOT NULL,
    departs_at     TIMESTAMPTZ NOT NULL,
    arrives_at     TIMESTAMPTZ NOT NULL,
    carried_wood   BIGINT      NOT NULL DEFAULT 0 CHECK (carried_wood >= 0),
    carried_stone  BIGINT      NOT NULL DEFAULT 0 CHECK (carried_stone >= 0),
    carried_iron   BIGINT      NOT NULL DEFAULT 0 CHECK (carried_iron >= 0),
    -- Set once the last leg has been processed; the row then stays as a record and is never shown again.
    applied        BOOLEAN     NOT NULL DEFAULT FALSE
);

-- What the movement is carrying; a type is listed once, and only while it is alive.
CREATE TABLE city_movement_unit (
    movement_id BIGINT      NOT NULL REFERENCES city_movement (id) ON DELETE CASCADE,
    unit        VARCHAR(16) NOT NULL,
    count       INTEGER     NOT NULL CHECK (count > 0),
    PRIMARY KEY (movement_id, unit)
);

CREATE INDEX ix_city_movement_origin ON city_movement (origin_city_id) WHERE NOT applied;
CREATE INDEX ix_city_movement_target ON city_movement (target_city_id) WHERE NOT applied;
CREATE INDEX ix_city_movement_due ON city_movement (arrives_at) WHERE NOT applied;
