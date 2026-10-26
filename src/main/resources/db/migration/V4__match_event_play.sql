ALTER TABLE match_events DROP CONSTRAINT ck_match_events_type;
ALTER TABLE match_events ADD CONSTRAINT ck_match_events_type CHECK (type IN ('GOAL', 'SAVE', 'TACKLE', 'PLAY'));
