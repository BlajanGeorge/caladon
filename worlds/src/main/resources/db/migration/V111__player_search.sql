-- Searching the ranking by name should survive a typo, not just a shortened name, so the nickname gets
-- trigram matching and an index to do it on (ARCHITECTURE.md -> Ranking).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_users_nickname_trgm ON users USING gin (nickname gin_trgm_ops);
