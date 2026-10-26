ALTER TABLE players ADD COLUMN level_tenths integer;
UPDATE players SET level_tenths = level * 10;
ALTER TABLE players ALTER COLUMN level_tenths SET NOT NULL;
ALTER TABLE players ADD CONSTRAINT ck_players_level_tenths CHECK (level_tenths BETWEEN 10 AND 100);
ALTER TABLE players DROP COLUMN level;
