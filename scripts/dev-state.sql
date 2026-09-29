-- Puts one development player back into a known state, and says nothing but what it changed.
--
--   PGPASSWORD=caladon psql -h localhost -p 5433 -U caladon -d caladon \
--     -v ON_ERROR_STOP=1 -f scripts/dev-state.sql
--
-- After it: the player is in one world (Caladon I) and holds two cities there, the second founded
-- exactly as joining founds one. Joins and cities in any other world are undone, which is the whole
-- point: a stray Join in the lobby drops you into an empty world with one city and nothing to play
-- against. Run it as often as you like — it only does what is missing.
--
-- The founding values come from CityAccess.found and Building.FOUNDED. If either changes, so does this.

\set player_email 'george@x.com'
\set home_world   'Caladon I'
\set second_city  'Northwatch'

BEGIN;

CREATE TEMP TABLE me AS
SELECT u.id AS user_id, w.id AS world_id
FROM users u, worlds w
WHERE u.email = :'player_email' AND w.name = :'home_world';

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM me) THEN
    RAISE EXCEPTION 'No such player or world: check the email and world name at the top of this file';
  END IF;
END $$;

-- 1. Everywhere else the player wandered into. An empty starter city goes with its membership; a city
--    that has been played in does not, so this refuses rather than throwing work away.
CREATE TEMP TABLE strays AS
SELECT c.id
FROM city c, me
WHERE c.owner_user_id = me.user_id AND c.world_id <> me.world_id;

DO $$
DECLARE busy bigint;
BEGIN
  SELECT c.id INTO busy FROM strays s JOIN city c ON c.id = s.id
  WHERE c.points > 39
     OR EXISTS (SELECT 1 FROM city_unit          x WHERE x.city_id = c.id)
     OR EXISTS (SELECT 1 FROM city_build_order   x WHERE x.city_id = c.id)
     OR EXISTS (SELECT 1 FROM city_recruit_order x WHERE x.city_id = c.id)
     OR EXISTS (SELECT 1 FROM city_study         x WHERE x.city_id = c.id)
     OR EXISTS (SELECT 1 FROM city_movement      x WHERE x.origin_city_id = c.id OR x.target_city_id = c.id)
  LIMIT 1;
  IF busy IS NOT NULL THEN
    RAISE EXCEPTION 'City % in another world has been played in; remove it yourself if you mean to', busy;
  END IF;
END $$;

DELETE FROM city_resources WHERE city_id IN (SELECT id FROM strays);
DELETE FROM city_building  WHERE city_id IN (SELECT id FROM strays);
DELETE FROM city           WHERE id      IN (SELECT id FROM strays);
DELETE FROM world_membership m USING me
  WHERE m.user_id = me.user_id AND m.world_id <> me.world_id;

-- 2. The home world itself, in case the membership is the thing that went missing.
INSERT INTO world_membership (world_id, user_id, joined_at)
SELECT me.world_id, me.user_id, now() FROM me
ON CONFLICT DO NOTHING;

-- 3. A second city, founded on the free slot nearest the first: the six FOUNDED buildings at level 1,
--    500 of each resource, the Farm's own population, and the points those six are worth.
WITH home AS (
  SELECT c.id, s.x, s.y FROM city c JOIN city_slot s ON s.id = c.slot_id, me
  WHERE c.owner_user_id = me.user_id AND c.world_id = me.world_id
  ORDER BY c.created_at LIMIT 1
), wanted AS (
  SELECT s.id AS slot_id
  FROM city_slot s LEFT JOIN city c ON c.slot_id = s.id, home, me
  WHERE s.world_id = me.world_id AND c.id IS NULL
    AND (SELECT count(*) FROM city x, me m WHERE x.owner_user_id = m.user_id AND x.world_id = m.world_id) < 2
  ORDER BY (s.x - home.x) ^ 2 + (s.y - home.y) ^ 2
  LIMIT 1
), founded AS (
  INSERT INTO city (world_id, slot_id, owner_user_id, name, points, created_at)
  SELECT me.world_id, wanted.slot_id, me.user_id, :'second_city', 39, now() FROM wanted, me
  RETURNING id
), stocked AS (
  INSERT INTO city_resources (city_id, wood, stone, silver, population,
                              wood_settled_at, stone_settled_at, silver_settled_at, cave_silver)
  SELECT id, 500, 500, 500, 240, now(), now(), now(), 0 FROM founded
)
INSERT INTO city_building (city_id, building, level)
SELECT founded.id, b, 1 FROM founded,
  unnest(ARRAY['FARM','WOODCUTTER','STONE_MINE','SILVER_MINE','DEPOSIT','TOWN_HALL']) AS b;

COMMIT;

SELECT w.name AS world, c.name AS city, s.x, s.y, c.points
FROM city c JOIN city_slot s ON s.id = c.slot_id JOIN worlds w ON w.id = c.world_id, me
WHERE c.owner_user_id = me.user_id
ORDER BY c.created_at;
