-- What a player is left with after something happened while they were not looking (ARCHITECTURE.md ->
-- Reports). A report is a snapshot, written once and never recomputed, and it is never queried by its
-- contents — only read — so everything that varies by kind lives in one jsonb payload.
CREATE TABLE report (
    id            BIGSERIAL   PRIMARY KEY,
    world_id      BIGINT      NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    owner_user_id BIGINT      NOT NULL REFERENCES users (id),
    kind          VARCHAR(24) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    read          BOOLEAN     NOT NULL DEFAULT FALSE,
    -- The reader's own city, and the other end of what happened, as they were named at the time: a city
    -- can be renamed or lost, and the snapshot must still read the way it did.
    subject_city  VARCHAR(64) NOT NULL,
    other_city    VARCHAR(64),
    other_player  VARCHAR(64),
    -- Null where winning means nothing: only a battle has a side that won.
    won           BOOLEAN,
    summary       VARCHAR(255) NOT NULL,
    payload       JSONB       NOT NULL
);

-- The list: newest first for this player in this world, which is the only way reports are read.
CREATE INDEX ix_report_owner ON report (world_id, owner_user_id, created_at DESC, id DESC);

-- The unread count the top bar carries, on the few rows that are still unread.
CREATE INDEX ix_report_unread ON report (world_id, owner_user_id) WHERE NOT read;
