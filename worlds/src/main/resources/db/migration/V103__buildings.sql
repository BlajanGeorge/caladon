-- Buildings: one row per (city, building) with its level, and the per-city build queue.
CREATE TABLE city_building (
    city_id  BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    building VARCHAR(16) NOT NULL,
    level    INTEGER     NOT NULL CHECK (level >= 1),
    PRIMARY KEY (city_id, building)
);

-- Every city is founded with the six founded buildings at level 1; backfill existing cities.
INSERT INTO city_building (city_id, building, level)
SELECT c.id, f.building, 1
FROM city c
CROSS JOIN (VALUES ('FARM'), ('WOODCUTTER'), ('STONE_MINE'), ('IRON_MINE'), ('DEPOSIT'), ('TOWN_HALL')) AS f (building);

-- city.points is the maintained sum of points[level] over the city's buildings: 39 at founding.
UPDATE city SET points = 39;

CREATE TABLE city_build_order (
    id           BIGSERIAL   PRIMARY KEY,
    city_id      BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    building     VARCHAR(16) NOT NULL,
    target_level INTEGER     NOT NULL CHECK (target_level >= 1),
    ordered_at   TIMESTAMPTZ NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,   -- when it (will) start: the previous order's completion, or the order instant
    completes_at TIMESTAMPTZ NOT NULL,
    duration_ms  BIGINT      NOT NULL    -- fixed at order time (Town Hall level then); used to re-chain after a cancel
);
CREATE INDEX ix_city_build_order_city ON city_build_order (city_id, completes_at, id);
