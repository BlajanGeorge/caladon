-- The Cave's own silver, kept apart from the city's stock: it is what pays for this city's spying and
-- what another city's spies have to outbid (ARCHITECTURE.md -> Espionage and the Cave). Silver moves in
-- from the stock and only ever leaves by being spent, so it is one number beside the stocks.
ALTER TABLE city_resources
    ADD COLUMN cave_silver BIGINT NOT NULL DEFAULT 0 CHECK (cave_silver >= 0);
