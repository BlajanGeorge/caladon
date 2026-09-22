-- Per-city resource stocks (lazily settled) and the remaining free population.
CREATE TABLE city_resources (
    city_id    BIGINT        PRIMARY KEY REFERENCES city (id) ON DELETE CASCADE,
    wood       NUMERIC(14,3) NOT NULL,
    stone      NUMERIC(14,3) NOT NULL,
    iron       NUMERIC(14,3) NOT NULL,
    settled_at TIMESTAMPTZ   NOT NULL,  -- instant the stocks above were last settled to
    population INTEGER       NOT NULL CHECK (population >= 0)
);

-- Cities founded before this table existed start with the founding values.
INSERT INTO city_resources (city_id, wood, stone, iron, settled_at, population)
SELECT id, 500, 500, 500, now(), 100 FROM city;
