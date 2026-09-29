-- Conquest by occupation (ARCHITECTURE.md -> Conquest): an attack that wins and still has a Nobleman
-- standing, with at least one other man beside him, does not turn around. It stays, and the city is held
-- for a fixed span before it changes hands. While it is held the city does nothing: no production, no
-- building, no recruiting, no studying. The garrison is ordinary support, owned by the cities that sent
-- it and hosted by the city it holds, so a counter-attack fights it exactly as it fights any defence.
ALTER TABLE city
    ADD COLUMN occupied_by_user_id BIGINT REFERENCES users (id),
    ADD COLUMN occupation_ends_at  TIMESTAMPTZ,
    -- Both or neither: a city is held by someone until some moment, or it is not held.
    ADD CONSTRAINT ck_city_occupation CHECK (num_nonnulls(occupied_by_user_id, occupation_ends_at) IN (0, 2));

-- The sweeper asks for cities whose hold is up, and there are never many.
CREATE INDEX ix_city_occupation ON city (occupation_ends_at) WHERE occupation_ends_at IS NOT NULL;
