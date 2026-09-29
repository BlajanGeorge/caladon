-- Makes a city's points what its buildings say they are, and gives the seeded cities buildings to say it
-- with (ARCHITECTURE.md -> Points).
--
--   PGPASSWORD=caladon psql -h localhost -p 5433 -U caladon -d caladon \
--     -v ON_ERROR_STOP=1 -f scripts/honest-points.sql
--
-- `city.points` is a counter the game keeps as levels complete, so it is only ever as true as the rows
-- it was counted from. Two things put it out: cities seeded straight into the database with a points
-- figure and no buildings at all, and building levels edited by hand without the counter following.
--
-- Part one invents buildings for a city that has none, chosen to land near the points it was seeded
-- with so the standings keep their order. Part two recounts every city from the levels it actually has.
-- The points of one building at level L are `P1 x 1.2^(L-1)`, rounded half up, as BuildingRules has it.

BEGIN;

-- What each building is worth at level 1, how far it goes, and the Town Hall it wants before it is
-- built at all. The spread keeps a city from reading as eleven buildings raised in step.
CREATE TEMP TABLE plan (building text, p1 numeric, max_level int, offset_from_hall int, wants_hall int) ON COMMIT DROP;
INSERT INTO plan VALUES
  ('TOWN_HALL',   10.0, 30,  0, 0),
  ('FARM',         5.0, 30, -1, 0),
  ('WOODCUTTER',   6.0, 30,  0, 0),
  ('STONE_MINE',   6.0, 30, -1, 0),
  ('SILVER_MINE',  6.0, 30, -1, 0),
  ('DEPOSIT',      6.0, 30, -2, 0),
  ('BARRACKS',    16.0, 25, -3, 3),
  ('ACADEMY',     19.0, 20, -6, 8),
  ('WALL',         8.0, 20, -4, 5),
  ('VAULT',        5.0, 10, -7, 5),
  ('CAVE',         7.0, 20, -5, 5);

-- 1. A city with no buildings at all. The whole set at level L is worth about 94 x 1.2^(L-1), so the
--    level that would have earned the seeded points is the inverse of that, held inside the ladder.
WITH bare AS (
  SELECT c.id, GREATEST(1, LEAST(30,
           round(1 + ln(GREATEST(c.points, 40)::numeric / 94) / ln(1.2))::int)) AS hall
  FROM city c
  WHERE NOT EXISTS (SELECT 1 FROM city_building b WHERE b.city_id = c.id)
)
INSERT INTO city_building (city_id, building, level)
SELECT bare.id, plan.building, LEAST(plan.max_level, GREATEST(1, bare.hall + plan.offset_from_hall))
FROM bare JOIN plan ON bare.hall >= plan.wants_hall;

-- 2. Every city's points, counted from the levels it has.
UPDATE city c
SET points = COALESCE((
  SELECT SUM(floor(plan.p1 * power(1.2, b.level - 1) + 0.5))::int
  FROM city_building b JOIN plan ON plan.building = b.building
  WHERE b.city_id = c.id AND b.level > 0
), 0);

COMMIT;

SELECT count(*) AS cities, min(points) AS lowest, max(points) AS highest, round(avg(points)) AS average
FROM city;
