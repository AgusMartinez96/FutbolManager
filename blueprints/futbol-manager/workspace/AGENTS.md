# Fútbol Manager — agent instructions

App web (Spring Boot 4 + React/Vite) para organizar partidos de fútbol amateur entre amigos: grupos, ranking ELO, armado de equipos y marcador en vivo.

## Commands

Run from the project root in a POSIX shell (Git Bash on Windows).

| Task | Command |
|---|---|
| Local DB | `docker compose up -d --wait db` |
| Backend tests (needs Docker) | `./mvnw -q verify` · one class: `./mvnw -q test -Dtest=<Class>` |
| Package jar | `./mvnw -q -DskipTests package` |
| Frontend install | `npm --prefix frontend install` |
| Frontend gates | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` |
| Jar smoke | `bash scripts/smoke.sh` |
| Docker | `docker build -t futbol-manager:local .` · `bash scripts/docker-smoke.sh` |
| E2E | `npm --prefix frontend run e2e:install` · `npm --prefix frontend run test:e2e` |

## Non-negotiable

1. Another group's data returns 404, never 403; every group query filters by `groupId`.
2. Never edit existing Flyway migrations; add a new `V{n}__*.sql`.
3. ELO is written only by `EloReplayService`; INITIAL rows are never recomputed.
4. No default passwords; secrets live only in `.env` (local) or the host's env.
5. Never commit `.env`, `target/`, `src/main/resources/static/` or `node_modules/`.
6. Do not build anything in blueprint §1 Non-Goals.
7. Never mark a task done with a failing gate, and never edit a verify command.

Full architecture, boundaries and design tokens: see `CLAUDE.md` in this directory. Build order: `blueprints/futbol-manager/tasks.json`.
