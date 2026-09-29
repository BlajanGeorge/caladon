-- Raises one city to the top of every ladder, for looking at a city that has everything.
--
--   PGPASSWORD=caladon psql -h localhost -p 5433 -U caladon -d caladon \
--     -v ON_ERROR_STOP=1 -v city=151 -f scripts/max-city.sql
--
-- Every building goes to its own maximum, and the city's points, free population and stocks are
-- recounted to match. The arithmetic is BuildingRules': points are `P1 x 1.2^(L-1)`, the population a
-- building holds is `base x factor^(L-1)`, capacity is `1000 x 1.2294934^(L-1)` and the Farm's people
-- are `floor(240 x 100^((L-1)/29))` — each rounded the way Tw.curve rounds, half up.
--
-- Level 1 of a founded building costs no population, which is why what a building has taken is its
-- total at its level less its total at level 1.

\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE plan (building text, max_level int, p1 numeric, base_pop numeric, pop_factor numeric, founded boolean) ON COMMIT DROP;
INSERT INTO plan VALUES
  ('FARM',        30,  5.0,  0.0, 1.000, true),
  ('WOODCUTTER',  30,  6.0,  5.0, 1.155, true),
  ('STONE_MINE',  30,  6.0, 10.0, 1.140, true),
  ('SILVER_MINE', 30,  6.0, 10.0, 1.170, true),
  ('DEPOSIT',     30,  6.0,  0.0, 1.150, true),
  ('TOWN_HALL',   30, 10.0,  5.0, 1.170, true),
  ('BARRACKS',    25, 16.0,  7.0, 1.170, false),
  ('ACADEMY',     20, 19.0, 20.0, 1.170, false),
  ('WALL',        20,  8.0,  5.0, 1.170, false),
  ('VAULT',       10,  5.0,  2.0, 1.170, false),
  ('CAVE',        20,  7.0,  3.0, 1.170, false);

-- psql leaves :city alone inside a dollar-quoted body, so the city to check goes through a table.
CREATE TEMP TABLE target ON COMMIT DROP AS SELECT :city::bigint AS id;
DO $$
DECLARE wanted bigint;
BEGIN
  SELECT id INTO wanted FROM target;
  IF NOT EXISTS (SELECT 1 FROM city WHERE id = wanted) THEN
    RAISE EXCEPTION 'No city %', wanted;
  END IF;
END $$;

-- Nothing may be left in the queue: it would complete onto levels that are already at the top.
DELETE FROM city_build_order WHERE city_id = :city;

INSERT INTO city_building (city_id, building, level)
SELECT :city, plan.building, plan.max_level FROM plan
ON CONFLICT (city_id, building) DO UPDATE SET level = EXCLUDED.level;

UPDATE city SET points = (
  SELECT SUM(floor(plan.p1 * power(1.2, plan.max_level - 1) + 0.5))::int FROM plan
) WHERE id = :city;

UPDATE city_resources r SET
  wood   = floor(1000 * power(1.2294934, 29) + 0.5)::bigint,
  stone  = floor(1000 * power(1.2294934, 29) + 0.5)::bigint,
  silver = floor(1000 * power(1.2294934, 29) + 0.5)::bigint,
  cave_silver = floor(600 * power(1.2294934, 19) + 0.5)::bigint,
  wood_settled_at = now(), stone_settled_at = now(), silver_settled_at = now(),
  -- What the Farm feeds, less what the buildings hold and what the troops already eat.
  population = floor(240 * power(100, 29 / 29.0))::bigint
    - (SELECT COALESCE(SUM(floor(plan.base_pop * power(plan.pop_factor, plan.max_level - 1) + 0.5)
                          - CASE WHEN plan.founded THEN plan.base_pop ELSE 0 END), 0) FROM plan)
    - (SELECT COALESCE(SUM(u.count * un.pop), 0) FROM city_unit u
       JOIN (VALUES ('SPEARMAN',1),('SWORDSMAN',1),('AXEMAN',1),('ARCHER',1),('SCOUT',2),
                    ('LIGHT_CAVALRY',4),('MOUNTED_ARCHER',5),('HEAVY_CAVALRY',6),
                    ('RAM',5),('CATAPULT',8),('NOBLEMAN',100)) AS un(type, pop) ON un.type = u.unit
       WHERE u.city_id = :city)
WHERE r.city_id = :city;

COMMIT;

SELECT c.name, c.points, r.wood, r.stone, r.silver, r.cave_silver, r.population
FROM city c JOIN city_resources r ON r.city_id = c.id WHERE c.id = :city;
