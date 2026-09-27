-- A player's standing in a world. City points are read from the cities themselves; what needs keeping is
-- what battles have earned: the population a player has killed attacking, and the population killed
-- defending, its own troops and the support it lent alike (ARCHITECTURE.md -> Ranking).
CREATE TABLE player_score (
    world_id       BIGINT NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    user_id        BIGINT NOT NULL REFERENCES users(id),
    attack_points  BIGINT NOT NULL DEFAULT 0 CHECK (attack_points >= 0),
    defence_points BIGINT NOT NULL DEFAULT 0 CHECK (defence_points >= 0),
    PRIMARY KEY (world_id, user_id)
);
