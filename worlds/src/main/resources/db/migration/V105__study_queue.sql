-- The Academy studies one unit type at a time: city_study becomes a queue (studied = completed).
-- `applied` records that advance() has seen the completion (the sweeper selects only unapplied ones).
ALTER TABLE city_study ADD COLUMN ordered_at TIMESTAMPTZ, ADD COLUMN applied BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE city_study SET ordered_at = completes_at;
ALTER TABLE city_study ALTER COLUMN ordered_at SET NOT NULL;
CREATE INDEX ix_city_study_due ON city_study (completes_at) WHERE NOT applied;
