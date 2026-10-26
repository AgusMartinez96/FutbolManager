# Epic 04: Arqueros rotativos, nivel de jugador y jugadón

> Al terminar, la posición es solo informativa; el ELO premia atajadas de cualquier jugador, jugadones y vallas invictas de todo el equipo; cada jugador tiene un nivel 1,0–10,0 (con un decimal) que solo ven los admins; y el armado de equipos minimiza la diferencia de nivel (el ELO solo desempata).

| | |
|---|---|
| **Epic id** | `04-arqueros-rotativos-nivel-y-jugadon` |
| **Tasks** | `E4-T1` … `E4-T10` |
| **Depends on** | `02-grupos-jugadores-y-motores` y `E3-T1…E3-T3` del epic `03-en-vivo-elo-y-produccion` |
| **Unlocks** | `E3-T4`, `E3-T5` y `E3-T6` (epic 03), que pasan a depender de estas tareas |
| **Parallel with** | ninguno |

Cambio de producto aprobado después de construir E3-T3. En `tasks.json` estas tareas van entre E3-T3 y E3-T4 (el orden del array es el orden de construcción); `E4-T8…E4-T10` (nivel con un decimal, agregadas después de construir E4-T5) van entre E4-T5 y E4-T6. You do not need any other file to complete this epic. Everything below is repeated here on purpose.

---

## Stack

Spring Boot 4.1 (Spring MVC, Security 7, Data JPA/Hibernate 7, Session JDBC, Flyway) · Java 21 · React 19 + TypeScript 6 + Vite 8 · Tailwind CSS 4 · TanStack Query 5 · React Router 7 · `radix-ui` · PostgreSQL 17.
Package managers: Maven Wrapper (`./mvnw`) y npm (`frontend/package-lock.json`). Versiones en `pom.xml` y el lockfile — leelas, nunca adivines una.

Todos los comandos corren en **Git Bash** desde la raíz del proyecto (en CMD/PowerShell: `.\mvnw.cmd` en vez de `./mvnw`; los `npm --prefix frontend run …` son iguales).

| Task | Command |
|---|---|
| Backend: todo | `./mvnw -q verify` (requiere Docker) |
| Backend: una clase | `./mvnw -q test -Dtest=<Clase>` |
| Frontend | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` |

**Gate:** `./mvnw -q verify` (tareas de backend) o los cinco comandos de frontend (tareas de frontend) en verde antes de marcar una tarea como hecha.

## Directory subtree

```
src/main/resources/db/migration/
  V3__player_level.sql                   # NEW (E4-T2)
  V4__match_event_play.sql               # NEW (E4-T4)
  V5__player_level_decimal.sql           # NEW (E4-T9)
src/main/java/com/futbolmanager/
  elo/EloCalculator.java                 # exists — edited (E4-T1)
  match/balance/TeamBalancer.java        # exists — edited (E4-T3, E4-T8)
  match/MatchController.java             # exists — edited (E4-T3, E4-T8)
  player/Player.java                     # exists — edited (E4-T2, E4-T8, E4-T9)
  player/PlayerController.java, PlayerService.java # exist — edited (E4-T2, E4-T9)
  live/MatchEvent.java, live/LiveController.java # exist — edited (E4-T4)
src/test/java/com/futbolmanager/
  elo/EloCalculatorTest.java             # edited (E4-T1)
  player/PlayerIntegrationTest.java      # edited (E4-T2, E4-T9)
  match/MatchIntegrationTest.java        # edited (E4-T2, E4-T3, E4-T8)
  live/LiveIntegrationTest.java          # edited (E4-T2, E4-T4)
  match/balance/TeamBalancerTest.java    # edited (E4-T3, E4-T8)
frontend/src/
  components/ui.tsx                      # exists — edited (E4-T7): DialogShell
  features/groups/api.ts, GroupDetailPage.tsx, GroupsPages.test.tsx  # edited (E4-T5; GroupsPages.test.tsx también E4-T10)
  features/groups/PlayersTab.tsx, PlayerForm.tsx, errorMessage.ts    # extraídos de GroupDetailPage (refactor entre E4-T5 y E4-T6); PlayersTab y PlayerForm se editan en E4-T10
  features/matches/api.ts, MatchSetupPage.tsx, MatchSetupPage.test.tsx # edited (E4-T6)
  features/live/api.ts, LivePage.tsx, LivePage.test.tsx # edited (E4-T7)
  features/live/EventDialog.tsx          # NEW (E4-T7)
```

Everything outside this subtree is out of scope. If a task seems to require editing a file not listed here, stop and report — it means the epic boundary is wrong. Las migraciones V1 y V2 **nunca** se editan.

## Data model touched here

| Entity | Cambio | Notes |
|---|---|---|
| `players` | V3: `level integer NOT NULL`, `CHECK (level BETWEEN 1 AND 10)`; V5 (E4-T9): `level_tenths integer NOT NULL`, `CHECK (level_tenths BETWEEN 10 AND 100)` y se elimina `level` | nivel × 10 (85 = 8,5); existentes (5 pasa a 5,0) y autocreados en 50; solo lo ven los ADMIN; la API lo expone como `level` decimal |
| `match_events` | `type` acepta `PLAY` (V4) | SAVE/TACKLE/PLAY: cualquier jugador del lineup del equipo |
| `elo_changes.details` | `{"goals":n,"assists":n,"mvp":0|3,"cleanSheet":0|2,"saves":0..3,"tackles":0..3,"plays":0..2,"uncapped":n,"total":n}` | puntos; todavía no hay filas BONUS escritas, así que no hay migración de datos |
| `match_lineup_entries.position` | sin cambio de esquema | pasa a ser solo informativo |

## Contracts

**Produced** — later work depends on exactly these signatures:

| Export | Signature | Used by |
|---|---|---|
| `EloCalculator.PlayerInput` | `(UUID playerId, int eloBefore, int previousCountedMatches, int goals, int assists, int saves, int tackles, int plays)` | `EloReplayService` (E3-T4) |
| `EloCalculator.BonusBreakdown` | `(goals, assists, mvp, cleanSheet, saves, tackles, plays, uncapped, total)` | `EloReplayService` (`details`) |
| `TeamBalancer.balance` | `Result balance(List<Candidate(UUID id, int levelTenths, int elo)>)` (`levelTenths` 10–100 tras E4-T8) | `MatchController`, `DemoDataSeeder` (E3-T6) |
| REST jugadores | respuestas con `level` (decimal, p. ej. `8.5`) solo para ADMIN; `POST` exige `level` 1,0–10,0 con un decimal; `PATCH` con `level` solo admin | frontend |
| REST balance | `POST …/balance` sin cuerpo → `{teamA:[{playerId,name,currentElo,level (decimal),position}], teamB, avgLevelA, avgLevelB, levelDifference, avgEloA, avgEloB, eloDifference}` | frontend |
| REST eventos | `type` ∈ `GOAL`/`SAVE`/`TACKLE`/`PLAY` | frontend, `EloReplayService` |
| `DialogShell` | componente de `components/ui` con overlay `z-40` y contenido `z-50` | `EventDialog`, `CloseMatchDialog` (E3-T5) |

## Conventions that bite in this area

- **Posición informativa:** nada en el ELO ni en el balance lee `position` ni `preferred_position`. Los selects de puesto de la UI se conservan como dato.
- **Bonus por jugador:** gol +2, asistencia +1, MVP +3, valla invicta +2 para cada jugador del equipo cuyo rival hizo 0, atajada +1 (tope +3, cualquier jugador), corte +1 (tope +3), jugadón +1 (tope +2); tope total +10; nunca negativo.
- **Nivel solo para admins:** la API lo omite (no lo manda en `null`) para un MEMBER; la UI no lo muestra. `balance` es solo ADMIN. Ni el detalle del partido, ni el lineup, ni el ranking, ni el perfil devuelven `level`.
- **Balanceo:** minimiza `|promNivelA − promNivelB|`; ante empate exacto, `|promEloA − promEloB|`; ante empate, lista de ids de A lexicográficamente menor. Fracciones exactas con `long` sobre **décimas enteras** de nivel (nunca `double` para comparar).
- **Nivel con un decimal:** se guarda en décimas (`level_tenths`) y la API lo expone como decimal; el JSON se lee con `BigDecimal` (no `double`) y se valida con `@Digits(integer = 2, fraction = 1)`. El formulario acepta coma o punto y muestra `5,0`.
- **Diálogos del modo en vivo:** siempre con `DialogShell` (overlay `z-40`, contenido `z-50`) porque el marcador sticky es `z-10`. jsdom no tiene layout: el test comprueba las utilidades, no los píxeles.
- **Migraciones:** skill `add-migration`; V3, V4 y V5 son forward-only; `ddl-auto: validate` exige que `Player` mapee `level_tenths` desde E4-T9.

Full project rules: `CLAUDE.md`. Area rules: `.claude/rules/backend-java.md`, `.claude/rules/database.md`, `.claude/rules/frontend.md`, `.claude/rules/testing.md`.

---

## Tasks

Listed in the same order as `tasks.json`. That order is the build order — work top to bottom and do not re-rank by priority or by what looks quick.

### `E4-T1` — Rework ELO bonus rules

**Depends on:** `E2-T4`, `E3-T3` · **Priority:** p0

`EloCalculator` sin puestos: atajada de cualquier jugador, jugadón y valla invicta para todo el equipo.

- `src/main/java/com/futbolmanager/elo/EloCalculator.java` (editar) — se elimina `Position` y el campo `position` de `PlayerInput`, que pasa a `(playerId, eloBefore, previousCountedMatches, goals, assists, saves, tackles, plays)`. Bonus por jugador (nunca negativo): gol +2 c/u; asistencia +1 c/u; MVP +3; valla invicta +2 para **cada** jugador del equipo cuyo rival hizo 0 goles, sin importar el puesto; atajada +1 c/u de **cualquier** jugador, tope +3; corte +1 c/u, tope +3; jugadón +1 c/u, tope +2; **bonus total con tope +10**. `BonusBreakdown(goals, assists, mvp, cleanSheet, saves, tackles, plays, uncapped, total)`. Sigue puro: sin `org.springframework` ni `jakarta.persistence`.
- `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` (editar) — el helper de `PlayerInput` pierde el puesto y gana `plays`; se borran `ignoresSavesForPlayersThatAreNotTheMatchGoalkeeper` y `awardsCleanSheetToDefenderOnATeamWithoutGoalkeeper`; se reescriben `capsGoalkeeperSavesAtThree`, `awardsCleanSheetOnlyToGoalkeepersAndDefenders` y `addsCleanSheetAndCappedSavesForAGoalkeeper`; se agregan tests del tope de jugadones, de la valla para todo el equipo y de bonus nunca negativo con `plays`.

**Files**
- `src/main/java/com/futbolmanager/elo/EloCalculator.java` — edit
- `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** un jugador de cualquier puesto registra 5 atajadas **THE SYSTEM SHALL** calcular `saves` 3 en su desglose (tope por jugador).
2. **WHEN** un jugador registra 3 jugadones **THE SYSTEM SHALL** calcular `plays` 2, y con 1 jugadón `plays` 1.
3. **WHEN** el rival hizo 0 goles **THE SYSTEM SHALL** calcular `cleanSheet` 2 para todos los jugadores del equipo sin importar el puesto, y **WHEN** el rival anotó **THE SYSTEM SHALL** calcular `cleanSheet` 0.
4. **WHEN** goles, asistencias, MVP, valla, atajadas, cortes y jugadones suman más de 10 **THE SYSTEM SHALL** devolver `bonus` 10 con `uncapped` igual a la suma real, y el `bonus` nunca SHALL ser negativo, tampoco con contadores negativos.
5. **WHEN** corre `grep` sobre `EloCalculator.java` **THE SYSTEM SHALL** no encontrar imports de `org.springframework` ni de `jakarta.persistence`.
6. **WHEN** corren `EloCalculatorTest` y `./mvnw -q verify` **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=EloCalculatorTest
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/elo/EloCalculator.java || status=$?; test "$status" -eq 1
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E4-T1: rework elo bonus rules"
git tag step-16-elo-bonus-rules
```

### `E4-T2` — Add admin-only player level

> **Superseded en parte por E4-T9: el nivel pasa a guardarse en décimas (`level_tenths`, V5) y la API acepta y devuelve un decimal (`8.5`).** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en las tareas indicadas.

**Depends on:** `E3-T3` · **Priority:** p0

`players.level` (V3), obligatorio al crear, editable solo por el admin y que la API no devuelve a un MEMBER.

- `src/main/resources/db/migration/V3__player_level.sql` — `ALTER TABLE players ADD COLUMN level integer NOT NULL DEFAULT 5;` (los jugadores existentes quedan en 5), `ALTER TABLE players ADD CONSTRAINT ck_players_level CHECK (level BETWEEN 1 AND 10);` y `ALTER TABLE players ALTER COLUMN level DROP DEFAULT;` (a partir de acá el nivel lo exige la base). Se crea con la skill `add-migration`; V1 y V2 no se tocan.
- `src/main/java/com/futbolmanager/player/Player.java` — campo `level` (int, no nulo), parámetro en el constructor y getter/setter.
- `src/main/java/com/futbolmanager/player/PlayerService.java` — `create(...)` recibe `level`; el jugador que se crea al crear el grupo o al unirse (`onMemberJoined`) nace con nivel 5.
- `src/main/java/com/futbolmanager/player/PlayerController.java` — `CreatePlayerRequest.level` con `@NotNull @Min(1) @Max(10) Integer`; `UpdatePlayerRequest.level` con `@Min(1) @Max(10) Integer` (opcional); `PlayerResponse.level` es `Integer` y se omite del JSON cuando es null (`@JsonInclude(JsonInclude.Include.NON_NULL)`). **El nivel solo lo ven los ADMIN del grupo:** `GET`, `POST` y `PATCH` rellenan `level` solo si quien llama es ADMIN; para un MEMBER es null (se omite). `PATCH` con `level` de un MEMBER, incluso sobre su propio jugador → 403 `Solo un administrador puede cambiar el nivel.`.
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` — los altas mandan `level`; casos nuevos de falta de nivel, rango, permisos, visibilidad por rol, valor por defecto y CHECK de la base (insert directo con `JdbcClient`). En `MatchIntegrationTest` y `LiveIntegrationTest` solo se agrega `level` al alta de jugadores.

**Excepción deliberada a la regla de 5 archivos (7 archivos):** `MatchIntegrationTest` y `LiveIntegrationTest` solo cambian una línea cada uno (el alta de jugadores por API ahora exige `level`); no se pueden diferir sin dejar el gate de este paso en rojo.

**Files**
- `src/main/resources/db/migration/V3__player_level.sql` — new
- `src/main/java/com/futbolmanager/player/Player.java` — edit
- `src/main/java/com/futbolmanager/player/PlayerController.java` — edit
- `src/main/java/com/futbolmanager/player/PlayerService.java` — edit
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` — edit
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` — edit
- `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** se envía `POST /api/groups/{groupId}/players` sin `level`, o con `level` 0 u 11, **THE SYSTEM SHALL** responder 400 con un error en el campo `level`, y **WHEN** se inserta un jugador con `level` fuera de 1–10 directamente en la base **THE SYSTEM SHALL** rechazarlo por el CHECK `ck_players_level`.
2. **WHEN** un admin envía `PATCH` con un `level` válido **THE SYSTEM SHALL** responder 200 con el nuevo nivel, y **WHEN** un `MEMBER` envía `level`, incluso sobre su propio jugador, **THE SYSTEM SHALL** responder 403.
3. **WHEN** un `MEMBER` pide `GET /api/groups/{groupId}/players` o edita su propio jugador con `PATCH` **THE SYSTEM SHALL** devolver jugadores sin el campo `level`, y **WHEN** lo pide un `ADMIN` **THE SYSTEM SHALL** incluir `level` en cada jugador.
4. **WHEN** se crea el jugador del creador de un grupo o de quien se une **THE SYSTEM SHALL** asignarle nivel 5, y **WHEN** `V3__player_level.sql` se aplica **THE SYSTEM SHALL** dejar los jugadores existentes en nivel 5 (`ADD COLUMN level integer NOT NULL DEFAULT 5`).
5. **WHEN** corren `PlayerIntegrationTest` y `./mvnw -q verify` **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=PlayerIntegrationTest
grep -q 'ADD COLUMN level integer NOT NULL DEFAULT 5' src/main/resources/db/migration/V3__player_level.sql
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E4-T2: add admin-only player level"
git tag step-17-player-level
```

### `E4-T3` — Balance teams by level

> **Superseded en parte por E4-T8: el balanceador recibe el nivel en décimas enteras (`levelTenths`, 10–100) y `balance` devuelve `level` con un decimal.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en las tareas indicadas.

**Depends on:** `E4-T2`, `E2-T5` · **Priority:** p0

El balanceador minimiza la diferencia de nivel promedio y usa el ELO solo como desempate; sin opción de arquero.

- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` (editar) — `Candidate(UUID id, int level, int elo)`; se eliminan `Position`, `Assignment`, `oneGoalkeeperPerSide` y `goalkeeperConstraintApplied`. `balance(List<Candidate>)` devuelve `Result(List<UUID> teamA, List<UUID> teamB, double avgLevelA, double avgLevelB, double levelDifference, double avgEloA, double avgEloB, double eloDifference)`. Mismo recorrido exhaustivo (A contiene al menor id, `|A| ∈ {⌊n/2⌋, ⌈n/2⌉}`); objetivo en este orden: (1) minimizar `|promNivelA − promNivelB|`, (2) ante empate exacto, minimizar `|promEloA − promEloB|`, (3) ante empate, la lista de ids de A lexicográficamente menor. Las comparaciones usan fracciones exactas con `long` (sin flotantes). Niveles fuera de 1–10 → `IllegalArgumentException`. Sigue puro.
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` (editar) — se borran los 4 tests de arquero; el oráculo de fuerza bruta compara la tupla (diferencia de nivel, diferencia de ELO); tests nuevos de nivel antes que ELO, ELO como desempate, validación del rango y desempate por ids.
- `src/main/java/com/futbolmanager/match/MatchController.java` (editar) — `POST …/balance` sin cuerpo (se elimina `BalanceRequest`); `BalanceResponse(teamA, teamB, avgLevelA, avgLevelB, levelDifference, avgEloA, avgEloB, eloDifference)` con `BalancedPlayer(playerId, name, currentElo, level, position)`; `position` es el puesto preferido del jugador (informativo). El endpoint es solo ADMIN, así que el nivel nunca llega a un MEMBER; ni el detalle del partido ni el lineup devuelven `level`.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` (editar) — el balance sin `oneGoalkeeperPerSide` y con el contrato nuevo; un MEMBER recibe 403 en `balance` y ni el detalle del partido ni el lineup que ve contienen `level`.

**Files**
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` — edit
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` — edit
- `src/main/java/com/futbolmanager/match/MatchController.java` — edit
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** `TeamBalancerTest` compara contra el oráculo de fuerza bruta (n ≤ 10) **THE SYSTEM SHALL** producir una partición con la mínima diferencia de nivel promedio y, entre las empatadas, la de menor diferencia de ELO promedio.
2. **WHEN** dos particiones empatan en diferencia de nivel y de ELO **THE SYSTEM SHALL** elegir la que tiene la lista de ids de A lexicográficamente menor, con el menor id siempre en A.
3. **WHEN** se balancean 22 jugadores **THE SYSTEM SHALL** terminar en menos de 2 segundos.
4. **WHEN** un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` sin cuerpo **THE SYSTEM SHALL** responder cada jugador con `level` y `position` y los totales `avgLevelA`, `avgLevelB`, `levelDifference`, `avgEloA`, `avgEloB`, `eloDifference`, sin `goalkeeperConstraintApplied`.
5. **WHEN** un `MEMBER` pide `balance` **THE SYSTEM SHALL** responder 403, y **WHEN** un `MEMBER` pide el detalle del partido o el lineup **THE SYSTEM SHALL** responder sin ningún campo `level`.
6. **WHEN** corren `TeamBalancerTest`, `MatchIntegrationTest` y `./mvnw -q verify`, y `grep` no encuentra imports de `org.springframework` ni `jakarta.persistence` en `TeamBalancer.java`, **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=TeamBalancerTest
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/match/balance/TeamBalancer.java || status=$?; test "$status" -eq 1
./mvnw -q test -Dtest=MatchIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E4-T3: balance teams by level"
git tag step-18-level-balancer
```

### `E4-T4` — Add jugadon events and open saves

**Depends on:** `E3-T2` · **Priority:** p0

Tipo de evento `PLAY` (Jugadón) y atajadas registrables por cualquier jugador del lineup.

- `src/main/resources/db/migration/V4__match_event_play.sql` — `ALTER TABLE match_events DROP CONSTRAINT ck_match_events_type;` y `ALTER TABLE match_events ADD CONSTRAINT ck_match_events_type CHECK (type IN ('GOAL', 'SAVE', 'TACKLE', 'PLAY'));`. Se crea con la skill `add-migration`.
- `src/main/java/com/futbolmanager/live/MatchEvent.java` (editar) — `enum Type { GOAL, SAVE, TACKLE, PLAY }`.
- `src/main/java/com/futbolmanager/live/LiveController.java` (editar) — `EventRequest.type` acepta `PLAY`. Reglas 422 nuevas: `SAVE`, `TACKLE` y `PLAY` exigen `playerId` del lineup de `team` (la atajada ya no exige ser el arquero: se elimina la comprobación de `position == GK`); `PLAY` no admite `assistPlayerId`. El resto (permisos, idempotencia, anulación, marcador derivado de los GOAL) no cambia; los `PLAY` no alteran el marcador.
- `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` (editar) — `saveMustComeFromThatTeamsGoalkeeperAndTackleFromItsLineup` se reescribe como `saveAndTackleAndPlayComeFromThatTeamsLineup`; casos nuevos de `PLAY` (201, idempotencia, 422, anulación, orden en el estado en vivo) y del CHECK de la base.

**Files**
- `src/main/resources/db/migration/V4__match_event_play.sql` — new
- `src/main/java/com/futbolmanager/live/MatchEvent.java` — edit
- `src/main/java/com/futbolmanager/live/LiveController.java` — edit
- `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** un admin envía un `PLAY` con un jugador del lineup de `team` y un `id` UUID nuevo **THE SYSTEM SHALL** responder 201; **WHEN** reenvía el mismo `id` con el mismo payload **THE SYSTEM SHALL** responder 200 sin duplicar; **WHEN** reenvía el mismo `id` con otro payload **THE SYSTEM SHALL** responder 409.
2. **WHEN** se envía un `PLAY` sin `playerId`, con un jugador fuera del lineup de `team` o con `assistPlayerId` **THE SYSTEM SHALL** responder 422.
3. **WHEN** se envía un `SAVE` de cualquier jugador del lineup de `team`, sea arquero o de campo, **THE SYSTEM SHALL** responder 201, y **WHEN** el jugador falta o no está en ese lineup **THE SYSTEM SHALL** responder 422.
4. **WHEN** se pide `GET …/live` con eventos `PLAY` **THE SYSTEM SHALL** incluirlos en orden de `createdAt` sin cambiar `scoreA` ni `scoreB`.
5. **WHEN** se inserta en `match_events` un `type` distinto de `GOAL`, `SAVE`, `TACKLE` o `PLAY` **THE SYSTEM SHALL** rechazarlo por el CHECK `ck_match_events_type`, y `PLAY` SHALL ser aceptado.
6. **WHEN** corren `LiveIntegrationTest` y `./mvnw -q verify` **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=LiveIntegrationTest
grep -q "'GOAL', 'SAVE', 'TACKLE', 'PLAY'" src/main/resources/db/migration/V4__match_event_play.sql
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E4-T4: add jugadon events and open saves"
git tag step-19-play-events
```

### `E4-T5` — Add admin-only level to player screens

> **Superseded en parte por E4-T10: el campo «Nivel» acepta coma o punto, envía un número decimal y se muestra con un decimal y coma.** Los criterios de esta tarea quedan como historial de lo construido; el comportamiento vigente está en las tareas indicadas.

**Depends on:** `E4-T2` · **Priority:** p0

Campo «Nivel» (1–10) al crear y editar jugadores; un MEMBER no lo ve en ninguna parte.

- `frontend/src/features/groups/api.ts` (editar) — `Player.level?: number` (ausente para un MEMBER) y `PlayerInput.level?: number`.
- `frontend/src/features/groups/GroupDetailPage.tsx` (editar) — solo con `myRole === 'ADMIN'`: campo obligatorio «Nivel (1 a 10)» (`input type=number`, `min=1`, `max=10`, con `label`; error con `aria-invalid` + `aria-describedby`) en el alta y la edición de jugadores, y el nivel junto al nombre en la lista. Un MEMBER no ve el campo, no lo envía y no ve ningún nivel.
- `frontend/src/features/groups/GroupsPages.test.tsx` (editar) — el alta manda `level`; casos de validación, edición por admin y ausencia total del nivel para un MEMBER.

**Files**
- `frontend/src/features/groups/api.ts` — edit
- `frontend/src/features/groups/GroupDetailPage.tsx` — edit
- `frontend/src/features/groups/GroupsPages.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** el admin envía el alta de un jugador con «Nivel» 7 **THE SYSTEM SHALL** hacer `POST /api/groups/{groupId}/players` con `level` 7 como entero.
2. **WHEN** el campo «Nivel» está vacío o fuera de 1–10 **THE SYSTEM SHALL** no enviar la petición y mostrar el error asociado al campo con `aria-invalid`.
3. **WHEN** el admin edita un jugador y cambia el nivel **THE SYSTEM SHALL** hacer `PATCH /api/groups/{groupId}/players/{playerId}` con `level`.
4. **WHEN** un `MEMBER` ve la lista de jugadores o edita su propio jugador **THE SYSTEM SHALL** no mostrar ni enviar el nivel en ninguna parte de la pantalla.
5. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

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
git add -A && git commit -m "E4-T5: add admin-only level to player screens"
git tag step-20-level-ui
```

### `E4-T8` — Balance teams by decimal level in tenths

**Depends on:** `E4-T3` · **Priority:** p0

El balanceador compara el nivel en décimas enteras (8,5 → 85) y `balance` devuelve `level` con un decimal.

- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` (editar) — `Candidate(UUID id, int levelTenths, int elo)`; `levelTenths` es el nivel × 10 (8,5 → 85) y debe estar entre 10 y 100, si no `IllegalArgumentException`. Las sumas y los numeradores de la comparación exacta siguen siendo `long`, ahora sobre décimas enteras; el orden de desempate (nivel, ELO, ids de A) no cambia. `Result` conserva su forma y expresa los promedios de nivel en unidades de nivel (`suma de décimas ÷ (10 · tamaño)`). Sigue puro: sin `org.springframework` ni `jakarta.persistence`.
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` (editar) — los candidatos pasan a décimas y el oráculo de fuerza bruta compara en décimas; casos nuevos de niveles decimales (8,5 y 4,8), de empate por una décima, del rango (9 y 101 rechazados; 10 y 100 aceptados) y de 22 jugadores en menos de 2 s.
- `src/main/java/com/futbolmanager/player/Player.java` (editar) — `getLevelTenths()` devuelve `level * 10`: es un puente transitorio que el paso 22 reemplaza por la columna nueva, para que este paso no rompa la compilación de `MatchController`.
- `src/main/java/com/futbolmanager/match/MatchController.java` (editar) — arma `Candidate` con `getLevelTenths()`; `BalancedPlayer.level` pasa a `BigDecimal.valueOf(levelTenths, 1)` (un número con un decimal, p. ej. `5.0`). `balance` sigue siendo solo ADMIN y el detalle del partido y el lineup siguen sin nivel.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` (editar) — `$.teamB[*].level` se lee como números con un decimal en vez de enteros; un MEMBER sigue recibiendo 403 en `balance` y sin `level` en detalle y lineup.

**Files**
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` — edit
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` — edit
- `src/main/java/com/futbolmanager/match/MatchController.java` — edit
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` — edit
- `src/main/java/com/futbolmanager/player/Player.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** `TeamBalancerTest` compara contra el oráculo de fuerza bruta (n ≤ 10) con niveles decimales como 8,5 y 4,8 **THE SYSTEM SHALL** producir una partición con la mínima diferencia exacta de nivel promedio y, entre las empatadas, la de menor diferencia de ELO promedio.
2. **WHEN** dos particiones difieren en una sola décima de nivel **THE SYSTEM SHALL** elegir la de menor diferencia sin errores de coma flotante, comparando décimas enteras con `long`.
3. **WHEN** un candidato trae `levelTenths` 9 o 101 **THE SYSTEM SHALL** lanzar `IllegalArgumentException`, y **WHEN** trae 10 o 100 **THE SYSTEM SHALL** aceptarlo.
4. **WHEN** se balancean 22 jugadores **THE SYSTEM SHALL** terminar en menos de 2 segundos.
5. **WHEN** un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` sin cuerpo **THE SYSTEM SHALL** responder cada jugador con `level` como número con un decimal (por ejemplo 5.0) y los totales `avgLevelA`, `avgLevelB`, `levelDifference`, `avgEloA`, `avgEloB` y `eloDifference`, y **WHEN** un `MEMBER` pide `balance` **THE SYSTEM SHALL** responder 403.
6. **WHEN** corren `TeamBalancerTest`, `MatchIntegrationTest` y `./mvnw -q verify`, y `grep` no encuentra imports de `org.springframework` ni `jakarta.persistence` en `TeamBalancer.java`, **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=TeamBalancerTest
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/match/balance/TeamBalancer.java || status=$?; test "$status" -eq 1
./mvnw -q test -Dtest=MatchIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E4-T8: balance teams by decimal level in tenths"
git tag step-21-balance-tenths
```

### `E4-T9` — Store and serve player level in tenths

**Depends on:** `E4-T8`, `E4-T2` · **Priority:** p0

`players.level_tenths` (V5) guarda el nivel en décimas enteras; la API de jugadores acepta y devuelve un decimal (`8.5`), solo para admins.

- `src/main/resources/db/migration/V5__player_level_decimal.sql` — `ALTER TABLE players ADD COLUMN level_tenths integer;`, `UPDATE players SET level_tenths = level * 10;`, `ALTER TABLE players ALTER COLUMN level_tenths SET NOT NULL;`, `ALTER TABLE players ADD CONSTRAINT ck_players_level_tenths CHECK (level_tenths BETWEEN 10 AND 100);` y `ALTER TABLE players DROP COLUMN level;` (arrastra `ck_players_level`). Los niveles existentes quedan igual (5 pasa a 50 décimas = 5,0). Se crea con la skill `add-migration`; V1–V4 no se tocan.
- `src/main/java/com/futbolmanager/player/Player.java` (editar) — el campo `level` pasa a `levelTenths` (`@Column(name = "level_tenths")`, int, no nulo) con su parámetro de constructor y getter/setter; se elimina el puente `getLevelTenths()` del paso 21 a favor del getter real.
- `src/main/java/com/futbolmanager/player/PlayerService.java` (editar) — `create(...)` recibe `levelTenths`; el jugador que se crea al crear el grupo o al unirse nace con 50.
- `src/main/java/com/futbolmanager/player/PlayerController.java` (editar) — `CreatePlayerRequest.level` pasa a `BigDecimal` con `@NotNull @DecimalMin("1.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1)`; `UpdatePlayerRequest.level` igual pero opcional. Se convierte con `movePointRight(1).intValueExact()` (el texto del JSON se lee exacto, sin pasar por `double`). `PlayerResponse.level` es `BigDecimal` (`BigDecimal.valueOf(tenths, 1)`, p. ej. `5.0`) y se omite del JSON cuando es null. El nivel sigue siendo solo para ADMIN y un MEMBER que lo envía recibe 403.
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` (editar) — `levelOf` lee `level_tenths`; el alta y el `PATCH` con 8.5 y 4.8; rechazo de 0.9, 10.1, 8.55, 0, 11, falta de nivel y valor no numérico; CHECK `ck_players_level_tenths` con insert directo (9 y 101); autocreados en 50 y respuesta `5.0`; visibilidad por rol sin cambios.

**Files**
- `src/main/resources/db/migration/V5__player_level_decimal.sql` — new
- `src/main/java/com/futbolmanager/player/Player.java` — edit
- `src/main/java/com/futbolmanager/player/PlayerService.java` — edit
- `src/main/java/com/futbolmanager/player/PlayerController.java` — edit
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** un admin envía `POST /api/groups/{groupId}/players` con `level` 8.5 y otro con 4.8 **THE SYSTEM SHALL** responder 201 con `level` 8.5 y 4.8 y guardar 85 y 48 en `players.level_tenths`.
2. **WHEN** `level` es 0.9, 10.1, 8.55, 0, 11 o falta **THE SYSTEM SHALL** responder 400 con un error en el campo `level`, tanto en el alta como en `PATCH`, y **WHEN** `level` no es un número **THE SYSTEM SHALL** responder 400.
3. **WHEN** se inserta un jugador con `level_tenths` 9 o 101 directamente en la base **THE SYSTEM SHALL** rechazarlo por el CHECK `ck_players_level_tenths`.
4. **WHEN** se crea el jugador del creador de un grupo o de quien se une **THE SYSTEM SHALL** asignarle nivel 5.0 (50 décimas), y **WHEN** `V5__player_level_decimal.sql` se aplica **THE SYSTEM SHALL** convertir los niveles existentes con `level_tenths = level * 10`.
5. **WHEN** un `MEMBER` pide `GET /api/groups/{groupId}/players` o edita su propio jugador **THE SYSTEM SHALL** devolver jugadores sin el campo `level`, y **WHEN** un `MEMBER` envía `level` en `PATCH` **THE SYSTEM SHALL** responder 403.
6. **WHEN** corren `PlayerIntegrationTest` y `./mvnw -q verify` **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=PlayerIntegrationTest
grep -q 'level_tenths BETWEEN 10 AND 100' src/main/resources/db/migration/V5__player_level_decimal.sql
grep -q 'SET level_tenths = level \* 10' src/main/resources/db/migration/V5__player_level_decimal.sql
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E4-T9: store player level in tenths"
git tag step-22-level-tenths
```

### `E4-T10` — Accept decimal level in the player form

**Depends on:** `E4-T9`, `E4-T5` · **Priority:** p0

El campo «Nivel» acepta coma o punto, muestra un decimal y sigue siendo solo para admins.

- `frontend/src/features/groups/PlayerForm.tsx` (editar) — el campo «Nivel (1 a 10)» pasa de `type=number` a `type=text` con `inputMode="decimal"` y `placeholder` «Ej.: 8,5» (el `number` no acepta la coma de forma consistente). Acepta coma o punto: se normaliza la coma a punto y se valida `^\d{1,2}(\.\d)?$` con rango 1,0–10,0; inválido (vacío, 0,9, 10,1, 8,55, texto) no envía y marca el campo con `aria-invalid` + `aria-describedby`. Envía `level` como número (`8.5`). Al editar precarga el nivel con un decimal y coma (`5,0`). `formatLevel(n)` (un decimal, coma) se exporta para la lista.
- `frontend/src/features/groups/PlayersTab.tsx` (editar) — la lista muestra `Nivel 5,0` con `formatLevel`, solo para ADMIN.
- `frontend/src/features/groups/GroupsPages.test.tsx` (editar) — el alta con `8,5`, `8.5` y `4,8` envía 8.5, 8.5 y 4.8; casos inválidos (vacío, `0,9`, `10,1`, `8,55`, `abc`); la edición precarga `5,0` y envía el nivel nuevo; un MEMBER no ve ni envía nivel. `Nivel 5` y `toHaveValue(5)` pasan a `Nivel 5,0` y `"5,0"`.

**Files**
- `frontend/src/features/groups/PlayerForm.tsx` — edit
- `frontend/src/features/groups/PlayersTab.tsx` — edit
- `frontend/src/features/groups/GroupsPages.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** el admin envía el alta con «Nivel» `8,5` u `8.5` **THE SYSTEM SHALL** hacer `POST /api/groups/{groupId}/players` con `level` 8.5 como número, y con `4,8` **THE SYSTEM SHALL** enviar 4.8.
2. **WHEN** el campo «Nivel» está vacío, fuera de 1,0–10,0 (`0,9`, `10,1`), con más de un decimal (`8,55`) o no es un número **THE SYSTEM SHALL** no enviar la petición y mostrar el error asociado al campo con `aria-invalid`.
3. **WHEN** el admin edita un jugador **THE SYSTEM SHALL** precargar el nivel con un decimal y coma (`5,0`), y **WHEN** cambia el nivel **THE SYSTEM SHALL** hacer `PATCH /api/groups/{groupId}/players/{playerId}` con `level`.
4. **WHEN** un `ADMIN` ve la lista de jugadores **THE SYSTEM SHALL** mostrar `Nivel 5,0` con un decimal, y **WHEN** un `MEMBER` ve la lista o edita su propio jugador **THE SYSTEM SHALL** no mostrar ni enviar el nivel en ninguna parte de la pantalla.
5. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

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
git add -A && git commit -m "E4-T10: accept decimal level in the player form"
git tag step-23-level-decimal-form
```

### `E4-T6` — Show level balance in team builder

**Depends on:** `E4-T3`, `E3-T1`, `E4-T8` · **Priority:** p0

El armado de equipos muestra nivel promedio y diferencia de nivel (solo admin); desaparece la opción de arquero.

- `frontend/src/features/matches/api.ts` (editar) — `BalanceResult`/`BalancedPlayer` con el contrato de E4-T3 y E4-T8 (`level` como número con un decimal, `avgLevelA/B`, `levelDifference`; sin `goalkeeperConstraintApplied`); `useBalance` es una mutación sin argumentos que hace `POST …/balance` sin cuerpo.
- `frontend/src/features/matches/MatchSetupPage.tsx` (editar) — se elimina la casilla «Un arquero por equipo»; en el armado (solo admin) cada equipo muestra «Nivel promedio» y debajo «Diferencia de nivel: N», y como dato secundario el ELO promedio y la diferencia de ELO; el select «Puesto de {nombre}» se conserva como dato informativo. Un MEMBER ve convocatoria y equipos guardados sin ningún nivel.
- `frontend/src/features/matches/MatchSetupPage.test.tsx` (editar) — el balance sin cuerpo y con el contrato nuevo; se borran los chequeos de `Un arquero por equipo`; caso de MEMBER sin niveles.

**Files**
- `frontend/src/features/matches/api.ts` — edit
- `frontend/src/features/matches/MatchSetupPage.tsx` — edit
- `frontend/src/features/matches/MatchSetupPage.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** se activa `Armar equipos` **THE SYSTEM SHALL** hacer `POST …/balance` sin `oneGoalkeeperPerSide` y mostrar ambos equipos con nivel promedio, `Diferencia de nivel: N` y, como dato secundario, el ELO promedio y la diferencia de ELO.
2. **WHEN** se renderiza el armado de equipos **THE SYSTEM SHALL** no mostrar ninguna casilla `Un arquero por equipo`.
3. **WHEN** se activa con el teclado `Pasar al equipo B` **THE SYSTEM SHALL** mover al jugador sin arrastrar, y cada select de puesto SHALL conservar el nombre accesible `Puesto de {nombre}`.
4. **WHEN** un `MEMBER` abre el detalle del partido **THE SYSTEM SHALL** mostrar la convocatoria y los equipos guardados sin controles de armado y sin ningún valor de nivel.
5. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

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
git add -A && git commit -m "E4-T6: show level balance in team builder"
git tag step-24-balance-level-ui
```

### `E4-T7` — Add jugadon and open saves to live mode

**Depends on:** `E4-T4`, `E3-T3` · **Priority:** p0

Botones `Jugadón A/B`, atajada de cualquier jugador y todos los diálogos del modo en vivo por encima del marcador.

- `frontend/src/components/ui.tsx` (editar) — nuevo `DialogShell` sobre `radix-ui` Dialog: overlay con `fixed inset-0 z-40 bg-background/80` y contenido con `z-50` (por encima del marcador sticky `z-10`), `max-h-[calc(100dvh-8rem)] overflow-y-auto` y la sombra de diálogo del sistema de diseño. **Todo diálogo del modo en vivo (autor/asistencia ahora, cierre de partido en el paso 27) se arma con `DialogShell`**, así la capa correcta no depende de cada pantalla.
- `frontend/src/features/live/api.ts` (editar) — `EventType` suma `'PLAY'`.
- `frontend/src/features/live/EventDialog.tsx` — el diálogo de autor/asistencia extraído de `LivePage.tsx` (mantiene cada archivo ≤ 400 líneas y cada componente ≤ 300) sobre `DialogShell`; en atajadas, cortes y jugadones lista a todos los jugadores del lineup del equipo **sin preseleccionar a nadie** (el arquero ya no es especial); el gol sigue ofreciendo `En contra / no sé` y asistencia opcional.
- `frontend/src/features/live/LivePage.tsx` (editar) — botones `Button size="live"` `Jugadón A` y `Jugadón B`; la lista de eventos muestra `Jugadón del equipo {equipo}: {jugador}`.
- `frontend/src/features/live/LivePage.test.tsx` (editar) — casos de `Jugadón`, de la atajada sin preselección y del chequeo de capas. `.claude/rules/testing.md` admite, junto a `min-h-16`, el chequeo de las utilidades `z-40`/`z-50` de los diálogos, porque jsdom no tiene layout.

**Files**
- `frontend/src/components/ui.tsx` — edit
- `frontend/src/features/live/api.ts` — edit
- `frontend/src/features/live/EventDialog.tsx` — new
- `frontend/src/features/live/LivePage.tsx` — edit
- `frontend/src/features/live/LivePage.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** se renderiza el panel de registro **THE SYSTEM SHALL** mostrar `Jugadón A` y `Jugadón B` con la utilidad `min-h-16`, y **WHEN** se activa `Jugadón A` y se elige el jugador **THE SYSTEM SHALL** hacer `POST …/events` con `type` `PLAY`, `team` `A`, el `playerId` elegido y un `id` UUID.
2. **WHEN** se activa `Atajada A` **THE SYSTEM SHALL** abrir el diálogo con todos los jugadores del lineup del equipo A, sin preseleccionar a nadie, y exigir elegir uno antes de registrar.
3. **WHEN** la lista de eventos incluye un `PLAY` **THE SYSTEM SHALL** mostrarlo como `Jugadón del equipo {equipo}: {jugador}` con `cargado por {displayName}` y `Anular` según las reglas de autor y admin.
4. **WHEN** se abre cualquier diálogo del modo en vivo **THE SYSTEM SHALL** renderizar su overlay con la utilidad `z-40` y su contenido con `z-50`, ambos por encima del marcador sticky (`z-10`), porque todos se arman con `DialogShell`.
5. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

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
git add -A && git commit -m "E4-T7: add jugadon and open saves to live mode"
git tag step-25-live-play-ui
```

## Epic acceptance

- [ ] `./mvnw -q verify` y los cinco comandos de frontend salen con 0.
- [ ] `git tag -l 'step-1[6-9]-*' 'step-2[0-5]-*'` lista los diez tags de este epic (`step-16-elo-bonus-rules` … `step-25-live-play-ui`).
- [ ] Ningún archivo de migración existente (V1–V4) cambió; el nivel decimal vive en `V5__player_level_decimal.sql`.

## Pitfalls

- No dejar `level` en ninguna respuesta que vea un MEMBER (jugadores, balance, partido, lineup, ranking, perfil).
- No volver a leer `position` en el cálculo de ELO ni en el balanceo.
- No comparar niveles con `double`: el balanceador trabaja en décimas enteras (`long`); el decimal solo existe en la API y en la pantalla.
- No armar un diálogo del modo en vivo sin `DialogShell`: queda debajo del marcador.

## Before moving on

Con las diez tareas hechas, E3-T4 (cierre y libro ELO) usa el `EloCalculator` y los tipos de evento nuevos tal como quedan acá.
