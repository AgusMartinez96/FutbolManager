# Epic 02: Grupos, jugadores y motores

> Al terminar existen grupos con invitación, roles y aislamiento (404 entre grupos), jugadores con ELO inicial congelado, sus pantallas, la calculadora ELO y el balanceador de equipos puros, y la API de partidos con convocatoria, balance, lineup e inicio.

| | |
|---|---|
| **Epic id** | `02-grupos-jugadores-y-motores` |
| **Tasks** | `E2-T1` … `E2-T6` |
| **Depends on** | `01-fundaciones-y-cuentas` (E1-T1, E1-T4, E1-T6) |
| **Unlocks** | `03-en-vivo-elo-y-produccion` |
| **Parallel with** | dentro del epic, `E2-T4` y `E2-T5` no comparten archivos con nada y pueden correr en paralelo con `E2-T1`…`E2-T3` |

You do not need any other file to complete this epic. Everything below is repeated here on purpose.

---

## Stack

Spring Boot 4.1 (Spring MVC, Security 7, Data JPA/Hibernate 7, Session JDBC, Flyway) · Java 21 · React 19 + TypeScript 6 + Vite 8 · Tailwind CSS 4 · TanStack Query 5 · React Router 7 · PostgreSQL 17 · sesión de servidor (cookie `FM_SESSION`).
Package managers: Maven Wrapper (`./mvnw`) y npm (`frontend/package-lock.json`). Node fijado en `.nvmrc` (24). Versiones en `pom.xml` y el lockfile — leelas, nunca adivines una.

Todos los comandos corren en **Git Bash** desde la raíz del proyecto.

| Task | Command |
|---|---|
| DB local | `docker compose up -d --wait db` / `docker compose down` |
| Backend: todo | `./mvnw -q verify` (requiere Docker: Testcontainers) |
| Backend: una clase | `./mvnw -q test -Dtest=<Clase>` |
| Frontend | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` |

**Gate:** `./mvnw -q verify` (backend) y los cinco comandos de frontend en verde antes de marcar una tarea como hecha.

Los tests de integración levantan su propio `postgres:17` con Testcontainers (`TestcontainersConfiguration`, ya en la raíz). Docker tiene que estar corriendo.

## Directory subtree

```
src/main/java/com/futbolmanager/
  FutbolManagerApplication.java          # exists — @EnableJpaRepositories(considerNestedRepositories = true), @Bean Clock
  common/ApiException.java               # exists — notFound/forbidden/conflict/unprocessable/badRequest
  auth/AuthUser.java                     # exists — @AuthenticationPrincipal
  group/FriendGroup.java                 # NEW (E2-T1)
  group/Membership.java                  # NEW (E2-T1)
  group/GroupAccess.java                 # NEW (E2-T1)
  group/GroupController.java             # NEW (E2-T1)
  player/Player.java                     # NEW (E2-T2)
  player/PlayerService.java              # NEW (E2-T2)
  player/PlayerController.java           # NEW (E2-T2)
  elo/EloCalculator.java                 # NEW (E2-T4)
  match/balance/TeamBalancer.java        # NEW (E2-T5)
  match/Match.java                       # NEW (E2-T6)
  match/MatchCallUp.java                 # NEW (E2-T6)
  match/LineupEntry.java                 # NEW (E2-T6)
  match/MatchController.java             # NEW (E2-T6)
src/test/java/com/futbolmanager/
  TestcontainersConfiguration.java, support/ApiTestClient.java # exist — read-only
  group/GroupIntegrationTest.java        # NEW (E2-T1)
  player/PlayerIntegrationTest.java      # NEW (E2-T2)
  elo/EloCalculatorTest.java             # NEW (E2-T4)
  match/balance/TeamBalancerTest.java    # NEW (E2-T5)
  match/MatchIntegrationTest.java        # NEW (E2-T6)
frontend/src/
  lib/api.ts, components/ui.tsx, features/auth/session.tsx # exist — read-only
  app/router.tsx                         # exists — edited (E2-T3)
  features/groups/api.ts                 # NEW (E2-T3)
  features/groups/GroupsPages.tsx        # NEW (E2-T3)
  features/groups/GroupDetailPage.tsx    # NEW (E2-T3)
  features/groups/GroupsPages.test.tsx   # NEW (E2-T3)
```

Everything outside this subtree is out of scope. If a task seems to require editing a file not listed here, stop and report — it means the epic boundary is wrong.

## Data model touched here

Todas las tablas ya existen (Flyway `V2__core_schema.sql`); acá se mapean. Enums = `text` + `CHECK`; `updated_at` por trigger (no mapear como escribible); `@Id @GeneratedValue(strategy = GenerationType.UUID)`.

| Entity | Fields this epic adds or reads | Notes |
|---|---|---|
| `groups` (entidad `FriendGroup`) | `id`, `name` (1–60), `invite_code` (`^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$`, único), `members_can_score` (default false), `created_by_user_id` | `@Entity(name = "FriendGroup") @Table(name = "groups")` — `Group` es palabra reservada de JPQL |
| `memberships` | `group_id`, `user_id`, `role` (`ADMIN`/`MEMBER`) | único `(group_id, user_id)` |
| `players` | `group_id`, `user_id` (nullable, único por grupo si no es null), `name` (1–60), `nickname` (1–30, nullable), `preferred_position` (`GK`/`DEF`/`MID`/`FWD`), `dominant_foot` (`RIGHT`/`LEFT`/`BOTH`), `current_elo`, `deleted_at` | soft delete |
| `elo_changes` | `group_id`, `player_id`, `match_id` (null solo en `INITIAL`), `reason`, `elo_before`, `elo_after`, `delta`, `details` | **solo `JdbcClient`**; CHECK `elo_after = elo_before + delta`; un solo `INITIAL` por jugador |
| `matches` | `group_id`, `played_at`, `location` (1–120), `format` (5/7/8/11), `status` (`SCHEDULED`/`LIVE`/`CLOSED`/`ANNULLED`), `score_a`, `score_b`, `mvp_player_id`, `closed_at`, `annulled_at`, `version` | `@Version long version` |
| `match_call_ups` | `match_id`, `player_id`, `status` (`CONFIRMED`/`MAYBE`/`NOT_GOING`) | único `(match_id, player_id)` |
| `match_lineup_entries` | `match_id`, `player_id`, `team` (`A`/`B`), `position` (`GK`/`DEF`/`MID`/`FWD`) | único `(match_id, player_id)` |

## Contracts

**Consumed** — already exists, do not rebuild:

| From | Interface | Guarantee |
|---|---|---|
| `01` | `ApiException.*` + `ApiExceptionHandler` | ProblemDetail con `detail` en español; validación → 400 + `errors: [{field, message}]` |
| `01` | `@AuthenticationPrincipal AuthUser user` | `record AuthUser(UUID id, String username, String displayName)` |
| `01` | `ApiTestClient` | `register(u, d, p)` deja la sesión iniciada; `get/post/put/patch/delete`; `json(result, "$.x")` |
| `01` | `apiFetch`, `ApiError`, `RequireAuth`, `Button`, `Field`, `Card`, `Alert`, `createAppRouter` | ver epic 01 |

**Produced** — later epics depend on exactly these signatures. Changing one breaks them:

| Export | Signature | Used by |
|---|---|---|
| `group/GroupAccess` | `Membership requireMember(UUID groupId, AuthUser user)` (404 `Grupo no encontrado.`), `Membership requireAdmin(UUID groupId, AuthUser user)` (403 `Solo un administrador del grupo puede hacer esto.`) | 03 |
| `group/GroupController.MemberJoined` | `record MemberJoined(UUID groupId, UUID userId, String displayName)` (publicado al crear el grupo y al unirse) | `PlayerService`, 03 (seed) |
| `group/GroupController.MemberRemoved` | `record MemberRemoved(UUID groupId, UUID userId)` | `PlayerService` |
| `group/FriendGroup`, `group/Membership` | entidades + `FriendGroup.Repo`, `Membership.Repo` | 03 |
| `player/Player` | entidad + `Player.Repo` (`findByIdAndGroupId`, `findAllByGroupIdAndDeletedAtIsNullOrderByNameAsc`, `findByGroupIdAndUserId`) | 03 |
| `player/PlayerService` | `Player create(UUID groupId, UUID userIdOrNull, String name, String nickname, Player.Position position, Player.Foot foot)` | 03 (seed) |
| `elo/EloCalculator` | `List<PlayerResult> calculate(MatchInput input)`; records `MatchInput`, `PlayerInput`, `PlayerResult(UUID playerId, int teamDelta, int bonus, BonusBreakdown breakdown)`, `BonusBreakdown(int goals, int assists, int mvp, int cleanSheet, int saves, int tackles, int uncapped, int total)` | 03 (`EloReplayService`) |
| `match/balance/TeamBalancer` | `Result balance(List<Candidate> candidates, boolean oneGoalkeeperPerSide)`; `Candidate(UUID id, int elo, Position preferredPosition)`; `Result(List<Assignment> teamA, List<Assignment> teamB, double avgEloA, double avgEloB, double eloDifference, boolean goalkeeperConstraintApplied)`; `Assignment(UUID playerId, Position position)` | `MatchController`, 03 (seed) |
| `match/Match`, `MatchCallUp`, `LineupEntry` | entidades + `Repo` anidados (`Match.Repo.findByIdAndGroupId`, `LineupEntry.Repo.findAllByMatchId`) | 03 |
| REST | rutas de grupos, miembros, jugadores y partidos (abajo) | 03 (frontend) |
| `frontend/src/features/groups/api.ts` | hooks `useGroup(groupId)`, `usePlayers(groupId)`, `useMembers(groupId)` con claves `['groups', groupId, …]` | 03 |

Rutas de este epic: `GET/POST /api/groups` · `POST /api/groups/join` `{inviteCode}` → `{groupId}` · `GET /api/groups/{groupId}` → `{id, name, inviteCode, membersCanScore, myRole, myUserId}` · `PATCH /api/groups/{groupId}` `{name?, membersCanScore?}` · `POST /api/groups/{groupId}/invite-code` · `GET /api/groups/{groupId}/members` → `[{userId, username, displayName, role}]` · `PATCH|DELETE /api/groups/{groupId}/members/{userId}` · `GET|POST /api/groups/{groupId}/players` · `PATCH|DELETE /api/groups/{groupId}/players/{playerId}` (respuesta `{id, name, nickname, preferredPosition, dominantFoot, currentElo, userId}`) · `GET|POST /api/groups/{groupId}/matches` · `GET|PATCH /api/groups/{groupId}/matches/{matchId}` · `PUT /api/groups/{groupId}/matches/{matchId}/call-ups/{playerId}` `{status}` · `POST …/balance` `{oneGoalkeeperPerSide}` · `PUT …/lineup` `{entries:[{playerId, team, position}]}` · `POST …/start`.

## Conventions that bite in this area

- **Tenancy:** primera línea de todo handler `/api/groups/{groupId}/**` = `groupAccess.requireMember(...)`/`requireAdmin(...)`. Otro grupo → **404, nunca 403**. Recursos anidados por `findByIdAndGroupId`.
- **Último admin:** degradar o quitar a un ADMIN cuando hay uno solo → 409 `El grupo tiene que tener al menos un administrador.`
- **Código de invitación:** 8 caracteres de `ABCDEFGHJKMNPQRSTUVWXYZ23456789` con `SecureRandom`; reintentar si ya existe; código inexistente → 404 `Código de invitación inválido.`
- **Eventos de dominio:** `GroupController` publica `MemberJoined`/`MemberRemoved`; `PlayerService` los escucha con `@EventListener` (síncrono, misma transacción). `GroupController` no importa `player`.
- **ELO inicial:** `Math.round` del promedio de `current_elo` de los jugadores activos del grupo al crear (1000 si no hay); fila `INITIAL` con `elo_before = elo_after = inicial`, `delta 0`, `match_id NULL`. Nunca se recalcula.
- **Enums en requests:** como `String` con `@Pattern` (`GK|DEF|MID|FWD`, `RIGHT|LEFT|BOTH`, `5|7|8|11` vía validación) para que un valor inválido dé 400 con `errors[].field`.
- **ELO (E2-T4), fórmulas exactas:** rating de equipo = promedio (double) del ELO previo; `E = 1 / (1 + 10^((R_rival − R_propio) / 400))`; `S` = 1/0.5/0; `K` = 40 si `previousCountedMatches < 10`, si no 24; `teamDelta = Math.round(K × (S − E))` (semántica de Java: .5 hacia +∞). Bonus (nunca negativo): gol +2 c/u, asistencia +1 c/u, MVP +3, valla invicta +3 si puesto por partido `GK` o `DEF` y el rival hizo 0, atajadas +1 c/u solo para `GK` con tope +3, cortes +1 c/u con tope +3, **total con tope +10**.
- **Balance (E2-T5), algoritmo exacto:** 2–22 candidatos (si no, `IllegalArgumentException`); ordenar por id; el equipo A contiene siempre al primero; recorrer todos los subconjuntos A que lo contienen con `|A| ∈ {⌊n/2⌋, ⌈n/2⌉}` (22 → C(21,10) = 352.716); minimizar `|sumA·nB − sumB·nA| / (nA·nB)` comparando fracciones con `long` (sin flotantes); desempate: lista ordenada de ids de A lexicográficamente menor. Con `oneGoalkeeperPerSide` y ≥ 2 GK-preferidos: solo particiones con ≥ 1 GK-preferido por lado; el GK-preferido de menor id de cada lado recibe `GK`, los GK-preferidos extra `DEF`, el resto su puesto preferido. Con < 2 la opción se ignora (`goalkeeperConstraintApplied: false`).
- **Algoritmos puros:** `EloCalculator` y `TeamBalancer` no importan `org.springframework` ni `jakarta.persistence` (un `grep` lo verifica); cada uno define su propio `enum Position`.
- **Frontend:** claves de query con el tenant (`['groups', groupId, 'players']`); tras una mutación, invalidar `['groups', groupId]`; pestañas por `?tab=`; UI en español; puestos ARQ/DEF/MED/DEL; ELO en `font-mono tabular-nums`; sin hex.

Full project rules: `CLAUDE.md`. Area rules: `.claude/rules/backend-java.md`, `.claude/rules/database.md`, `.claude/rules/frontend.md`, `.claude/rules/testing.md`. Both sit in the project root — the builder copied them there from the bundle's `workspace/` before task one.

---

## Tasks

Listed in the same order as `tasks.json`. That order is the build order — work top to bottom and do not re-rank by priority or by what looks quick.

### `E2-T1` — Add groups, invites and members API

**Depends on:** `E1-T4` · **Priority:** p0 — metadata for scope cuts, not a running order

`FriendGroup` (+ `Repo` con `findByInviteCode`, `existsByInviteCode`; `static String newInviteCode(SecureRandom)`), `Membership` (`enum Role { ADMIN, MEMBER }`; `Repo` con `findByGroupIdAndUserId`, `findAllByGroupId`, `findAllByUserId`, `countByGroupIdAndRole`), `GroupAccess` (`requireMember`/`requireAdmin`), `GroupController` con las rutas de grupos y miembros, DTOs como records anidados, `@Transactional`, y los records públicos `MemberJoined`/`MemberRemoved` publicados con `ApplicationEventPublisher` (al crear: para el creador; al unirse; al quitar). `join` de alguien ya miembro → 200 con el mismo `groupId`. `GET /api/groups/{groupId}` devuelve `myUserId` (el frontend encuentra "mi jugador" por `userId`). El test usa varios `ApiTestClient`, uno por usuario.

**Files**
- `src/main/java/com/futbolmanager/group/FriendGroup.java` — new
- `src/main/java/com/futbolmanager/group/Membership.java` — new
- `src/main/java/com/futbolmanager/group/GroupAccess.java` — new
- `src/main/java/com/futbolmanager/group/GroupController.java` — new
- `src/test/java/com/futbolmanager/group/GroupIntegrationTest.java` — new

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** un usuario autenticado hace `POST /api/groups` con `{name}` **THE SYSTEM SHALL** responder 201 con un `inviteCode` que cumple `^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$` y dejar al creador como `ADMIN`.
2. **WHEN** otro usuario hace `POST /api/groups/join` con ese código **THE SYSTEM SHALL** crear exactamente una membresía `MEMBER`, y una segunda unión **SHALL** responder 200 dejando una sola membresía.
3. **WHEN** un usuario que no es miembro pide `GET /api/groups/{groupId}`, `GET /api/groups/{groupId}/members`, `PATCH /api/groups/{groupId}` o `DELETE /api/groups/{groupId}/members/{userId}` de ese grupo **THE SYSTEM SHALL** responder 404 en todos, nunca 403.
4. **WHEN** un `MEMBER` llama a `PATCH /api/groups/{groupId}`, `POST /api/groups/{groupId}/invite-code` o `PATCH /api/groups/{groupId}/members/{userId}` **THE SYSTEM SHALL** responder 403.
5. **WHEN** se degrada o se quita al único `ADMIN` **THE SYSTEM SHALL** responder 409 y dejar su rol sin cambios.
6. **WHEN** un admin regenera el código **THE SYSTEM SHALL** devolver un código nuevo y unirse con el código viejo **SHALL** responder 404.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=GroupIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T1: add groups, invites and members api"
git tag step-07-groups-api
```

Run both after the last `Verify` command exits 0, before starting the next task. The tag is this task's rollback target and the thing the build's final gate counts.

### `E2-T2` — Add players API with frozen initial ELO

> **Superseded en parte por E4-T2 agrega `players.level` (V3): obligatorio al crear, editable solo por un admin y omitido de las respuestas a un MEMBER.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E2-T1` · **Priority:** p0

`Player` (`enum Position { GK, DEF, MID, FWD }`, `enum Foot { RIGHT, LEFT, BOTH }`, `Repo`). `PlayerService.create(...)` calcula el ELO inicial, guarda el jugador e inserta la fila `INITIAL` con `JdbcClient`; `@EventListener` de `MemberJoined` (restaura un jugador existente de ese `user_id` o crea uno con `name = displayName`, `MID`, `RIGHT`) y de `MemberRemoved` (soft delete del vinculado). `PlayerController`: listar (activos, por nombre), crear y borrar (admin), editar (admin o el usuario vinculado); request con `@NotBlank @Size(max=60) name`, `@Size(min=1,max=30) nickname`, `@Pattern(regexp = "GK|DEF|MID|FWD")` y `@Pattern(regexp = "RIGHT|LEFT|BOTH")` como `String`. En el test, para el criterio 2, fijar `current_elo = 1101` de un jugador con `JdbcClient` antes de crear el invitado.

**Files**
- `src/main/java/com/futbolmanager/player/Player.java` — new
- `src/main/java/com/futbolmanager/player/PlayerService.java` — new
- `src/main/java/com/futbolmanager/player/PlayerController.java` — new
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` — new

**Acceptance**

1. **WHEN** se crea un grupo **THE SYSTEM SHALL** auto-crear el jugador del creador con `currentElo` 1000 y una fila `elo_changes` con `reason` `INITIAL`, `match_id` null, `elo_before` = `elo_after` = 1000 y `delta` = 0.
2. **WHEN** un admin crea un jugador mientras los jugadores activos del grupo tienen ELO 1000 y 1101 **THE SYSTEM SHALL** asignarle `currentElo` 1051 y escribir su fila `INITIAL` con ese valor.
3. **WHEN** un usuario se une a un grupo **THE SYSTEM SHALL** crear exactamente un jugador vinculado a ese usuario con `name` igual a su `displayName`, `preferredPosition` `MID` y `dominantFoot` `RIGHT`, y **WHEN** vuelve a unirse después de haber sido quitado **THE SYSTEM SHALL** restaurar ese mismo jugador en vez de crear otro.
4. **WHEN** un admin borra un jugador **THE SYSTEM SHALL** poner `deleted_at`, conservar sus filas de `elo_changes` y omitirlo de `GET /api/groups/{groupId}/players`.
5. **WHEN** un `MEMBER` crea un jugador **THE SYSTEM SHALL** responder 403, y **WHEN** alguien que no es miembro lista los jugadores **THE SYSTEM SHALL** responder 404.
6. **WHEN** el body trae `preferredPosition` fuera de `GK`, `DEF`, `MID`, `FWD` **THE SYSTEM SHALL** responder 400 con un error en el campo `preferredPosition`.

**Verify**

```bash
./mvnw -q test -Dtest=PlayerIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T2: add players api with frozen initial elo"
git tag step-08-players-api
```

### `E2-T3` — Build groups, members and players screens

> **Superseded en parte por E4-T5 agrega el campo «Nivel» (solo admin) al alta y edición de jugadores.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E1-T6`, `E2-T2` · **Priority:** p0

`api.ts`: hooks `useGroups`, `useCreateGroup`, `useJoinGroup`, `useGroup`, `useUpdateGroup`, `useRegenerateCode`, `useMembers`, `useChangeRole`, `useRemoveMember`, `usePlayers`, `useCreatePlayer`, `useUpdatePlayer`, `useDeletePlayer`. `GroupsPages.tsx`: `GroupsPage` (`h1` "Mis grupos"; esqueleto / vacío `Todavía no tenés grupos` + `Crear grupo` + `Unirme con código` / error `No pudimos cargar tus grupos.` + `Reintentar`; crear → navega a `/grupos/{id}`) y `JoinByLinkPage` (`/unirse/:code`: une al montar y navega; 404 → `Código de invitación inválido.`). `GroupDetailPage.tsx`: `h1` con el nombre; pestañas `?tab=jugadores|miembros|ajustes`; jugadores (ELO mono, ARQ/DEF/MED/DEL, link al perfil `/grupos/{groupId}/jugadores/{playerId}`; admin: `Agregar jugador`, editar, borrar con confirmación); miembros (admin: `Cambiar rol`, `Quitar`; todos: `Salir del grupo`); ajustes (admin: nombre, `membersCanScore`, código + `Copiar link` + `Regenerar código`); links a `/grupos/{groupId}/partidos` y `/grupos/{groupId}/ranking` (las rutas llegan en el epic 03; hasta entonces caen en "Página no encontrada"). Router: `/grupos`, `/unirse/:code`, `/grupos/:groupId` dentro de `RequireAuth`. Tests con `fetch` simulado (incluida `GET /api/auth/me`).

**Files**
- `frontend/src/features/groups/api.ts` — new
- `frontend/src/features/groups/GroupsPages.tsx` — new
- `frontend/src/features/groups/GroupDetailPage.tsx` — new
- `frontend/src/app/router.tsx` — edit: rutas de grupos
- `frontend/src/features/groups/GroupsPages.test.tsx` — new

**Acceptance**

1. **WHEN** la API simulada de grupos devuelve `[]` **THE SYSTEM SHALL** mostrar `Todavía no tenés grupos` con las acciones `Crear grupo` y `Unirme con código`.
2. **WHEN** se envía el formulario de crear grupo **THE SYSTEM SHALL** hacer `POST /api/groups` con `{name}` y navegar a `/grupos/{id}`.
3. **WHEN** un usuario autenticado abre `/unirse/ABCD2345` **THE SYSTEM SHALL** hacer `POST /api/groups/join` con `{"inviteCode":"ABCD2345"}` y navegar a `/grupos/{groupId}`.
4. **WHEN** el detalle de grupo se muestra para un `MEMBER` **THE SYSTEM SHALL NOT** mostrar los controles de admin `Agregar jugador`, `Cambiar rol` ni `Regenerar código`.
5. **WHEN** falla la petición de la lista de grupos **THE SYSTEM SHALL** mostrar `No pudimos cargar tus grupos.` y un botón `Reintentar` que repite la petición.
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
git add -A && git commit -m "E2-T3: build groups, members and players screens"
git tag step-09-groups-ui
```

### `E2-T4` — Implement the pure ELO calculator

> **Superseded en parte por E4-T1 quita los puestos del cálculo: atajada de cualquier jugador (tope +3), jugadón (+1, tope +2) y valla invicta +2 para todo el equipo.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E1-T1` · **Priority:** p0

Clase final `EloCalculator` sin Spring/JPA con `List<PlayerResult> calculate(MatchInput input)`. Records anidados: `MatchInput(List<PlayerInput> teamA, List<PlayerInput> teamB, int scoreA, int scoreB, UUID mvpPlayerId)`, `PlayerInput(UUID playerId, int eloBefore, int previousCountedMatches, Position position, int goals, int assists, int saves, int tackles)`, `PlayerResult(UUID playerId, int teamDelta, int bonus, BonusBreakdown breakdown)`, `BonusBreakdown(int goals, int assists, int mvp, int cleanSheet, int saves, int tackles, int uncapped, int total)` (puntos), `enum Position { GK, DEF, MID, FWD }`. Fórmulas exactas en *Conventions*. Tests JUnit puros, uno por regla, con los números de los criterios (calculados con esas fórmulas: 1000 vs 1400 → `E` del lado débil = 1/11 ≈ 0.0909).

**Files**
- `src/main/java/com/futbolmanager/elo/EloCalculator.java` — new
- `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` — new

**Acceptance**

1. **WHEN** dos equipos promedian 1000 y gana el equipo A con todos sus jugadores en K=40 **THE SYSTEM SHALL** dar `teamDelta` +20 a cada jugador de A y −20 a cada jugador de B, y un empate **SHALL** dar 0.
2. **WHEN** un equipo con promedio 1000 juega contra uno con promedio 1400 **THE SYSTEM SHALL** dar al lado de 1000 un `teamDelta` de +36 (K=40) o +22 (K=24) si gana, −4 (K=40) o −2 (K=24) si pierde, y +16 (K=40) si empata.
3. **WHEN** un jugador tiene 9 partidos contados previos **THE SYSTEM SHALL** usar K=40, y **WHEN** tiene 10 **THE SYSTEM SHALL** usar K=24.
4. **WHEN** un jugador hace 4 goles, da 2 asistencias y es MVP **THE SYSTEM SHALL** dar `bonus` 10 (tope total), **WHEN** el GK por partido registra 5 atajadas **THE SYSTEM SHALL** sumar 3 por atajadas, **WHEN** un jugador registra 4 cortes **THE SYSTEM SHALL** sumar 3 por cortes, y el `bonus` nunca **SHALL** ser negativo.
5. **WHEN** el rival hace 0 goles **THE SYSTEM SHALL** sumar 3 de valla invicta a los jugadores con puesto por partido `GK` o `DEF` y 0 a `MID` y `FWD`, también en un equipo sin `GK` asignado.
6. **WHEN** los equipos tienen 5 y 6 jugadores **THE SYSTEM SHALL** usar el promedio de cada equipo, y **WHEN** recibe dos veces la misma entrada **THE SYSTEM SHALL** devolver resultados iguales.

**Verify**

```bash
./mvnw -q test -Dtest=EloCalculatorTest
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/elo/EloCalculator.java || status=$?; test "$status" -eq 1
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T4: implement the pure elo calculator"
git tag step-10-elo-calculator
```

### `E2-T5` — Implement the pure exhaustive team balancer

> **Superseded en parte por E4-T3 lo reemplaza por un balanceo por nivel (el ELO solo desempata) y elimina `oneGoalkeeperPerSide`.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E1-T1` · **Priority:** p0

Clase final `TeamBalancer` sin Spring/JPA con la firma de *Contracts* y el algoritmo exacto de *Conventions* (combinaciones iterativas o bitmask con `int`, sin recursión profunda; comparación de fracciones en `long`). Test JUnit puro con un oráculo de fuerza bruta independiente (n ≤ 10) y `assertTimeoutPreemptively(Duration.ofSeconds(2), …)` para 22 jugadores.

**Files**
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` — new
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` — new

**Acceptance**

1. **WHEN** se balancean 10 jugadores **THE SYSTEM SHALL** devolver equipos de 5 y 5 cuya diferencia de promedios es igual al mínimo que encuentra el oráculo de fuerza bruta del test.
2. **WHEN** se balancean 2, 3 u 11 jugadores **THE SYSTEM SHALL** devolver tamaños 1 y 1, 2 y 1, y 6 y 5 respectivamente (en algún orden).
3. **WHEN** `oneGoalkeeperPerSide` es true y hay 2 o más jugadores con puesto preferido `GK` **THE SYSTEM SHALL** dejar al menos uno por lado, asignar `GK` a exactamente uno por lado y devolver `goalkeeperConstraintApplied` true, y **WHEN** hay solo 1 **THE SYSTEM SHALL** ignorar la opción y devolver `goalkeeperConstraintApplied` false.
4. **WHEN** recibe la misma entrada dos veces, en distinto orden **THE SYSTEM SHALL** devolver la misma partición, con el jugador de menor id en el equipo A.
5. **WHEN** recibe 23 jugadores **THE SYSTEM SHALL** lanzar `IllegalArgumentException`.
6. **WHEN** recibe 22 jugadores **THE SYSTEM SHALL** terminar en menos de 2 segundos.

**Verify**

```bash
./mvnw -q test -Dtest=TeamBalancerTest
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/match/balance/TeamBalancer.java || status=$?; test "$status" -eq 1
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T5: implement the pure exhaustive team balancer"
git tag step-11-team-balancer
```

### `E2-T6` — Add matches, call-ups, balance and lineup API

> **Superseded en parte por E4-T3 cambia `balance`: sin cuerpo, con nivel promedio y diferencia de nivel (solo admin).** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en el epic 04.

**Depends on:** `E2-T2`, `E2-T5` · **Priority:** p0

`Match` (`enum Status`, `@Version`; `Repo` con `findByIdAndGroupId`, `findTop200ByGroupIdOrderByPlayedAtDescIdDesc`), `MatchCallUp` (`Repo` con `findAllByMatchId`, `findByMatchIdAndPlayerId`), `LineupEntry` (`Repo` con `findAllByMatchId`, `deleteAllByMatchId`), `MatchController`: crear (`@NotNull Instant playedAt`, `@NotBlank @Size(max=120) location`, `format` validado contra 5/7/8/11 → 400 en `format`), editar (solo `SCHEDULED`), convocatoria (admin cualquiera; MEMBER solo su jugador → si no 403; jugador de otro grupo o borrado → 422), `balance` (confirmados no borrados → `TeamBalancer`; `IllegalArgumentException` → 400 `El armado automático admite hasta 22 jugadores.` o `Necesitás al menos 2 confirmados para armar equipos.`; no escribe nada), `lineup` (solo `SCHEDULED`; reemplazo total en una transacción; jugador de otro grupo/borrado/repetido → 422 sin tocar el anterior), `start` (`SCHEDULED` con ≥ 1 por equipo → `LIVE`; si no → 409 `Guardá los equipos antes de empezar.`; otro estado → 409). El `GET` de detalle incluye `callUps` y `lineup` con nombre y ELO.

**Files**
- `src/main/java/com/futbolmanager/match/Match.java` — new
- `src/main/java/com/futbolmanager/match/MatchCallUp.java` — new
- `src/main/java/com/futbolmanager/match/LineupEntry.java` — new
- `src/main/java/com/futbolmanager/match/MatchController.java` — new
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` — new

**Acceptance**

1. **WHEN** un admin hace `POST /api/groups/{groupId}/matches` con `{playedAt, location, format: 7}` **THE SYSTEM SHALL** responder 201 con `status` `SCHEDULED`, y **WHEN** `format` es 6 **THE SYSTEM SHALL** responder 400 con un error en el campo `format`.
2. **WHEN** un `MEMBER` pone en `CONFIRMED` la convocatoria de su propio jugador vinculado **THE SYSTEM SHALL** responder 200, y **WHEN** intenta cambiar la de otro jugador **THE SYSTEM SHALL** responder 403.
3. **WHEN** un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` con 10 confirmados **THE SYSTEM SHALL** devolver dos equipos de 5 con `eloDifference` y `position` por jugador sin escribir filas en `match_lineup_entries`, y **WHEN** hay 23 confirmados **THE SYSTEM SHALL** responder 400.
4. **WHEN** un admin guarda un lineup que incluye un jugador de otro grupo o un jugador borrado **THE SYSTEM SHALL** responder 422 y conservar el lineup anterior.
5. **WHEN** se llama a `POST /api/groups/{groupId}/matches/{matchId}/start` sin un lineup guardado con al menos un jugador por equipo **THE SYSTEM SHALL** responder 409, **WHEN** lo tiene **THE SYSTEM SHALL** responder 200 con `status` `LIVE`, y un segundo `start` **SHALL** responder 409.
6. **WHEN** alguien que no es miembro pide `GET /api/groups/{groupId}/matches/{matchId}` **THE SYSTEM SHALL** responder 404.

**Verify**

```bash
./mvnw -q test -Dtest=MatchIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T6: add matches, call-ups, balance and lineup api"
git tag step-12-matches-api
```

---

## Epic acceptance

The epic is done when every task is `done` **and**:

1. **WHEN** la suite completa de backend corre **THE SYSTEM SHALL** probar el aislamiento entre grupos (404) en grupos, jugadores y partidos.
2. **WHEN** los algoritmos puros se compilan **THE SYSTEM SHALL** no depender de Spring ni de JPA.

```bash
./mvnw -q verify
npm --prefix frontend run test
npm --prefix frontend run build
```

## Pitfalls

- **Entidad llamada `Group`** — choca con la palabra reservada de JPQL; es `FriendGroup` con `@Table(name = "groups")`.
- **Olvidar `considerNestedRepositories`** — los `Repo` anidados no se registran y los controllers no arrancan; ya está en `FutbolManagerApplication`, no lo toques.
- **`GroupController` importando `PlayerService`** — rompe la frontera entre features; usá los eventos.
- **Enum en el body como tipo Java** — un valor inválido da un error de deserialización sin `errors[].field`; usá `String` + `@Pattern`.
- **Flotantes en el balanceador** — los empates dependen de errores de redondeo y la partición deja de ser determinística; compará fracciones en `long`.
- **`Math.round` vs `RoundingMode.HALF_UP`** — para negativos difieren (−2.5 → −2 con `Math.round`); usá `Math.round`, que es la regla.

## Before moving on

- [ ] Every task in this epic is `done` in `tasks.json` — no task left `in_progress`.
- [ ] Every `verify` command of every task in this epic passed, not just the first one.
- [ ] No `verify` command was edited, and none was skipped because a file it names did not exist.
- [ ] **Every task in this epic has its `checkpoint` tag in version control** — `git tag -l 'step-*'` lists `step-07-groups-api` … `step-12-matches-api`.
- [ ] Gate command passes clean, run from the project root.
- [ ] Every "Produced" contract above exists with the stated signature.
- [ ] No file outside the subtree was modified.
- [ ] `.env.example` updated if this epic added a variable — this epic adds none.
- [ ] One commit per task, each prefixed with its task id, each followed by its checkpoint tag.
