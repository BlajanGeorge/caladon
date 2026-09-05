-- Flyway versions are global across modules: users owns V1..V99, worlds owns V100..V199.

CREATE TABLE worlds (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL,
    state      VARCHAR(16)  NOT NULL,
    terrain    BYTEA        NOT NULL DEFAULT ''::bytea,  -- byte-per-tile grid, row-major, SIZE*SIZE bytes; written right after insert
    created_by BIGINT       NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_worlds_name UNIQUE (name),
    CONSTRAINT ck_worlds_state CHECK (state IN ('DRAFT', 'PLAYABLE', 'ENDED'))
);

CREATE TABLE city_slot (
    id       BIGSERIAL PRIMARY KEY,
    world_id BIGINT    NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    x        SMALLINT  NOT NULL,
    y        SMALLINT  NOT NULL,
    CONSTRAINT uk_city_slot_position UNIQUE (world_id, x, y)
);

CREATE TABLE city (
    id            BIGSERIAL    PRIMARY KEY,
    world_id      BIGINT       NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    slot_id       BIGINT       NOT NULL REFERENCES city_slot (id),
    owner_user_id BIGINT       NOT NULL REFERENCES users (id),
    name          VARCHAR(64)  NOT NULL,
    points        INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_city_slot UNIQUE (slot_id)
);
CREATE INDEX ix_city_world_owner ON city (world_id, owner_user_id);

CREATE TABLE barbarian_village (
    id       BIGSERIAL PRIMARY KEY,
    world_id BIGINT    NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    x        SMALLINT  NOT NULL,
    y        SMALLINT  NOT NULL,
    CONSTRAINT uk_barbarian_village_position UNIQUE (world_id, x, y)
);

CREATE TABLE world_membership (
    world_id  BIGINT      NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (world_id, user_id)
);
CREATE INDEX ix_world_membership_user ON world_membership (user_id);
