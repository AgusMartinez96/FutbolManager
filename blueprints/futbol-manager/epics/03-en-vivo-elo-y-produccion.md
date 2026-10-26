# Epic 03: En vivo, ELO y producción

> Al terminar existen las pantallas de partido y armado de equipos, el modo en vivo idempotente con polling, el cierre con libro ELO y recálculo determinístico, ranking y estadísticas, headers de producción, la demo sembrada, los E2E con axe y el runbook de deploy.

| | |
|---|---|
| **Epic id** | `03-en-vivo-elo-y-produccion` |
| **Tasks** | `E3-T1` … `E3-T6` |
| **Depends on** | `01-fundaciones-y-cuentas`, `02-grupos-jugadores-y-motores`; E3-T4…E3-T6 además dependen de tareas del epic `04-arqueros-rotativos-nivel-y-jugadon` (que a su vez depende de E3-T1…E3-T3) |
| **Unlocks** | nothing — el build termina acá (después, §20.1 de `blueprint.md`) |
| **Parallel with** | ninguno |

You do not need any other file to complete this epic. Everything below is repeated here on purpose.

---

## Stack

Spring Boot 4.1 (Spring MVC, Security 7, Data JPA/Hibernate 7, Session JDBC, Flyway) · Java 21 · React 19 + TypeScript 6 + Vite 8 · Tailwind CSS 4 · TanStack Query 5 · React Router 7 · `radix-ui` · PostgreSQL 17 · Playwright + axe · Docker → Render Free + Neon.
Package managers: Maven Wrapper (`./mvnw`) y npm (`frontend/package-lock.json`). Node fijado en `.nvmrc` (24). Versiones en `pom.xml` y el lockfile — leelas, nunca adivines una.

Todos los comandos corren en **Git Bash** desde la raíz del proyecto.

| Task | Command |
|---|---|
| DB local | `docker compose up -d --wait db` / `docker compose down` |
| Backend: todo | `./mvnw -q verify` (requiere Docker) |
| Backend: una clase | `./mvnw -q test -Dtest=<Clase>` |
| Package jar | `./mvnw -q -DskipTests package` |
| Smoke del jar | `bash scripts/smoke.sh` · con demo: `SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh` |
| Frontend | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` |
| E2E | `npm --prefix frontend run e2e:install` (una vez) · `npm --prefix frontend run test:e2e` |

**Gate:** `./mvnw -q verify` y los cinco comandos de frontend en verde antes de marcar una tarea como hecha.

La base de compose (`postgres:17`, host **5433**, credenciales en `.env`) la usan `scripts/smoke.sh` y Playwright; `frontend/playwright.config.ts` ya existe y arranca `java -jar target/futbol-manager.jar` con `cwd` en la raíz (así carga `.env`).

## Directory subtree

```
src/main/java/com/futbolmanager/
  config/SecurityConfig.java             # exists — edited (E3-T6): CSP de fm.security.content-security-policy
  config/DemoDataSeeder.java             # NEW (E3-T6)
  group/GroupAccess.java, group/GroupController.java # exist — read-only
  player/Player.java, player/PlayerService.java       # exist — read-only
  elo/EloCalculator.java                 # exists — read-only
  elo/EloReplayService.java              # NEW (E3-T4)
  match/Match.java, MatchCallUp.java, LineupEntry.java, MatchController.java # exist — read-only
  match/balance/TeamBalancer.java        # exists — read-only
  match/MatchLifecycleController.java    # NEW (E3-T4)
  live/MatchEvent.java                   # NEW (E3-T2)
  live/LiveController.java               # NEW (E3-T2), edited (E3-T4)
  stats/StatsController.java             # NEW (E3-T5)
src/main/resources/application-prod.yml  # exists — read-only (define la CSP y apaga springdoc)
src/test/java/com/futbolmanager/
  live/LiveIntegrationTest.java          # NEW (E3-T2)
  elo/EloReplayIntegrationTest.java      # NEW (E3-T4)
  elo/EloReplayPropertyTest.java         # NEW (E3-T4)
  stats/StatsIntegrationTest.java        # NEW (E3-T5)
  config/ProdProfileIntegrationTest.java # NEW (E3-T6)
frontend/
  playwright.config.ts                   # exists — read-only
  e2e/flows.spec.ts                      # NEW (E3-T6)
  src/app/router.tsx                     # exists — edited (E3-T1, E3-T3, E3-T5)
  src/features/matches/api.ts            # NEW (E3-T1)
  src/features/matches/MatchesPages.tsx  # NEW (E3-T1)
  src/features/matches/MatchSetupPage.tsx # NEW (E3-T1)
  src/features/matches/MatchSetupPage.test.tsx # NEW (E3-T1)
  src/features/live/api.ts               # NEW (E3-T3)
  src/features/live/LivePage.tsx         # NEW (E3-T3), edited (E3-T5)
  src/features/live/LivePage.test.tsx    # NEW (E3-T3)
  src/features/stats/StatsPages.tsx      # NEW (E3-T5)
README.md                                # NEW (E3-T6)
```

Everything outside this subtree is out of scope. If a task seems to require editing a file not listed here, stop and report — it means the epic boundary is wrong.

## Data model touched here

| Entity | Fields this epic adds or reads | Notes |
|---|---|---|
| `match_events` | `id` (UUID **del cliente**, PK), `match_id`, `type` (`GOAL`/`SAVE`/`TACKLE`/`PLAY`; `PLAY` desde V4), `team` (`A`/`B`), `player_id` (obligatorio salvo GOAL), `assist_player_id` (solo GOAL con autor, ≠ autor), `recorded_by_user_id`, `voided_at`, `voided_by_user_id`, `created_at` | append-only; sin `updated_at`; entidad `MatchEvent implements Persistable<UUID>` |
| `elo_changes` | `group_id`, `player_id`, `match_id`, `reason` (`TEAM_RESULT`/`BONUS`; `INITIAL` existe y no se toca; `MVP_BONUS` reservado), `elo_before`, `elo_after`, `delta`, `details jsonb` | solo `JdbcClient`; únicos `(match_id, player_id, reason)`; `details` en BONUS: `{"goals":n,"assists":n,"mvp":0|3,"cleanSheet":0|2,"saves":0..3,"tackles":0..3,"plays":0..2,"uncapped":n,"total":n}` (puntos) |
| `matches` | `status`, `score_a`, `score_b`, `mvp_player_id`, `closed_at`, `annulled_at` | marcador cacheado al cerrar |
| `players` | `current_elo` | caché del libro: INITIAL.elo_after + Σ delta de sus otras filas |
| `match_lineup_entries`, `match_call_ups`, `groups`, `memberships`, `users` | lectura | |

## Contracts

**Consumed** — already exists, do not rebuild:

| From | Interface | Guarantee |
|---|---|---|
| `01` | `ApiException`, `ApiExceptionHandler`, `AuthUser`, `ApiTestClient`, `TestcontainersConfiguration` | ver epic 01 |
| `01` | `apiFetch`, `ApiError`, `RequireAuth`, `Button` (`size="live"` = `min-h-16`), `Field`, `Card`, `Alert`, `createAppRouter` | ver epic 01 |
| `02` | `GroupAccess.requireMember/requireAdmin` | 404 no miembro / 403 sin rol |
| `02` | `EloCalculator.calculate(MatchInput)` → `List<PlayerResult(playerId, teamDelta, bonus, BonusBreakdown)>` | puro |
| `02` | `TeamBalancer.balance(candidates)` (por nivel, tras E4-T3) | puro |
| `02` | `Match.Repo.findByIdAndGroupId`, `LineupEntry.Repo.findAllByMatchId`, `Player.Repo`, `PlayerService.create(...)`, `GroupController.MemberJoined` | |
| `02` | REST de grupos, jugadores (`{id, name, nickname, preferredPosition, dominantFoot, currentElo, userId}` y `level` solo para ADMIN, tras E4-T2; decimal con un decimal, p. ej. `8.5`, tras E4-T9), partidos, convocatoria, `balance`, `lineup`, `start`; `GET /api/groups/{groupId}` → `{…, myRole, myUserId}` | |
| `02` | `frontend/src/features/groups/api.ts` `useGroup`, `usePlayers` | claves `['groups', groupId, …]` |

**Produced** — later work depends on exactly these signatures:

| Export | Signature | Used by |
|---|---|---|
| `elo/EloReplayService` | `@Transactional void replayFrom(UUID groupId, UUID matchId)` | `MatchLifecycleController`, `LiveController`, `DemoDataSeeder`, Fase B |
| REST en vivo | `GET …/live` → `{matchId, status, scoreA, scoreB, canRecord, isAdmin, myUserId, events:[{id, type, team, playerId, playerName, assistPlayerId, assistPlayerName, recordedBy:{userId, displayName}, createdAt, voidedAt}]}`; `POST …/events` `{id, type, team, playerId?, assistPlayerId?}`; `POST …/events/{eventId}/void` | frontend |
| REST cierre | `POST …/close` `{mvpPlayerId?}` → `{scoreA, scoreB, mvpPlayerId, eloChanges:[{playerId, name, eloBefore, eloAfter, delta}]}`; `PUT …/mvp` `{mvpPlayerId}`; `POST …/annul` | frontend |
| REST stats | `GET /api/groups/{groupId}/ranking` → `[{playerId, name, nickname, currentElo, played, provisional}]`; `GET …/players/{playerId}/stats` → `{playerId, name, played, won, drawn, lost, goals, assists, mvps, streak:{type, length}|null, currentElo, provisional}`; `GET …/players/{playerId}/elo-history` → `[{matchId, playedAt, eloAfter}]` | frontend |

(`…` en esta tabla abrevia `/api/groups/{groupId}/matches/{matchId}`.)

## Conventions that bite in this area

- **Eventos (E3-T2), reglas exactas:** 404 si no sos miembro o el partido no es del grupo; 409 en `SCHEDULED`/`ANNULLED`; en `LIVE` registran ADMIN o MEMBER si `members_can_score` (si no 403); en `CLOSED` solo ADMIN. Idempotencia: mismo `id` + mismo `(type, team, playerId, assistPlayerId)` → 200 con el existente; mismo `id` con otro payload u otro partido → 409. 422: GOAL con autor fuera del lineup de `team`, asistencia sin autor/fuera del lineup/igual al autor; SAVE, TACKLE o PLAY de alguien fuera del lineup de `team` o sin jugador, y PLAY con asistencia (desde E4-T4 la atajada ya no exige ser el `GK`; en E3-T2 sí lo exigía). Anular: autor o admin en `LIVE`, solo admin en `CLOSED`; ya anulado → 200 sin cambios. Marcador = GOAL no anulados por equipo, siempre derivado.
- **ELO replay (E3-T4), algoritmo exacto, una transacción:** (1) `SELECT id FROM groups WHERE id = ? FOR UPDATE`; (2) pivote = `(played_at, id)` del partido; (3) borrar filas `TEAM_RESULT`/`BONUS`/`MVP_BONUS` de los partidos del grupo con `(played_at, id) >= pivote`; (4) `players.current_elo` = INITIAL.elo_after + Σ delta de sus filas restantes, para todo jugador del grupo; (5) para cada partido `CLOSED` con `(played_at, id) >= pivote`, en orden: lineup, `current_elo` vigente como `eloBefore`, `previousCountedMatches` = partidos `CLOSED` anteriores (por `(played_at, id)`) en cuyo lineup está, agregados de eventos no anulados (goles y asistencias por jugador, atajadas, cortes, jugadones, marcador), MVP → `EloCalculator` → insertar `TEAM_RESULT` (`elo_before` = vigente, `delta` = teamDelta) y, si `bonus > 0`, `BONUS` (`elo_before` = `TEAM_RESULT.elo_after`, `details` con `?::jsonb`) → `UPDATE players SET current_elo`. `INITIAL` nunca se toca. Cerrar **es** `replayFrom(groupId, matchId)`. Partidos `ANNULLED` no generan filas.
- **Cierre:** `LIVE` → `CLOSED` solo admin; `score_a`/`score_b` desde eventos; MVP opcional del lineup (si no, 422); `closed_at = now()`. Anular: `SCHEDULED` → `ANNULLED`; `CLOSED` → `ANNULLED` + replay; otro → 409.
- **Estadísticas (E3-T5):** sobre partidos `CLOSED` donde el jugador está en el lineup: `played/won/drawn/lost`; `goals`/`assists` de eventos no anulados de esos partidos; `mvps`; `streak` desde el último partido hacia atrás mientras se repita el resultado (`W`/`D`/`L`), `null` sin partidos; `provisional` = `played < 10`. Historial: primero `{matchId:null, playedAt:null, eloAfter: INITIAL}`, luego uno por partido cerrado (orden `(played_at, id)`) con el valor tras `TEAM_RESULT` + `BONUS`. Ranking: no borrados por `current_elo DESC, name ASC`.
- **Polling:** `refetchInterval: 5000`, `refetchIntervalInBackground: false`; sin WebSockets.
- **Idempotencia en el cliente:** `crypto.randomUUID()` una vez por toque; los reintentos (hasta 3) reusan el mismo `id`.
- **a11y:** sin arrastre (botones `Pasar al equipo A/B`); `select` de puesto con etiqueta `Puesto de {nombre}`; botones de registro `min-h-16`; marcador `role="status"`; anulados tachados **y** con texto "anulado"; deltas de ELO con signo y texto, no solo color; `scroll-pt-28` bajo el marcador sticky.
- **Headers de prod (E3-T6):** `SecurityConfig` agrega `Content-Security-Policy` solo si `fm.security.content-security-policy` no está vacío (lo define `application-prod.yml`: `default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'`); `Referrer-Policy`, `nosniff` y `X-Frame-Options: DENY` ya estaban.
- **Demo:** `@Profile("demo")`, lee `DEMO_PASSWORD` (está en `.env`), idempotente por nombre de grupo `Los del jueves`; `Random(42)`.

Full project rules: `CLAUDE.md`. Area rules: `.claude/rules/backend-java.md`, `.claude/rules/database.md`, `.claude/rules/frontend.md`, `.claude/rules/testing.md`. Both sit in the project root — the builder copied them there from the bundle's `workspace/` before task one.

---

## Tasks

Listed in the same order as `tasks.json`. That order is the build order — work top to bottom and do not re-rank by priority or by what looks quick.

### `E3-T1` — Build match, call-up and team builder screens

> **Superseded en parte por E4-T6 quita «Un arquero por equipo» y muestra el nivel promedio (solo admin).** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E2-T3`, `E2-T6` · **Priority:** p0 — metadata for scope cuts, not a running order

`api.ts`: `useMatches`, `useCreateMatch`, `useMatch`, `useSetCallUp`, `useBalance` (mutación; no invalida), `useSaveLineup`, `useStartMatch`. `MatchesPage` (`h1` "Partidos"; lista con fecha `es-AR`, lugar, formato, estado; esqueleto / vacío `No hay partidos todavía.` + (admin) `Crear partido` / error + `Reintentar`; alta con `datetime-local` → `new Date(value).toISOString()`, lugar, formato 5/7/8/11). `MatchSetupPage`: `h1` con fecha y lugar; convocatoria con un `select` `Asistencia de {nombre}` por jugador (MEMBER solo el suyo, identificado por `userId` = `myUserId`); armado (admin): checkbox `Un arquero por equipo`, `Armar equipos`, Equipo A/B con ELO promedio y `Diferencia: N`, botones `Pasar al equipo B`/`Pasar al equipo A`, `select` `Puesto de {nombre}` (ARQ/DEF/MED/DEL), `Guardar equipos`, `Empezar partido` → `/grupos/{groupId}/partidos/{matchId}/en-vivo`. Router: `/grupos/:groupId/partidos` y `/grupos/:groupId/partidos/:matchId`.

**Files**
- `frontend/src/features/matches/api.ts` — new
- `frontend/src/features/matches/MatchesPages.tsx` — new
- `frontend/src/features/matches/MatchSetupPage.tsx` — new
- `frontend/src/app/router.tsx` — edit: rutas de partidos
- `frontend/src/features/matches/MatchSetupPage.test.tsx` — new

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** la API simulada de partidos devuelve `[]` para un admin **THE SYSTEM SHALL** mostrar `No hay partidos todavía.` y la acción `Crear partido`.
2. **WHEN** el admin envía el formulario de crear partido **THE SYSTEM SHALL** hacer `POST /api/groups/{groupId}/matches` con `playedAt` en ISO-8601, `location` y `format`, y navegar a `/grupos/{groupId}/partidos/{matchId}`.
3. **WHEN** se activa `Armar equipos` **THE SYSTEM SHALL** llamar a `POST /api/groups/{groupId}/matches/{matchId}/balance` y mostrar ambos equipos con su ELO promedio y la diferencia.
4. **WHEN** se activa con el teclado el botón `Pasar al equipo B` de un jugador **THE SYSTEM SHALL** moverlo de equipo sin arrastrar, y cada select de puesto **SHALL** tener un nombre accesible que incluye el nombre del jugador.
5. **WHEN** se activa `Guardar equipos` **THE SYSTEM SHALL** hacer `PUT /api/groups/{groupId}/matches/{matchId}/lineup` con una entrada `{playerId, team, position}` por jugador.
6. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T1: build match, call-up and team builder screens"
git tag step-13-match-setup-ui
```

Run both after the last `Verify` command exits 0, before starting the next task. The tag is this task's rollback target and the thing the build's final gate counts.

### `E3-T2` — Add idempotent live events API

> **Superseded en parte por E4-T4 agrega el tipo `PLAY` y permite atajadas de cualquier jugador.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E2-T6` · **Priority:** p0

`MatchEvent` (`@Id UUID id` asignado por el cliente, `implements Persistable<UUID>` con `isNew()` true hasta persistir, `enum Type`, sin `updated_at`; `Repo` con `findAllByMatchIdOrderByCreatedAtAsc`). `LiveController` con `GET …/live`, `POST …/events`, `POST …/events/{eventId}/void` y las reglas exactas de *Conventions* (en este paso no hay partidos `CLOSED` todavía: el cierre llega en E3-T4). El test arma grupo, jugadores, partido, lineup (con un `GK` por equipo) y `start` por API.

**Files**
- `src/main/java/com/futbolmanager/live/MatchEvent.java` — new
- `src/main/java/com/futbolmanager/live/LiveController.java` — new
- `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` — new

**Acceptance**

1. **WHEN** un admin envía un `GOAL` con un `id` UUID nuevo a un partido `LIVE` **THE SYSTEM SHALL** responder 201 y sumar 1 al marcador de ese equipo en `GET /api/groups/{groupId}/matches/{matchId}/live`; **WHEN** reenvía el mismo `id` con el mismo payload **THE SYSTEM SHALL** responder 200 sin cambiar el marcador; **WHEN** reenvía el mismo `id` con otro payload **THE SYSTEM SHALL** responder 409.
2. **WHEN** se envía un `SAVE` de un jugador que no es el `GK` por partido de ese equipo, o un `TACKLE` de un jugador que no está en el lineup de ese equipo **THE SYSTEM SHALL** responder 422.
3. **WHEN** un `MEMBER` envía un evento con `membersCanScore` false **THE SYSTEM SHALL** responder 403, y **WHEN** `membersCanScore` es true **THE SYSTEM SHALL** responder 201.
4. **WHEN** el autor anula su evento **THE SYSTEM SHALL** responder 200 y descontarlo del marcador; **WHEN** otro `MEMBER` intenta anularlo **THE SYSTEM SHALL** responder 403; **WHEN** un admin anula cualquier evento **THE SYSTEM SHALL** responder 200.
5. **WHEN** se envía un evento a un partido `SCHEDULED` **THE SYSTEM SHALL** responder 409.
6. **WHEN** se pide el estado en vivo **THE SYSTEM SHALL** devolver los eventos en orden de `createdAt`, cada uno con `recordedBy.displayName`, y `canRecord` true solo para admins o para miembros cuando `membersCanScore` es true.

**Verify**

```bash
./mvnw -q test -Dtest=LiveIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T2: add idempotent live events api"
git tag step-14-live-api
```

### `E3-T3` — Build mobile-first live mode

> **Superseded en parte por E4-T7 agrega `Jugadón A/B`, quita la preselección del arquero y arma los diálogos con `DialogShell`; el diálogo de esta tarea ya quedó por encima del marcador (`z-40`/`z-50`).** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E3-T1`, `E3-T2` · **Priority:** p0

`api.ts`: `useLive` (polling 5 s), `useRecordEvent` (UUID una vez, hasta 3 reintentos con el mismo `id`), `useVoidEvent`; ambas mutaciones invalidan la clave `live`. `LivePage`: `h1` "En vivo" (o "Resumen" en `CLOSED`); `Scoreboard` `sticky top-0` con `role="status"` y números `font-mono tabular-nums`, contenedor con `scroll-pt-28`; si `canRecord`: `Button size="live"` `Gol A`, `Gol B`, `Atajada A`, `Atajada B`, `Corte A`, `Corte B`; diálogo (`radix-ui` Dialog) para autor (o `En contra / no sé`) y asistencia; atajadas preseleccionan al arquero; `Deshacer` anula mi último evento no anulado; lista (más nuevos primero) con `cargado por {displayName}` y `Anular` si soy autor o admin; anulados tachados + "anulado"; banner `Sin conexión, reintentando…` mientras falle el polling. Router: `/grupos/:groupId/partidos/:matchId/en-vivo`. Test con `vi.useFakeTimers({ shouldAdvanceTime: true })` y `fetch` simulado. El alto real de los botones no se puede medir en jsdom: acá se verifica la utilidad `min-h-16`; E3-T6 mide los píxeles con Playwright.

**Files**
- `frontend/src/features/live/api.ts` — new
- `frontend/src/features/live/LivePage.tsx` — new
- `frontend/src/app/router.tsx` — edit: ruta del modo en vivo
- `frontend/src/features/live/LivePage.test.tsx` — new

**Acceptance**

1. **WHEN** `LivePage.test.tsx` avanza los timers falsos 5000 ms **THE SYSTEM SHALL** pedir `GET /api/groups/{groupId}/matches/{matchId}/live` por segunda vez.
2. **WHEN** se activa `Gol A` y se elige el autor **THE SYSTEM SHALL** hacer `POST /api/groups/{groupId}/matches/{matchId}/events` con `type` `GOAL`, `team` `A`, el `playerId` elegido y un `id` UUID, y reenviar el mismo `id` si el primer intento falla por red.
3. **WHEN** se activa `Deshacer` **THE SYSTEM SHALL** hacer `POST /api/groups/{groupId}/matches/{matchId}/events/{eventId}/void` sobre el último evento no anulado cargado por el usuario actual.
4. **WHEN** un `MEMBER` ve la lista de eventos **THE SYSTEM SHALL** mostrar `Anular` solo en sus propios eventos, y **WHEN** la ve un `ADMIN` **THE SYSTEM SHALL** mostrarlo en todos los no anulados.
5. **WHEN** se renderiza el panel de registro **THE SYSTEM SHALL** dar a cada botón de registro la utilidad `min-h-16` y mostrar el marcador en una región `role="status"`.
6. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify**

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T3: build mobile-first live mode"
git tag step-15-live-ui
```

### `E3-T4` — Close matches with ELO ledger and replay

**Depends on:** `E2-T4`, `E3-T2`, `E4-T1`, `E4-T4` · **Priority:** p0

`EloReplayService.replayFrom` con el algoritmo exacto de *Conventions*, todo por `JdbcClient` (`details` armado con `JsonMapper` de Jackson 3 en el orden `goals, assists, mvp, cleanSheet, saves, tackles, uncapped, total` e insertado con `?::jsonb`). `MatchLifecycleController`: `close`, `mvp`, `annul` (admin; si no 403). `LiveController` (edit): en `CLOSED`, alta/anulación solo admin y, tras guardar, recalcula `score_a`/`score_b` y llama a `replayFrom` en la misma transacción. `EloReplayIntegrationTest` por API. `EloReplayPropertyTest`: `Random(42)`, 12 jugadores, 8 partidos con lineups y eventos aleatorios (GOAL, SAVE, TACKLE y PLAY; atajadas de cualquier jugador) cerrados uno por uno en orden de `played_at`; capturar el libro `(match_id, player_id, reason, elo_before, elo_after, delta)` ordenado y los `current_elo`; `replayFrom` desde el primero; igualdad exacta.

**Files**
- `src/main/java/com/futbolmanager/elo/EloReplayService.java` — new
- `src/main/java/com/futbolmanager/match/MatchLifecycleController.java` — new
- `src/main/java/com/futbolmanager/live/LiveController.java` — edit: correcciones sobre `CLOSED` + replay
- `src/test/java/com/futbolmanager/elo/EloReplayIntegrationTest.java` — new
- `src/test/java/com/futbolmanager/elo/EloReplayPropertyTest.java` — new

**Acceptance**

1. **WHEN** un admin cierra un partido `LIVE` con goles 2–1 **THE SYSTEM SHALL** dejarlo `CLOSED` con `score_a` 2 y `score_b` 1, escribir una fila `TEAM_RESULT` por jugador del lineup y una `BONUS` solo para quienes tienen bonus mayor que 0, cada una con `elo_after` igual a `elo_before` + `delta`, y actualizar `current_elo`.
2. **WHEN** se intenta cerrar un partido que no está `LIVE` **THE SYSTEM SHALL** responder 409, y **WHEN** lo intenta un `MEMBER` **THE SYSTEM SHALL** responder 403.
3. **WHEN** `EloReplayPropertyTest` cierra uno por uno una secuencia aleatoria de partidos con semilla fija (eventos GOAL, SAVE, TACKLE y PLAY, con atajadas de cualquier jugador) y después ejecuta `replayFrom` desde el primero **THE SYSTEM SHALL** producir filas `(match_id, player_id, reason, elo_before, elo_after, delta)` y valores de `current_elo` idénticos.
4. **WHEN** un admin anula un partido `CLOSED` **THE SYSTEM SHALL** borrar sus filas `TEAM_RESULT` y `BONUS`, recalcular los partidos posteriores y dejar cada `current_elo` igual al que produce un `replayFrom` completo posterior.
5. **WHEN** un admin agrega o anula un evento o cambia el MVP de un partido `CLOSED` **THE SYSTEM SHALL** recalcular ese partido y todos los posteriores, y **WHEN** lo intenta un `MEMBER` **THE SYSTEM SHALL** responder 403.
6. **WHEN** corre cualquier recálculo **THE SYSTEM SHALL** dejar intactas las filas `INITIAL` (mismos ids y valores).

**Verify**

```bash
./mvnw -q test -Dtest=EloReplayIntegrationTest
./mvnw -q test -Dtest=EloReplayPropertyTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T4: close matches with elo ledger and replay"
git tag step-26-close-elo-replay
```

### `E3-T5` — Add ranking, stats and close dialog

**Depends on:** `E3-T3`, `E3-T4`, `E4-T7` · **Priority:** p0

`StatsController` con `JdbcClient` y las definiciones de *Conventions*. `StatsIntegrationTest`: por API, 3 partidos cerrados (victoria, victoria, empate para el jugador observado, en ese orden de `played_at`), uno anulado y eventos anulados. `StatsPages.tsx`: `RankingPage` (`h1` "Ranking"; tabla con posición, nombre, ELO mono, partidos, badge `Provisorio`) y `PlayerProfilePage` (`h1` con el nombre; PJ/G/E/P, goles, asistencias, MVPs con badge `bg-mvp-badge text-on-mvp`, racha en texto; tabla del historial ELO — sin gráfico), con sus propios hooks (claves `['groups', groupId, 'ranking']`, `['groups', groupId, 'players', playerId, 'stats' | 'elo-history']`). `LivePage` (edit): admin en `LIVE` → `Cerrar partido` → diálogo (`DialogShell` de `components/ui`, siempre por encima del marcador sticky) con `select` `MVP (opcional)` → `POST …/close` → resumen de `eloChanges` (delta con signo y texto) e invalidación de `['groups', groupId]`; en `CLOSED`, `Cambiar MVP` y `Anular partido` (con confirmación). Router: `/grupos/:groupId/ranking`, `/grupos/:groupId/jugadores/:playerId`.

**Files**
- `src/main/java/com/futbolmanager/stats/StatsController.java` — new
- `src/test/java/com/futbolmanager/stats/StatsIntegrationTest.java` — new
- `frontend/src/features/stats/StatsPages.tsx` — new
- `frontend/src/features/live/LivePage.tsx` — edit: diálogo de cierre, cambiar MVP, anular
- `frontend/src/app/router.tsx` — edit: ranking y perfil

**Acceptance**

1. **WHEN** se pide `GET /api/groups/{groupId}/ranking` **THE SYSTEM SHALL** devolver los jugadores no borrados ordenados por `currentElo` descendente y `name` ascendente, cada uno con `played` y `provisional` igual a `played < 10`.
2. **WHEN** un jugador tiene 3 partidos cerrados con resultados victoria, victoria y empate (el empate es el último) **THE SYSTEM SHALL** devolver `played` 3, `won` 2, `drawn` 1, `lost` 0 y `streak` `{"type":"D","length":1}`.
3. **WHEN** se calculan goles y asistencias **THE SYSTEM SHALL** contar solo eventos no anulados de partidos `CLOSED`, sin contar partidos `ANNULLED`.
4. **WHEN** se pide `GET /api/groups/{groupId}/players/{playerId}/elo-history` **THE SYSTEM SHALL** devolver primero el punto `INITIAL` y luego un punto por partido cerrado cuyo `eloAfter` incluye `TEAM_RESULT` y `BONUS`.
5. **WHEN** alguien que no es miembro pide el ranking, las estadísticas o el historial **THE SYSTEM SHALL** responder 404.
6. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend con `StatsPages.tsx` y el diálogo de cierre **THE SYSTEM SHALL** salir con 0.

**Verify**

```bash
./mvnw -q test -Dtest=StatsIntegrationTest
./mvnw -q verify
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T5: add ranking, stats and close dialog"
git tag step-27-stats-ranking
```

### `E3-T6` — Harden prod, seed demo, run E2E, document deploy

**Depends on:** `E1-T3`, `E1-T5`, `E3-T5`, `E4-T5`, `E4-T6` · **Priority:** p1

`SecurityConfig` (edit): `@Value("${fm.security.content-security-policy}")`; si no está vacío, `headers.contentSecurityPolicy(c -> c.policyDirectives(csp))`. `DemoDataSeeder` (`@Profile("demo")` `@Component` que implementa `SmartInitializingSingleton` — **no** `ApplicationRunner`: `afterSingletonsInstantiated()` corre después de Flyway y antes de que arranque el conector web, así el primer 200 de `/api/health` implica que la siembra ya está commiteada y `scripts/smoke.sh` no puede cortarla a mitad; `@Value("${DEMO_PASSWORD}")`, `TransactionTemplate` inyectado): si existe el grupo `Los del jueves` no hace nada; si no, dentro de `transactionTemplate.executeWithoutResult(...)` crea el usuario `demo` (display `Demo`, BCrypt), el grupo (el creador obtiene su jugador por `MemberJoined`), 13 invitados más (14 activos, 2 con puesto preferido `GK`), 6 partidos en jueves pasados consecutivos con lineups de `TeamBalancer`, eventos con `Random(42)`, estado `CLOSED` y `EloReplayService.replayFrom` desde el primero. `ProdProfileIntegrationTest` (`@ActiveProfiles("prod")` + Testcontainers). `flows.spec.ts`: tests `registro y grupo`, `partido completo`, `throttling de login`, con usuarios `e2e_` + 8 caracteres aleatorios; helpers `expectNoSeriousA11yViolations(page)` (`AxeBuilder` con tags `wcag2a`, `wcag2aa`, `wcag21aa`, `wcag22aa`; falla en `serious`/`critical`) y `expectNoHorizontalScroll(page)` en `/ingresar`, `/registro`, `/grupos`, detalle de grupo y modo en vivo; en vivo, `boundingBox()` de los botones de registro con alto ≥ 64. `README.md`: qué es, stack, requisitos (JDK 21, Docker Desktop, Node 24 LTS — Node 25 instala con `EBADENGINE`), Bootstrap, comandos Git Bash y CMD/PowerShell, demo, y runbook de deploy: Neon (Postgres 17, us-east-1), Render (`render.yaml`, variables `DB_URL` con `?sslmode=require`, `DB_USERNAME`, `DB_PASSWORD`), smoke de `/api/health`, pinger opcional cada 10 min, rollback desde *Events → Rollback*, rotación de la contraseña de Neon.

**Files**
- `src/main/java/com/futbolmanager/config/SecurityConfig.java` — edit: CSP condicional
- `src/main/java/com/futbolmanager/config/DemoDataSeeder.java` — new
- `src/test/java/com/futbolmanager/config/ProdProfileIntegrationTest.java` — new
- `frontend/e2e/flows.spec.ts` — new
- `README.md` — new

**Acceptance**

1. **WHEN** corre `ProdProfileIntegrationTest` con el perfil `prod` **THE SYSTEM SHALL** devolver en `/api/health` los headers `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Content-Type-Options: nosniff` y `X-Frame-Options: DENY`, y 404 en `/v3/api-docs` y `/swagger-ui.html`.
2. **WHEN** el jar arranca dos veces con `SPRING_PROFILES_ACTIVE=demo` **THE SYSTEM SHALL** dejar exactamente un grupo `Los del jueves` con 14 jugadores activos y 6 partidos `CLOSED`.
3. **WHEN** corre el test de Playwright `registro y grupo` **THE SYSTEM SHALL** registrar un usuario, crear un grupo y mostrar 3 jugadores invitados agregados en la lista de jugadores.
4. **WHEN** corre el test de Playwright `partido completo` **THE SYSTEM SHALL** crear un partido, confirmar 4 jugadores, armar y guardar equipos, empezar, cargar 2 goles y 1 jugadón del equipo A, cerrar el partido y mostrar en el ranking a cada jugador del equipo A con ELO mayor a 1000.
5. **WHEN** corre el test de Playwright `throttling de login` y un usuario falla la contraseña 5 veces y lo intenta una sexta **THE SYSTEM SHALL** mostrar un mensaje que contiene `Demasiados intentos fallidos`.
6. **WHEN** los tests de Playwright analizan con axe `/ingresar`, `/registro`, `/grupos`, el detalle de grupo y el modo en vivo a 375 px de ancho **THE SYSTEM SHALL** reportar 0 violaciones `serious` o `critical`, ningún scroll horizontal y botones de registro en vivo de al menos 64 px de alto.

**Verify**

```bash
./mvnw -q test -Dtest=ProdProfileIntegrationTest
./mvnw -q verify
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run build
./mvnw -q -DskipTests package
SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh
SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh
test "$(docker compose exec -T db psql -U futbol -d futbol -tAc "select count(*) from groups where name = 'Los del jueves'")" = 1
test "$(docker compose exec -T db psql -U futbol -d futbol -tAc "select count(*) from players p join groups g on g.id = p.group_id where g.name = 'Los del jueves' and p.deleted_at is null")" = 14
test "$(docker compose exec -T db psql -U futbol -d futbol -tAc "select count(*) from matches m join groups g on g.id = m.group_id where g.name = 'Los del jueves' and m.status = 'CLOSED'")" = 6
npm --prefix frontend run e2e:install
npm --prefix frontend run test:e2e
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T6: harden prod, seed demo, run e2e, document deploy"
git tag step-28-hardening-e2e
```

---

## Epic acceptance

The epic is done when every task is `done` **and**:

1. **WHEN** el jar empaquetado corre los 3 recorridos de Playwright **THE SYSTEM SHALL** completarlos con 0 violaciones axe serias o críticas.
2. **WHEN** se cierra, corrige o anula un partido **THE SYSTEM SHALL** dejar `current_elo` igual al de un replay completo.

```bash
./mvnw -q verify
npm --prefix frontend run test
npm --prefix frontend run build
./mvnw -q -DskipTests package
npm --prefix frontend run test:e2e
```

## Pitfalls

- **`test:e2e` corre 4 tests, no 3** — `e2e/health.spec.ts` (emitido, gate desde E1-T2) más los 3 de `flows.spec.ts`. No lo borres ni lo muevas.
- **Seed demo como `ApplicationRunner`** — corre después de que el servidor acepta tráfico; `smoke.sh` puede matar la JVM a mitad de la transacción y los conteos de `psql` quedan en 0. Usá `SmartInitializingSingleton`.
- **Escribir ELO fuera de `EloReplayService`** — dos caminos divergen; cerrar es un replay, siempre.
- **Recalcular `INITIAL`** — está congelado; el replay borra solo `TEAM_RESULT`/`BONUS`/`MVP_BONUS`.
- **Ordenar por `played_at` sin `id`** — dos partidos a la misma hora quedan en orden indefinido; siempre `(played_at, id)`.
- **`save()` de un `MatchEvent` con id asignado sin `Persistable`** — Spring Data hace `merge` (SELECT + INSERT) y la idempotencia se vuelve ambigua.
- **Generar un UUID nuevo en cada reintento** — duplica goles; el `id` se genera una vez por toque.
- **Playwright con el dev server** — el config apunta al jar en :8080; corré `build` + `package` antes.
- **E2E que dependen de datos previos** — cada test crea su usuario y su grupo.

## Before moving on

- [ ] Every task in this epic is `done` in `tasks.json` — no task left `in_progress`.
- [ ] Every `verify` command of every task in this epic passed, not just the first one.
- [ ] No `verify` command was edited, and none was skipped because a file it names did not exist.
- [ ] **Every task in this epic has its `checkpoint` tag in version control** — `git tag -l 'step-*'` lists `step-13-match-setup-ui` … `step-28-hardening-e2e`.
- [ ] Gate command passes clean, run from the project root.
- [ ] Every "Produced" contract above exists with the stated signature.
- [ ] No file outside the subtree was modified.
- [ ] `.env.example` updated if this epic added a variable — `DEMO_PASSWORD` ya está.
- [ ] One commit per task, each prefixed with its task id, each followed by its checkpoint tag.
