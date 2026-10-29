# Epic 05: Equipos por suma de nivel y alternativas

> Al terminar, el armado de equipos mide la fuerza de cada equipo con la SUMA del nivel de sus jugadores (el ELO sigue siendo solo desempate, también por suma); `balance` devuelve las 5 mejores particiones distintas; la pantalla muestra por equipo la cantidad de jugadores y el nivel total, avisa si al mover jugadores a mano la diferencia de cantidad supera 1, y un botón «Otra opción» recorre las alternativas.

| | |
|---|---|
| **Epic id** | `05-equipos-por-suma-y-alternativas` |
| **Tasks** | `E5-T1` … `E5-T3` |
| **Depends on** | `04-arqueros-rotativos-nivel-y-jugadon` (E4-T6, E4-T8) y `E3-T6` (el `DemoDataSeeder` usa `TeamBalancer`) |
| **Unlocks** | nada |
| **Parallel with** | ninguno |

Cambio de producto aprobado con el proyecto completo (28 pasos hechos). En `tasks.json` estas tareas van **al final** del array, después de E3-T6, y sus pasos son el 29, 30 y 31: no se renumera nada porque los tags `step-01` … `step-28` ya existen. You do not need any other file to complete this epic. Everything below is repeated here on purpose.

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

**Gate:** `./mvnw -q verify` (tarea de backend) o los cinco comandos de frontend (tareas de frontend) en verde antes de marcar una tarea como hecha.

## Directory subtree

```
src/main/java/com/futbolmanager/
  match/balance/TeamBalancer.java        # exists — edited (E5-T1)
  match/MatchController.java             # exists — edited (E5-T1)
  config/DemoDataSeeder.java             # exists — edited (E5-T1): toma la primera alternativa
src/test/java/com/futbolmanager/
  match/balance/TeamBalancerTest.java    # edited (E5-T1)
  match/MatchIntegrationTest.java        # edited (E5-T1)
frontend/src/features/matches/
  api.ts                                 # exists — edited (E5-T2)
  MatchSetupPage.tsx                     # exists — edited (E5-T2): se le extraen LineupBuilder y TeamColumn
  MatchSetupPage.test.tsx                # exists — edited (E5-T2)
  LineupBuilder.tsx                      # NEW (E5-T2), edited (E5-T3)
  TeamColumn.tsx                         # NEW (E5-T2)
  LineupBuilder.test.tsx                 # NEW (E5-T3)
```

Everything outside this subtree is out of scope. If a task seems to require editing a file not listed here, stop and report — it means the epic boundary is wrong. No hay migraciones nuevas ni cambios de esquema.

## Data model touched here

Ninguno. El balanceo no guarda nada: `balance` solo propone; el lineup se guarda con `PUT …/lineup` como hasta ahora.

## Contracts

**Produced** — later work depends on exactly these signatures:

| Export | Signature | Used by |
|---|---|---|
| `TeamBalancer.balance` | `List<Result> balance(List<Candidate(UUID id, int levelTenths, int elo)>)` — entre 1 y 5 particiones distintas, mejor primero | `MatchController`, `DemoDataSeeder` (usa la primera) |
| `TeamBalancer.Result` | `(List<UUID> teamA, List<UUID> teamB, double totalLevelA, double totalLevelB, double levelDifference, double avgEloA, double avgEloB, double eloDifference)` | `MatchController` |
| REST balance | `POST …/balance` sin cuerpo → `{options:[{teamA:[{playerId,name,currentElo,level (decimal),position}], teamB, totalLevelA, totalLevelB, levelDifference, avgEloA, avgEloB, eloDifference}]}` (1 a 5 `options`; solo ADMIN) | frontend |

## Conventions that bite in this area

- **Medida de fuerza:** suma de décimas de nivel por equipo. La diferencia de nivel es `|ΣnivelA − ΣnivelB|` en décimas enteras (`long`), sin fracciones ni `double` para comparar. `levelDifference` y `totalLevel*` se calculan con una sola división por 10 sobre un entero.
- **Orden de las alternativas:** (1) menor diferencia de suma de nivel; (2) ante empate exacto, menor `|ΣeloA − ΣeloB|`; (3) ante empate, la lista de ids de A lexicográficamente menor. El menor id está siempre en A, así que A y B intercambiados no son particiones distintas. El desempate interno es por **suma** de ELO.
- **ELO en la respuesta y en pantalla:** `avgEloA`, `avgEloB` y `eloDifference` (diferencia de promedios) son el dato secundario, porque la suma de ELO con equipos de distinto tamaño confunde. El orden interno usa la suma; lo que se muestra es el promedio, así que la diferencia mostrada puede no ser monótona entre alternativas con tamaños distintos.
- **Tamaños:** el armado automático sigue generando equipos de igual tamaño o con 1 de diferencia (`|A| ∈ {⌊n/2⌋, ⌈n/2⌉}`). Con n impar la suma tiende a poner a los jugadores más flojos en el equipo más grande: es consecuencia directa de medir por suma.
- **Pocas alternativas:** con 2 jugadores hay 1 partición, con 3 hay 3 y con 4 hay 3; con más de 5 posibles se devuelven exactamente 5.
- **Aviso de cantidad:** si al mover jugadores a mano `|nA − nB| > 1` se muestra `Alert variant="error"` (`role="alert"`) con la cantidad de cada equipo. No bloquea guardar.
- **Nivel solo para admins:** `balance` es solo ADMIN; el armado solo lo ve el admin; el MEMBER sigue sin ver ningún nivel.
- **Determinismo:** misma entrada (en cualquier orden) → mismas alternativas en el mismo orden. El oráculo de fuerza bruta del test compara las tuplas (diferencia de nivel, diferencia de ELO) de las 5 mejores.
- **Rendimiento:** 22 jugadores en menos de 2 s (≈ 352.716 particiones por recorrer, con una lista acotada de 5).
- **Tamaños de archivo:** `MatchSetupPage.tsx` ya tiene 390 líneas (tope 400; componente ≤ 300): por eso E5-T2 extrae `LineupBuilder` y `TeamColumn`.

Full project rules: `CLAUDE.md`. Area rules: `.claude/rules/backend-java.md`, `.claude/rules/frontend.md`, `.claude/rules/testing.md`.

---

## Tasks

Listed in the same order as `tasks.json`. That order is the build order — work top to bottom and do not re-rank by priority or by what looks quick.

### `E5-T1` — Balance by level sum with top-5 alternatives

**Depends on:** `E4-T8`, `E3-T6` · **Priority:** p0

El balanceador mide la fuerza por suma de nivel y devuelve las 5 mejores particiones distintas; `balance` las expone como `options`.

- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` (editar) — `List<Result> balance(List<Candidate>)`: recorre todas las particiones con el menor id en A y `|A| ∈ {⌊n/2⌋, ⌈n/2⌉}` (Gosper, sin recursión) y conserva una lista ordenada de las 5 mejores por (diferencia de suma de nivel en décimas, diferencia de suma de ELO, ids de A lexicográficamente menor). `Result(teamA, teamB, totalLevelA, totalLevelB, levelDifference, avgEloA, avgEloB, eloDifference)`: los totales y la diferencia de nivel son `décimas ÷ 10` (una sola división, 8,5 contra 4,8 da exactamente 3.7); los ELO son promedios. La validación (2 a 22 jugadores, `levelTenths` 10–100) no cambia. Sigue puro: sin `org.springframework` ni `jakarta.persistence`.
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` (editar) — el oráculo de fuerza bruta pasa a sumas enteras en décimas y devuelve la lista ordenada de las 5 mejores tuplas (nivel, ELO); el test la compara posición por posición con `balance`; casos de 2, 3 y 4 jugadores (1, 3 y 3 alternativas), de unicidad, de orden no decreciente, de tamaños que difieren a lo sumo en 1 con el menor id en A, de determinismo con la entrada mezclada, de una décima de diferencia, del rango 9/101/10/100 y de 22 jugadores en menos de 2 s.
- `src/main/java/com/futbolmanager/match/MatchController.java` (editar) — `BalanceResponse(List<BalanceOption> options)` y `BalanceOption(teamA, teamB, totalLevelA, totalLevelB, levelDifference, avgEloA, avgEloB, eloDifference)` con `BalancedPlayer` igual que hoy; se eliminan `avgLevelA` y `avgLevelB`. `balance` sigue siendo solo ADMIN, sin cuerpo, sin escribir filas, y conserva los 400 por menos de 2 o más de 22 confirmados.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` (editar) — el contrato nuevo (`$.options` de 1 a 5, totales, sin `avgLevel*`), el caso decimal de punta a punta con `levelDifference` 3.7 en `$.options[0]`, el 403 del MEMBER, sin `level` en detalle ni lineup, y los 400.
- `src/main/java/com/futbolmanager/config/DemoDataSeeder.java` (editar) — `seedMatch` usa `balance(candidates).get(0)`; el resto de la siembra no cambia.

**Files**
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` — edit
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` — edit
- `src/main/java/com/futbolmanager/match/MatchController.java` — edit
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` — edit
- `src/main/java/com/futbolmanager/config/DemoDataSeeder.java` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** `TeamBalancerTest` compara contra el oráculo de fuerza bruta (n ≤ 10) **THE SYSTEM SHALL** devolver las 5 mejores particiones distintas, ordenadas por menor diferencia de suma de nivel y, entre las empatadas, por menor diferencia de suma de ELO, con las mismas tuplas (nivel, ELO) que el oráculo.
2. **WHEN** se balancean 2, 3 y 4 jugadores **THE SYSTEM SHALL** devolver 1, 3 y 3 alternativas respectivamente, y **WHEN** hay más de 5 particiones posibles **THE SYSTEM SHALL** devolver exactamente 5, todas distintas, con el menor id en el equipo A y tamaños que difieren en 1 como máximo.
3. **WHEN** dos particiones difieren en una sola décima de nivel **THE SYSTEM SHALL** elegir la de menor diferencia sin errores de coma flotante, y **WHEN** un candidato trae `levelTenths` 9 o 101 **THE SYSTEM SHALL** lanzar `IllegalArgumentException`, y **WHEN** trae 10 o 100 **THE SYSTEM SHALL** aceptarlo.
4. **WHEN** se balancea dos veces la misma entrada, en cualquier orden, **THE SYSTEM SHALL** devolver exactamente las mismas alternativas en el mismo orden, y **WHEN** se balancean 22 jugadores **THE SYSTEM SHALL** terminar en menos de 2 segundos.
5. **WHEN** un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` sin cuerpo **THE SYSTEM SHALL** responder `options` con entre 1 y 5 elementos, cada uno con `teamA`, `teamB` (jugadores con `level` y `position`), `totalLevelA`, `totalLevelB`, `levelDifference`, `avgEloA`, `avgEloB` y `eloDifference`, sin `avgLevelA` ni `avgLevelB`, con `levelDifference` 3.7 para dos jugadores de nivel 8.5 y 4.8, y **WHEN** un `MEMBER` pide `balance` **THE SYSTEM SHALL** responder 403.
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
git add -A && git commit -m "E5-T1: balance by level sum with top-5 alternatives"
git tag step-29-balance-sum-top5
```

### `E5-T2` — Show level totals, player counts and size warning

**Depends on:** `E5-T1`, `E4-T6` · **Priority:** p0

El armado muestra por equipo la cantidad de jugadores y el nivel total, la diferencia de nivel es la diferencia de totales y un aviso salta si la diferencia de cantidad supera 1.

- `frontend/src/features/matches/api.ts` (editar) — `BalanceOption` (`teamA`, `teamB`, `totalLevelA`, `totalLevelB`, `levelDifference`, `avgEloA`, `avgEloB`, `eloDifference`) y `BalanceResult = { options: BalanceOption[] }`; `useBalance` sigue siendo una mutación sin argumentos que hace `POST …/balance` sin cuerpo.
- `frontend/src/features/matches/LineupBuilder.tsx` (nuevo) — el `LineupBuilder` extraído de `MatchSetupPage.tsx` (mantiene cada archivo ≤ 400 líneas y cada componente ≤ 300). Al armar usa `options[0]`. Totales y diferencias se recalculan en el cliente sobre los equipos en pantalla (así se actualizan al mover jugadores): nivel total en décimas enteras (`Math.round(level * 10)`), diferencia de nivel = `|totalA − totalB|`, y el ELO como promedio por equipo con la diferencia de promedios. Si `|nA − nB| > 1` muestra `Alert variant="error"` con `Los equipos tienen {nA} y {nB} jugadores: la diferencia es mayor a 1.`; no bloquea guardar.
- `frontend/src/features/matches/TeamColumn.tsx` (nuevo) — el `TeamColumn` extraído: por equipo `{n} jugadores`, `Nivel total: N` (un decimal y coma, `font-mono tabular-nums`) y, como dato secundario, `ELO promedio`; se conservan `Pasar al equipo A/B` y el select `Puesto de {nombre}`. Un equipo guardado sin nivel disponible no muestra totales.
- `frontend/src/features/matches/MatchSetupPage.tsx` (editar) — queda con la página, la convocatoria y `SavedTeams`; importa `LineupBuilder`.
- `frontend/src/features/matches/MatchSetupPage.test.tsx` (editar) — el mock `BALANCE` pasa a `options`; «Nivel promedio» pasa a «Nivel total» con cantidad de jugadores; casos del aviso (aparece con 3 contra 1 al mover jugadores y desaparece al volver a 2 contra 2); caso de MEMBER sin niveles.

**Files**
- `frontend/src/features/matches/api.ts` — edit
- `frontend/src/features/matches/LineupBuilder.tsx` — new
- `frontend/src/features/matches/TeamColumn.tsx` — new
- `frontend/src/features/matches/MatchSetupPage.tsx` — edit
- `frontend/src/features/matches/MatchSetupPage.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** se activa `Armar equipos` **THE SYSTEM SHALL** hacer `POST …/balance` sin cuerpo y mostrar la primera de las `options` con, por equipo, la cantidad de jugadores y `Nivel total: N` con un decimal y coma, y debajo `Diferencia de nivel: N` como diferencia de los totales.
2. **WHEN** se renderiza el armado de equipos **THE SYSTEM SHALL** no mostrar ningún `Nivel promedio` y **SHALL** mostrar el ELO como promedio por equipo y `Diferencia de ELO` entre promedios, como dato secundario.
3. **WHEN** el admin mueve jugadores con `Pasar al equipo A` o `Pasar al equipo B` **THE SYSTEM SHALL** actualizar cantidad, nivel total y diferencias, y **WHEN** la diferencia de cantidad entre equipos pasa de 1 **THE SYSTEM SHALL** mostrar un aviso con `role="alert"` que indica la cantidad de jugadores de cada equipo, y **WHEN** vuelve a 1 o menos **THE SYSTEM SHALL** quitarlo.
4. **WHEN** se activa con el teclado `Pasar al equipo B` **THE SYSTEM SHALL** mover al jugador sin arrastrar, y cada select de puesto **SHALL** conservar el nombre accesible `Puesto de {nombre}`.
5. **WHEN** un `MEMBER` abre el detalle del partido **THE SYSTEM SHALL** mostrar la convocatoria y los equipos guardados sin controles de armado y sin ningún valor de nivel.
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
git add -A && git commit -m "E5-T2: show level totals, player counts and size warning"
git tag step-30-team-totals-warning
```

### `E5-T3` — Add Otra opción to cycle balance alternatives

**Depends on:** `E5-T2` · **Priority:** p0

Un botón «Otra opción» recorre en orden las alternativas que ya devolvió `balance`, y vuelve a la primera al final.

- `frontend/src/features/matches/LineupBuilder.tsx` (editar) — guarda `options` e `index`. «Armar equipos» pide las opciones y pone `index = 0`; «Otra opción» pasa a `(index + 1) % options.length` sin llamar al servidor y reemplaza los equipos en pantalla (descarta los movimientos manuales no guardados); un `role="status"` dice `Opción {k} de {N}`; con una sola opción el botón queda deshabilitado. Es un botón normal (alcanzable con Tab, activable con Enter y Espacio), sin arrastrar.
- `frontend/src/features/matches/LineupBuilder.test.tsx` (nuevo) — los casos de «Otra opción»; reutiliza el mismo patrón de `fetch` mockeado de `MatchSetupPage.test.tsx` (si hace falta compartir helpers se copian acá, no se importan de otro test).

**Files**
- `frontend/src/features/matches/LineupBuilder.tsx` — edit
- `frontend/src/features/matches/LineupBuilder.test.tsx` — new

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** `balance` devolvió más de una opción **THE SYSTEM SHALL** mostrar el botón `Otra opción` y `Opción 1 de N` en un `role="status"`, y **WHEN** se activa `Otra opción` N−1 veces **THE SYSTEM SHALL** mostrar en orden las opciones 2 a N, y **WHEN** se activa una vez más **THE SYSTEM SHALL** volver a la primera, sin nuevas peticiones a `balance`.
2. **WHEN** `balance` devolvió una sola opción **THE SYSTEM SHALL** mostrar `Otra opción` deshabilitado.
3. **WHEN** el admin cambia de opción **THE SYSTEM SHALL** reemplazar los equipos en pantalla y descartar los movimientos manuales no guardados, y **WHEN** vuelve a activar `Armar equipos` **THE SYSTEM SHALL** pedir las opciones de nuevo y mostrar la primera.
4. **WHEN** se activa `Otra opción` con el teclado **THE SYSTEM SHALL** cambiar de opción igual que con el puntero.
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
git add -A && git commit -m "E5-T3: add otra opcion to cycle balance alternatives"
git tag step-31-balance-alternatives
```

## Epic acceptance

- [ ] `./mvnw -q verify` y los cinco comandos de frontend salen con 0.
- [ ] `git tag -l 'step-29-*' 'step-30-*' 'step-31-*'` lista los tres tags de este epic (`step-29-balance-sum-top5` … `step-31-balance-alternatives`).
- [ ] No se agregó ninguna migración ni dependencia nueva.

## Pitfalls

- Entre el commit de E5-T1 y el de E5-T2 el backend ya devuelve `options` y el frontend todavía espera el contrato viejo: no desplegar a Render en el medio.
- No comparar niveles con `double`: el balanceador trabaja en décimas enteras (`long`); el decimal solo existe en la respuesta y en la pantalla.
- No mostrar la suma de ELO: la pantalla muestra el promedio por equipo; la suma es solo el desempate interno.
- No dejar `level` en ninguna respuesta ni pantalla de un MEMBER.
- Un componente ≤ 300 líneas y un archivo ≤ 400: `MatchSetupPage.tsx` ya está en 390 antes de empezar.

## Before moving on

Con las tres tareas hechas el proyecto vuelve a estar completo: nada más depende de este epic.
