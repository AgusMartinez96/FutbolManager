-- Fútbol Manager v1 schema. Blueprint §4 is the contract; this file is that contract, applied
-- from step 1 so every later step's entities are validated (ddl-auto=validate) against it.
-- Forward-only: never edit this file once it has run anywhere. Changes go in V3+.

-- updated_at is maintained by the database, never by application code.
CREATE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TABLE users (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  username      text        NOT NULL,
  display_name  text        NOT NULL,
  password_hash text        NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_users_username CHECK (username ~ '^[A-Za-z0-9_.]{3,30}$'),
  CONSTRAINT ck_users_display_name CHECK (char_length(display_name) BETWEEN 1 AND 60)
);
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE groups (
  id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  name               text        NOT NULL,
  invite_code        text        NOT NULL,
  members_can_score  boolean     NOT NULL DEFAULT false,
  created_by_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_groups_name CHECK (char_length(name) BETWEEN 1 AND 60),
  CONSTRAINT ck_groups_invite_code CHECK (invite_code ~ '^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$')
);
CREATE UNIQUE INDEX uq_groups_invite_code ON groups (invite_code);
CREATE INDEX idx_groups_created_by_user_id ON groups (created_by_user_id);
CREATE TRIGGER trg_groups_updated_at BEFORE UPDATE ON groups
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE memberships (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id   uuid        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  role       text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_memberships_role CHECK (role IN ('ADMIN', 'MEMBER'))
);
CREATE UNIQUE INDEX uq_memberships_group_id_user_id ON memberships (group_id, user_id);
CREATE INDEX idx_memberships_user_id ON memberships (user_id);
CREATE TRIGGER trg_memberships_updated_at BEFORE UPDATE ON memberships
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE players (
  id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id           uuid        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  user_id            uuid        REFERENCES users (id) ON DELETE SET NULL,
  name               text        NOT NULL,
  nickname           text,
  preferred_position text        NOT NULL,
  dominant_foot      text        NOT NULL,
  current_elo        integer     NOT NULL,
  deleted_at         timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_players_name CHECK (char_length(name) BETWEEN 1 AND 60),
  CONSTRAINT ck_players_nickname CHECK (nickname IS NULL OR char_length(nickname) BETWEEN 1 AND 30),
  CONSTRAINT ck_players_preferred_position CHECK (preferred_position IN ('GK', 'DEF', 'MID', 'FWD')),
  CONSTRAINT ck_players_dominant_foot CHECK (dominant_foot IN ('RIGHT', 'LEFT', 'BOTH'))
);
CREATE INDEX idx_players_group_id ON players (group_id);
CREATE INDEX idx_players_user_id ON players (user_id);
CREATE UNIQUE INDEX uq_players_group_id_user_id ON players (group_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_players_ranking ON players (group_id, current_elo DESC) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_players_updated_at BEFORE UPDATE ON players
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE matches (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id      uuid        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  played_at     timestamptz NOT NULL,
  location      text        NOT NULL,
  format        integer     NOT NULL,
  status        text        NOT NULL DEFAULT 'SCHEDULED',
  score_a       integer,
  score_b       integer,
  mvp_player_id uuid        REFERENCES players (id) ON DELETE RESTRICT,
  closed_at     timestamptz,
  annulled_at   timestamptz,
  version       bigint      NOT NULL DEFAULT 0,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_matches_location CHECK (char_length(location) BETWEEN 1 AND 120),
  CONSTRAINT ck_matches_format CHECK (format IN (5, 7, 8, 11)),
  CONSTRAINT ck_matches_status CHECK (status IN ('SCHEDULED', 'LIVE', 'CLOSED', 'ANNULLED')),
  CONSTRAINT ck_matches_scores CHECK ((score_a IS NULL) = (score_b IS NULL))
);
CREATE INDEX idx_matches_group_id_played_at_id ON matches (group_id, played_at, id);
CREATE INDEX idx_matches_mvp_player_id ON matches (mvp_player_id);
CREATE TRIGGER trg_matches_updated_at BEFORE UPDATE ON matches
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE match_call_ups (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  match_id   uuid        NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
  player_id  uuid        NOT NULL REFERENCES players (id) ON DELETE RESTRICT,
  status     text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_match_call_ups_status CHECK (status IN ('CONFIRMED', 'MAYBE', 'NOT_GOING'))
);
CREATE UNIQUE INDEX uq_match_call_ups_match_id_player_id ON match_call_ups (match_id, player_id);
CREATE INDEX idx_match_call_ups_player_id ON match_call_ups (player_id);
CREATE TRIGGER trg_match_call_ups_updated_at BEFORE UPDATE ON match_call_ups
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE match_lineup_entries (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  match_id   uuid        NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
  player_id  uuid        NOT NULL REFERENCES players (id) ON DELETE RESTRICT,
  team       text        NOT NULL,
  position   text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_match_lineup_entries_team CHECK (team IN ('A', 'B')),
  CONSTRAINT ck_match_lineup_entries_position CHECK (position IN ('GK', 'DEF', 'MID', 'FWD'))
);
CREATE UNIQUE INDEX uq_match_lineup_entries_match_id_player_id ON match_lineup_entries (match_id, player_id);
CREATE INDEX idx_match_lineup_entries_player_id ON match_lineup_entries (player_id);
CREATE TRIGGER trg_match_lineup_entries_updated_at BEFORE UPDATE ON match_lineup_entries
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Append-only: rows are never updated except to void them (voided_at / voided_by_user_id),
-- so this table deliberately has no updated_at. id is generated by the client (idempotency key).
CREATE TABLE match_events (
  id                  uuid        PRIMARY KEY,
  match_id            uuid        NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
  type                text        NOT NULL,
  team                text        NOT NULL,
  player_id           uuid        REFERENCES players (id) ON DELETE RESTRICT,
  assist_player_id    uuid        REFERENCES players (id) ON DELETE RESTRICT,
  recorded_by_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  voided_at           timestamptz,
  voided_by_user_id   uuid        REFERENCES users (id) ON DELETE RESTRICT,
  created_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_match_events_type CHECK (type IN ('GOAL', 'SAVE', 'TACKLE')),
  CONSTRAINT ck_match_events_team CHECK (team IN ('A', 'B')),
  CONSTRAINT ck_match_events_player_required CHECK (type = 'GOAL' OR player_id IS NOT NULL),
  CONSTRAINT ck_match_events_assist_only_goal CHECK (assist_player_id IS NULL OR (type = 'GOAL' AND player_id IS NOT NULL AND assist_player_id <> player_id)),
  CONSTRAINT ck_match_events_void_pair CHECK ((voided_at IS NULL) = (voided_by_user_id IS NULL))
);
CREATE INDEX idx_match_events_match_id_created_at ON match_events (match_id, created_at);
CREATE INDEX idx_match_events_player_id ON match_events (player_id);
CREATE INDEX idx_match_events_assist_player_id ON match_events (assist_player_id);
CREATE INDEX idx_match_events_recorded_by_user_id ON match_events (recorded_by_user_id);
CREATE INDEX idx_match_events_voided_by_user_id ON match_events (voided_by_user_id);

-- ELO ledger. Written only through JdbcClient (PlayerService for INITIAL, EloReplayService for the
-- rest), never mapped as a JPA entity. Append-only per replay: rows are deleted and re-inserted by
-- EloReplayService, never updated, so there is no updated_at.
CREATE TABLE elo_changes (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id   uuid        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  player_id  uuid        NOT NULL REFERENCES players (id) ON DELETE CASCADE,
  match_id   uuid        REFERENCES matches (id) ON DELETE CASCADE,
  reason     text        NOT NULL,
  elo_before integer     NOT NULL,
  elo_after  integer     NOT NULL,
  delta      integer     NOT NULL,
  details    jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_elo_changes_reason CHECK (reason IN ('INITIAL', 'TEAM_RESULT', 'BONUS', 'MVP_BONUS')),
  CONSTRAINT ck_elo_changes_arithmetic CHECK (elo_after = elo_before + delta),
  CONSTRAINT ck_elo_changes_initial_has_no_match CHECK ((reason = 'INITIAL') = (match_id IS NULL))
);
CREATE INDEX idx_elo_changes_group_id ON elo_changes (group_id);
CREATE INDEX idx_elo_changes_player_id ON elo_changes (player_id);
CREATE INDEX idx_elo_changes_match_id ON elo_changes (match_id);
CREATE UNIQUE INDEX uq_elo_changes_match_id_player_id_reason ON elo_changes (match_id, player_id, reason) WHERE match_id IS NOT NULL;
CREATE UNIQUE INDEX uq_elo_changes_initial_player_id ON elo_changes (player_id) WHERE reason = 'INITIAL';
