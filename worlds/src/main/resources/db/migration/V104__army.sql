-- Army: units held per city, studied unit types, and the per-city recruitment queue.
CREATE TABLE city_unit (
    city_id BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    unit    VARCHAR(16) NOT NULL,
    count   INTEGER     NOT NULL CHECK (count >= 0),
    PRIMARY KEY (city_id, unit)
);

-- A study is a timed one-time purchase; studied once completes_at <= now.
CREATE TABLE city_study (
    city_id      BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    unit         VARCHAR(16) NOT NULL,
    completes_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (city_id, unit)
);

CREATE TABLE city_recruit_order (
    id                BIGSERIAL   PRIMARY KEY,
    city_id           BIGINT      NOT NULL REFERENCES city (id) ON DELETE CASCADE,
    unit              VARCHAR(16) NOT NULL,
    count             INTEGER     NOT NULL CHECK (count >= 1),
    remaining         INTEGER     NOT NULL CHECK (remaining >= 0),
    ordered_at        TIMESTAMPTZ NOT NULL,
    next_completes_at TIMESTAMPTZ            -- set on the head order only; null while waiting behind another order
);
CREATE INDEX ix_city_recruit_order_city ON city_recruit_order (city_id, id);
CREATE INDEX ix_city_recruit_order_due ON city_recruit_order (next_completes_at) WHERE next_completes_at IS NOT NULL;
