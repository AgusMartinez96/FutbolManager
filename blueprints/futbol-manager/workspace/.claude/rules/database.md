---
description: Flyway migrations, schema conventions and the ELO ledger
paths:
  - "src/main/resources/db/migration/**"
  - "src/main/java/com/futbolmanager/elo/**"
  - "src/main/java/com/futbolmanager/**/*.java"
---

# Database

- Flyway owns the schema. `V1__spring_session.sql` and `V2__core_schema.sql` already exist and have
  run: **never edit them**. Any schema change is a new `V{n}__{snake_case}.sql` (next free number).
- Hibernate runs with `ddl-auto: validate`. If validation fails, fix the entity mapping to match the
  SQL — never the other way round inside an existing migration.
- Tables plural snake_case; `id uuid default gen_random_uuid()` (except `match_events.id`, which the
  client generates); `created_at`/`updated_at timestamptz`, `updated_at` maintained by the
  `set_updated_at()` trigger. Enums are `text` + `CHECK`. Every FK has an index.
- Soft delete only on `players` (`deleted_at`). Rankings and pickers filter `deleted_at IS NULL`;
  history (lineups, events, ledger) keeps deleted players.
- `elo_changes` is the ledger. `INITIAL` rows are written once per player and are never touched by a
  replay. `TEAM_RESULT`/`BONUS` rows are deleted and re-inserted only by `EloReplayService`, inside one
  transaction that first runs `SELECT id FROM groups WHERE id = ? FOR UPDATE`.
- `players.current_elo` is a cache of the ledger: after any replay it must equal the player's INITIAL
  `elo_after` plus the sum of `delta` of their other rows.
- Matches are ordered by `(played_at, id)` everywhere ELO is concerned.
- Local DB: `docker compose up -d --wait db` (host port 5433). Reset: `docker compose down -v`.
