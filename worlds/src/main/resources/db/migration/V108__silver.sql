-- The third resource becomes silver: espionage is paid for in it (ARCHITECTURE.md → Espionage and the
-- Cave), and a mine that produces the coin the spies are paid with reads better than one producing iron.
-- Nothing changes but the names: the stocks, the rates and the costs are the same numbers.
ALTER TABLE city_resources RENAME COLUMN iron TO silver;
ALTER TABLE city_resources RENAME COLUMN iron_settled_at TO silver_settled_at;
ALTER TABLE city_resources RENAME CONSTRAINT ck_city_resources_iron TO ck_city_resources_silver;

ALTER TABLE city_movement RENAME COLUMN carried_iron TO carried_silver;

UPDATE city_building    SET building = 'SILVER_MINE' WHERE building = 'IRON_MINE';
UPDATE city_build_order SET building = 'SILVER_MINE' WHERE building = 'IRON_MINE';
