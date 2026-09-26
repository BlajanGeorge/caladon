-- Troops of one city stationed in another: the owner keeps them, the host shelters them.
-- A city's own garrison stays in city_unit; this table is only for troops away from home.
CREATE TABLE city_support (
    host_city_id  BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    owner_city_id BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    unit          VARCHAR(16) NOT NULL,
    count         INTEGER     NOT NULL CHECK (count > 0),
    PRIMARY KEY (host_city_id, owner_city_id, unit)
);

CREATE INDEX idx_city_support_owner ON city_support (owner_city_id);
