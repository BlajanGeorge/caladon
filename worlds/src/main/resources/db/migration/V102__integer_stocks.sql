-- Resources v2: whole-unit stocks with a production clock per resource (ARCHITECTURE.md → Production model).
ALTER TABLE city_resources
    ALTER COLUMN wood  TYPE BIGINT USING floor(wood)::bigint,
    ALTER COLUMN stone TYPE BIGINT USING floor(stone)::bigint,
    ALTER COLUMN iron  TYPE BIGINT USING floor(iron)::bigint,
    ADD COLUMN wood_settled_at  TIMESTAMPTZ,
    ADD COLUMN stone_settled_at TIMESTAMPTZ,
    ADD COLUMN iron_settled_at  TIMESTAMPTZ;

-- Existing cities: clocks start now; population becomes the Tribal Wars level-1 Farm (240, all free).
UPDATE city_resources
   SET wood_settled_at = now(), stone_settled_at = now(), iron_settled_at = now(), population = 240;

ALTER TABLE city_resources
    ALTER COLUMN wood_settled_at  SET NOT NULL,
    ALTER COLUMN stone_settled_at SET NOT NULL,
    ALTER COLUMN iron_settled_at  SET NOT NULL,
    ADD CONSTRAINT ck_city_resources_wood  CHECK (wood  >= 0),
    ADD CONSTRAINT ck_city_resources_stone CHECK (stone >= 0),
    ADD CONSTRAINT ck_city_resources_iron  CHECK (iron  >= 0),
    DROP COLUMN settled_at;
