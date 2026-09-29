-- Fills one player's world with something worth looking at: a city that has been played for a while,
-- neighbours around it, troops on the road in every direction, and a few reports to read.
--
--   PGPASSWORD=caladon psql -h localhost -p 5433 -U caladon -d caladon \
--     -v ON_ERROR_STOP=1 -v player="'worldsmith@seed.test'" -f scripts/demo.sql
--
-- It is for screenshots and for looking at a screen with real numbers in it, nothing more. Everything it
-- writes is arithmetic the game itself would have produced: points from levels, population from the Farm
-- less what the buildings hold, capacity from the Deposit.

\if :{?player}
\else
  \set player '\'worldsmith@seed.test\''
\endif

BEGIN;

CREATE TEMP TABLE me ON COMMIT DROP AS
SELECT c.id AS city_id, c.world_id, c.owner_user_id, c.slot_id
FROM city c JOIN users u ON u.id = c.owner_user_id
WHERE u.email = :player
ORDER BY c.created_at LIMIT 1;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM me) THEN RAISE EXCEPTION 'That player holds no city; join a world first'; END IF;
END $$;

-- 1. Into the thick of it. The seeded players sit in a band along the top of the map, so the city moves
--    to the middle of that band and the map view opens on neighbours rather than on empty grass.
UPDATE city_slot s SET x = 266, y = 14 FROM me WHERE s.id = me.slot_id;
UPDATE city c SET name = 'Ravensgard' FROM me WHERE c.id = me.city_id;

-- 2. A city some months old: the Town Hall well up the ladder and the rest spread around it.
CREATE TEMP TABLE levels (building text, level int, p1 numeric, base_pop numeric, pop_factor numeric, founded boolean) ON COMMIT DROP;
INSERT INTO levels VALUES
  ('TOWN_HALL',   20, 10.0,  5.0, 1.170, true),
  ('FARM',        27,  5.0,  0.0, 1.000, true),
  ('WOODCUTTER',  21,  6.0,  5.0, 1.155, true),
  ('STONE_MINE',  20,  6.0, 10.0, 1.140, true),
  ('SILVER_MINE', 19,  6.0, 10.0, 1.170, true),
  ('DEPOSIT',     20,  6.0,  0.0, 1.150, true),
  ('BARRACKS',    18, 16.0,  7.0, 1.170, false),
  ('ACADEMY',     14, 19.0, 20.0, 1.170, false),
  ('WALL',        15,  8.0,  5.0, 1.170, false),
  ('VAULT',        8,  5.0,  2.0, 1.170, false),
  ('CAVE',        12,  7.0,  3.0, 1.170, false);

INSERT INTO city_building (city_id, building, level)
SELECT me.city_id, levels.building, levels.level FROM me, levels
ON CONFLICT (city_id, building) DO UPDATE SET level = EXCLUDED.level;

UPDATE city c SET points = (SELECT SUM(floor(p1 * power(1.2, level - 1) + 0.5))::int FROM levels)
FROM me WHERE c.id = me.city_id;

-- 3. An army, and every unit type studied so the Barracks and the Academy read as a played city.
CREATE TEMP TABLE army (unit text, count int, pop int) ON COMMIT DROP;
INSERT INTO army VALUES
  ('SPEARMAN', 1850, 1), ('SWORDSMAN', 1240, 1), ('ARCHER', 760, 1), ('AXEMAN', 2100, 1),
  ('SCOUT', 320, 2), ('LIGHT_CAV', 540, 4), ('HEAVY_CAV', 260, 6),
  ('RAM', 45, 5), ('CATAPULT', 30, 8), ('NOBLEMAN', 1, 100);

INSERT INTO city_unit (city_id, unit, count)
SELECT me.city_id, army.unit, army.count FROM me, army
ON CONFLICT (city_id, unit) DO UPDATE SET count = EXCLUDED.count;

INSERT INTO city_study (city_id, unit, ordered_at, completes_at, applied)
SELECT me.city_id, army.unit, now() - interval '10 days', now() - interval '9 days', true
FROM me, army WHERE army.unit <> 'SPEARMAN'
ON CONFLICT (city_id, unit) DO UPDATE SET completes_at = EXCLUDED.completes_at, applied = true;

-- 4. Stocks well along, and silver in the Cave to pay for spying.
UPDATE city_resources r SET
  wood = 31480, stone = 27650, silver = 19240,
  cave_silver = floor(600 * power(1.2294934, 11) + 0.5)::bigint,
  wood_settled_at = now(), stone_settled_at = now(), silver_settled_at = now(),
  -- The Farm feeds them all: an army this size is most of what a city's people are spent on.
  population = (SELECT floor(240 * power(100, (level - 1) / 29.0))::bigint FROM levels WHERE building = 'FARM')
    - (SELECT SUM(floor(base_pop * power(pop_factor, level - 1) + 0.5)
                  - CASE WHEN founded THEN base_pop ELSE 0 END) FROM levels)
    - (SELECT SUM(count * pop) FROM army)
FROM me WHERE r.city_id = me.city_id;

-- 5. Something in every queue, caught partway through.
DELETE FROM city_build_order   WHERE city_id IN (SELECT city_id FROM me);
DELETE FROM city_recruit_order WHERE city_id IN (SELECT city_id FROM me);

INSERT INTO city_build_order (city_id, building, target_level, ordered_at, started_at, completes_at, duration_ms)
SELECT me.city_id, b.building, b.level, now() - interval '20 minutes', now() - interval '20 minutes',
       now() + b.wait, EXTRACT(EPOCH FROM (b.wait + interval '20 minutes')) * 1000 FROM me,
  (VALUES ('WALL', 16, interval '38 minutes'),
          ('ACADEMY', 15, interval '3 hours 12 minutes'),
          ('SILVER_MINE', 20, interval '5 hours 40 minutes')) AS b(building, level, wait);

INSERT INTO city_recruit_order (city_id, unit, count, remaining, ordered_at, next_completes_at)
SELECT me.city_id, 'AXEMAN', 120, 87, now() - interval '1 hour', now() + interval '4 minutes' FROM me;
INSERT INTO city_recruit_order (city_id, unit, count, remaining, ordered_at, next_completes_at)
SELECT me.city_id, 'HEAVY_CAV', 40, 40, now() - interval '55 minutes', NULL FROM me;

INSERT INTO city_study (city_id, unit, ordered_at, completes_at, applied)
SELECT me.city_id, 'NOBLEMAN', now() - interval '2 hours', now() + interval '9 hours', false FROM me
ON CONFLICT (city_id, unit) DO UPDATE SET completes_at = EXCLUDED.completes_at, applied = false;

SELECT c.name, s.x, s.y, c.points, r.population,
       (SELECT count(*) FROM city_unit u WHERE u.city_id = c.id) AS unit_types
FROM city c JOIN city_slot s ON s.id = c.slot_id JOIN city_resources r ON r.city_id = c.id, me
WHERE c.id = me.city_id;

COMMIT;

-- 6. What the neighbours are sending back: one attack close enough to be made out, one still too far,
--    and a friend's troops on their way in. Written straight in, because the players who would have sent
--    them are seeded accounts nobody logs in as.
BEGIN;

CREATE TEMP TABLE me2 ON COMMIT DROP AS
SELECT c.id AS city_id, c.world_id FROM city c JOIN users u ON u.id = c.owner_user_id
WHERE u.email = :player ORDER BY c.created_at LIMIT 1;

DELETE FROM city_movement_unit WHERE movement_id IN (
  SELECT m.id FROM city_movement m, me2 WHERE m.target_city_id = me2.city_id AND m.origin_city_id <> me2.city_id);
DELETE FROM city_movement m USING me2
WHERE m.target_city_id = me2.city_id AND m.origin_city_id <> me2.city_id;

CREATE TEMP TABLE inbound (nick text, kind text, wait interval, units jsonb) ON COMMIT DROP;
INSERT INTO inbound VALUES
  ('Brona2',   'ATTACK',  interval '26 minutes', '{"AXEMAN": 640, "LIGHT_CAV": 180, "RAM": 14}'),
  ('Joric',    'ATTACK',  interval '3 hours 5 minutes', '{"AXEMAN": 1200, "HEAVY_CAV": 260, "CATAPULT": 20}'),
  ('Isolde',   'SUPPORT', interval '52 minutes', '{"SPEARMAN": 900, "SWORDSMAN": 320}');

WITH sent AS (
  INSERT INTO city_movement (world_id, origin_city_id, target_city_id, kind, direction, departs_at, arrives_at)
  SELECT me2.world_id, src.id, me2.city_id, inbound.kind, 'OUTWARD', now() - interval '35 minutes', now() + inbound.wait
  FROM me2, inbound
  JOIN users u ON u.nickname = inbound.nick
  JOIN city src ON src.owner_user_id = u.id AND src.world_id = (SELECT world_id FROM me2)
  RETURNING id, origin_city_id
)
INSERT INTO city_movement_unit (movement_id, unit, count)
SELECT sent.id, e.key, e.value::int
FROM sent
JOIN city src ON src.id = sent.origin_city_id
JOIN users u ON u.id = src.owner_user_id
JOIN inbound ON inbound.nick = u.nickname
CROSS JOIN LATERAL jsonb_each_text(inbound.units) AS e(key, value);

-- 7. A few reports to read: one attack won, one lost, one spy run that got in and one that was caught.
DELETE FROM report r USING me2 WHERE r.owner_user_id = (SELECT owner_user_id FROM city WHERE id = me2.city_id);

INSERT INTO report (world_id, owner_user_id, kind, created_at, read, subject_city, other_city, other_player, won, summary, payload)
SELECT me2.world_id, c.owner_user_id, 'BATTLE', now() - interval '3 hours', true,
       'Ravensgard', 'Dagna2''s city', 'Dagna2', true, 'Attack on Dagna2''s city',
       '{"role":"ATTACKER",
         "attacker":{"player":"worldsmith","city":"Ravensgard","points":1840,
           "units":[{"type":"AXEMAN","name":"Axeman","sent":800,"lost":131,"left":669},
                    {"type":"LIGHT_CAV","name":"Light Cavalry","sent":200,"lost":33,"left":167},
                    {"type":"RAM","name":"Ram","sent":15,"lost":2,"left":13}]},
         "defender":{"player":"Dagna2","city":"Dagna2''s city","points":410,
           "units":[{"type":"SPEARMAN","name":"Spearman","sent":900,"lost":900,"left":0},
                    {"type":"SWORDSMAN","name":"Swordsman","sent":460,"lost":460,"left":0},
                    {"type":"ARCHER","name":"Archer","sent":240,"lost":240,"left":0}]},
         "plunder":{"wood":9840,"stone":9840,"silver":6210},
         "wall":{"before":9,"after":6}}'::jsonb
FROM me2 JOIN city c ON c.id = me2.city_id;

INSERT INTO report (world_id, owner_user_id, kind, created_at, read, subject_city, other_city, other_player, won, summary, payload)
SELECT me2.world_id, c.owner_user_id, 'BATTLE', now() - interval '1 hour 10 minutes', false,
       'Ravensgard', 'Hrothgar''s city', 'Hrothgar', false, 'Attack on Hrothgar''s city',
       '{"role":"ATTACKER",
         "attacker":{"player":"worldsmith","city":"Ravensgard","points":0,
           "units":[{"type":"AXEMAN","name":"Axeman","sent":400,"lost":400,"left":0},
                    {"type":"LIGHT_CAV","name":"Light Cavalry","sent":60,"lost":60,"left":0}]},
         "defender":null, "plunder":null, "wall":null}'::jsonb
FROM me2 JOIN city c ON c.id = me2.city_id;

INSERT INTO report (world_id, owner_user_id, kind, created_at, read, subject_city, other_city, other_player, won, summary, payload)
SELECT me2.world_id, c.owner_user_id, 'BATTLE', now() - interval '22 minutes', false,
       'Ravensgard', 'Brona2''s city', 'Brona2', true, 'Attack on Ravensgard',
       '{"role":"DEFENDER",
         "attacker":{"player":"Brona2","city":"Brona2''s city","points":0,
           "units":[{"type":"AXEMAN","name":"Axeman","sent":520,"lost":520,"left":0},
                    {"type":"RAM","name":"Ram","sent":10,"lost":10,"left":0}]},
         "defender":{"player":"worldsmith","city":"Ravensgard","points":530,
           "units":[{"type":"SPEARMAN","name":"Spearman","sent":1250,"lost":96,"left":1154},
                    {"type":"SWORDSMAN","name":"Swordsman","sent":840,"lost":64,"left":776},
                    {"type":"ARCHER","name":"Archer","sent":510,"lost":39,"left":471}]},
         "plunder":null, "wall":{"before":15,"after":15}}'::jsonb
FROM me2 JOIN city c ON c.id = me2.city_id;

INSERT INTO report (world_id, owner_user_id, kind, created_at, read, subject_city, other_city, other_player, won, summary, payload)
SELECT me2.world_id, c.owner_user_id, 'ESPIONAGE', now() - interval '40 minutes', false,
       'Ravensgard', 'Joric''s city', 'Joric', true, 'Spying on Joric''s city',
       '{"success":true,"silver":1400,
         "seen":{"resources":{"wood":24180,"stone":19640,"silver":15020},
           "buildings":[{"type":"TOWN_HALL","level":18},{"type":"FARM","level":24},{"type":"BARRACKS","level":16},
                        {"type":"WALL","level":12},{"type":"DEPOSIT","level":18},{"type":"ACADEMY","level":11}],
           "units":[{"type":"SPEARMAN","name":"Spearman","count":1420},
                    {"type":"SWORDSMAN","name":"Swordsman","count":980},
                    {"type":"AXEMAN","name":"Axeman","count":1640},
                    {"type":"HEAVY_CAV","name":"Heavy Cavalry","count":210}]}}'::jsonb
FROM me2 JOIN city c ON c.id = me2.city_id;

INSERT INTO report (world_id, owner_user_id, kind, created_at, read, subject_city, other_city, other_player, won, summary, payload)
SELECT me2.world_id, c.owner_user_id, 'ESPIONAGE_CAUGHT', now() - interval '8 minutes', false,
       'Ravensgard', 'Cedric2''s city', 'Cedric2', true, 'A spy was caught in Ravensgard',
       '{"player":"Cedric2","city":"Cedric2''s city","silver":600}'::jsonb
FROM me2 JOIN city c ON c.id = me2.city_id;

COMMIT;
