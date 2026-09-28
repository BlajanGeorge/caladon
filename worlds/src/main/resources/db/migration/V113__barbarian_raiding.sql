-- A barbarian village becomes a place worth attacking (ARCHITECTURE.md -> Raiding barbarians): it
-- defends with militia at its level and holds a store that refills over time, settled lazily on arrival
-- exactly as a city's production is. A fresh village is at level 1 with its store full.
ALTER TABLE barbarian_village
    ADD COLUMN level      INTEGER     NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 3),
    ADD COLUMN wood       BIGINT      NOT NULL DEFAULT 1000 CHECK (wood >= 0),
    ADD COLUMN stone      BIGINT      NOT NULL DEFAULT 1000 CHECK (stone >= 0),
    ADD COLUMN silver     BIGINT      NOT NULL DEFAULT 1000 CHECK (silver >= 0),
    -- When the store was last brought up to date, and when the village was last taken: the fall-back is
    -- measured from the second, and is null for a village nobody has taken yet.
    ADD COLUMN settled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN raided_at  TIMESTAMPTZ;

-- A movement may be aimed at a village instead of a city. Both ends stay one row: only what it is aimed
-- at differs, and it is one or the other, never both and never neither.
ALTER TABLE city_movement
    ALTER COLUMN target_city_id DROP NOT NULL,
    ADD COLUMN target_barbarian_id BIGINT REFERENCES barbarian_village (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_city_movement_target CHECK ((target_city_id IS NULL) <> (target_barbarian_id IS NULL));

CREATE INDEX ix_city_movement_barbarian ON city_movement (target_barbarian_id) WHERE NOT applied;
