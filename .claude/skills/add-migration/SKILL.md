---
name: add-migration
description: Add a database schema change to Fútbol Manager. Use when a table, column, index or constraint must be added or changed — "add a column", "new table", "change the schema", "migration".
---

# Add a Flyway migration

## When to use
Any change to the PostgreSQL schema. V1 and V2 have already run everywhere and are never edited.

## Steps
1. List `src/main/resources/db/migration/` and take the next free number `n` (V3 first, then V4, and so on).
2. Create `src/main/resources/db/migration/V{n}__{snake_case_description}.sql`.
3. Follow the conventions: plural snake_case tables, `uuid` ids with `gen_random_uuid()`,
   `timestamptz` with `updated_at` trigger (`set_updated_at()`), `text` + `CHECK` for enums, an index
   on every foreign key, `NOT NULL` unless the column is genuinely optional.
4. Destructive changes are expand → migrate → contract across separate releases: add the new column,
   backfill, switch reads, and drop the old column in a later migration.
5. Update the JPA entity so `ddl-auto: validate` passes.
6. Add or extend an integration test that exercises the new column through the API.

## Verify
```bash
./mvnw -q verify   # expect: exit 0 — Flyway applies V1..Vn to the Testcontainers postgres:17 and Hibernate validates
```

## Do not
- Edit a migration that already exists.
- Use `spring.jpa.hibernate.ddl-auto=update` to "fix" a mismatch.
- Map `elo_changes` as a JPA entity.
