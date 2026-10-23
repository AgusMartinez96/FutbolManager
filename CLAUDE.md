# Fútbol Manager

App web para organizar partidos de fútbol amateur entre amigos: grupos, ranking ELO, estadísticas, armado automático de equipos y marcador en vivo mobile-first.

## Commands

Todos los comandos se corren desde la raíz del proyecto, en **Git Bash** (el shell del Bash tool de Claude Code en Windows).

| Task | Git Bash (gates) | CMD / PowerShell (humano) |
|---|---|---|
| DB local up / down / reset | `docker compose up -d --wait db` · `docker compose down` · `docker compose down -v` | igual |
| Backend dev | `./mvnw spring-boot:run` — http://localhost:8080 | `mvnw.cmd spring-boot:run` / `.\mvnw.cmd spring-boot:run` |
| Backend tests (todo) | `./mvnw -q verify` (requiere Docker: Testcontainers) | `mvnw.cmd -q verify` / `.\mvnw.cmd -q verify` |
| Backend test (una clase) | `./mvnw -q test -Dtest=AuthIntegrationTest` | `.\mvnw.cmd -q test "-Dtest=AuthIntegrationTest"` (PowerShell) |
| Package jar | `./mvnw -q -DskipTests package` → `target/futbol-manager.jar` | `.\mvnw.cmd -q -DskipTests package` |
| Frontend install | `npm --prefix frontend install` | igual |
| Frontend dev | `npm --prefix frontend run dev` — http://localhost:5173 (proxy `/api` → :8080) | igual |
| Format / check | `npm --prefix frontend run format` · `npm --prefix frontend run format:check` | igual |
| Lint | `npm --prefix frontend run lint` | igual |
| Typecheck | `npm --prefix frontend run typecheck` | igual |
| Frontend tests | `npm --prefix frontend run test` · un archivo: `npm --prefix frontend run test -- src/app/AppShell.test.tsx` | igual |
| Frontend build | `npm --prefix frontend run build` → `src/main/resources/static/` | igual |
| Smoke del jar | `bash scripts/smoke.sh` (acepta pares opcionales `PATH TEXTO`) | solo Git Bash |
| Imagen Docker | `docker build -t futbol-manager:local .` · `bash scripts/docker-smoke.sh` | smoke solo Git Bash |
| E2E | `npm --prefix frontend run e2e:install` (una vez) · `npm --prefix frontend run test:e2e` | igual |

**Gate de backend:** `./mvnw -q verify` en verde. **Gate de frontend:** `format:check`, `lint`, `typecheck`, `test` y `build` en verde. Ninguna tarea se marca hecha con un gate en rojo.

Node 24 LTS (`.nvmrc`); Node 25 instala con un warning `EBADENGINE` — usá Node 24 (nvm-windows o fnm). JDK 21. Las versiones de dependencias están en `pom.xml` y `frontend/package-lock.json`: leelas, nunca adivines una.

## Stack

Java 21 · Spring Boot 4.1 (WebMVC, Security, Data JPA, Session JDBC, Flyway) · PostgreSQL 17 · React 19 + TypeScript + Vite · Tailwind CSS 4 · TanStack Query · React Router 7 · Testcontainers · Vitest · Playwright · Docker → Render Free + Neon.

## Architecture

**Request path.** Navegador → `frontend/src/features/<f>/api.ts` (TanStack Query) → `src/lib/api.ts` `apiFetch` (CSRF + ProblemDetail) → `/api/**` → Spring Security (sesión `FM_SESSION` en `SPRING_SESSION`) → `com.futbolmanager.<feature>.<Feature>Controller` → `GroupAccess.requireMember/requireAdmin` → `Entity.Repo` (Spring Data JPA) o `JdbcClient` → PostgreSQL. En prod, el SPA compilado vive en `classpath:/static/` del mismo jar; `config/SpaWebConfig` devuelve `index.html` para rutas sin punto que no son `/api/**` ni `/v3/**`.

**Boundaries.**

| Layer | May import from | Must never |
|---|---|---|
| `elo/EloCalculator`, `match/balance/TeamBalancer` | JDK only | `org.springframework.*`, `jakarta.persistence.*` |
| `<feature>/*Controller` | its entities, `common`, `group.GroupAccess`, pure algorithms | another feature's controller |
| `<feature>/<Entity>` (+ nested `Repo`) | JPA, `common` | controllers |
| `frontend/src/features/**` | `src/lib`, `src/components`, own feature | another feature's internals (except exported pages) |
| `frontend/src/components/**` | `src/lib` | `features/**` |

**Where things live.**

| Concern | Single source of truth |
|---|---|
| DB schema | `src/main/resources/db/migration/V*.sql` (Flyway; V1/V2 nunca se editan) |
| Config / env | `src/main/resources/application.yml` (+ `application-prod.yml`); `.env` local vía `spring.config.import` |
| Errores HTTP | `common/ApiException` + `common/ApiExceptionHandler` (RFC 9457, `detail` en español) |
| Usuario actual | `@AuthenticationPrincipal AuthUser user` (`auth/AuthUser`) |
| Tenancy | `group/GroupAccess` — 404 si no sos miembro, 403 si te falta rol |
| ELO | `elo/EloCalculator` (puro) + `elo/EloReplayService` (único escritor de TEAM_RESULT/BONUS) |
| Design tokens | `frontend/src/styles/tokens.css` (mapeados en `index.css` `@theme`) |
| Cliente HTTP | `frontend/src/lib/api.ts` |

## Code rules

1. Repositorios Spring Data como interfaces anidadas en la entidad (`User.Repo`); depende de `@EnableJpaRepositories(considerNestedRepositories = true)` en `FutbolManagerApplication`.
2. DTOs como `record` anidados en el controller. Nunca devolver entidades JPA.
3. Toda query de datos de un grupo recibe `groupId`; todo endpoint `/api/groups/{groupId}/**` arranca con `GroupAccess`.
4. `elo_changes` solo por `JdbcClient`. Cerrar, editar o anular un partido = `EloReplayService.replayFrom(groupId, matchId)`.
5. Frontend: imports relativos sin extensión, sin alias ni barrels; datos de servidor solo vía TanStack Query; HTTP solo vía `apiFetch`.
6. Sin hex ni colores arbitrarios fuera de `tokens.css` (ESLint lo rechaza). Íconos solo `lucide-react`.
7. Un componente ≤ 300 líneas; un archivo ≤ 400.
8. Ninguna dependencia nueva sin motivo en el commit y fila en blueprint §11.

## Design system

| Role | Dark (default) | Light | Used for |
|---|---|---|---|
| Background | `#0b1210` | `#f7faf8` | página |
| Surface / raised | `#111a17` / `#18241f` | `#ffffff` / `#eef4f0` | cards, paneles |
| Border / strong | `#24332d` / `#5b6f65` | `#d4e0d9` / `#7a8c82` | divisores / inputs y controles |
| Text / muted | `#e6efe9` / `#9db0a6` | `#0f1a14` / `#4b5d53` | texto |
| Primary + on-primary | `#15803d` + `#ffffff` | `#15803d` + `#ffffff` | botones primarios |
| Accent | `#4ade80` | `#15803d` | links, texto destacado, foco |
| MVP | `#f59e0b` (texto) | `#b45309` (texto) · badge `#f59e0b` + `#1a1203` | MVP |
| Danger | `#f87171` · bg `#b91c1c` + `#ffffff` | `#b91c1c` · bg `#b91c1c` + `#ffffff` | errores, anular |

- **Type:** Inter Variable (UI) 400/600/700; JetBrains Mono Variable para marcadores y ELO (`tabular-nums`). Body 16px/1.6.
- **Scale:** 12 / 14 / 16 / 20 / 24 / 30 / 48 px.
- **Spacing:** base 4px — 4, 8, 12, 16, 24, 32, 48, 64. Sin valores arbitrarios.
- **Radius:** 8px botones e inputs, 12px cards, full avatares y badges.
- **Elevation:** plana — bordes, sin sombras salvo diálogos (`0 8px 24px rgb(0 0 0 / 0.35)`).
- **Motion:** 150ms `ease-out`, solo transform y opacity; respetar `prefers-reduced-motion`.
- **Layout:** mobile-first, verificado a 375px; max ancho de contenido 960px; breakpoints sm 640 / md 768 / lg 1024.
- **Targets:** ≥ 24px siempre; botones de modo en vivo ≥ 64px (`min-h-16`). Tema: `data-theme` en `<html>`, clave `fm-theme` en localStorage, aplicado antes del paint por `public/theme-init.js`.

## Environment

| Variable | Required | Used by | Source |
|---|---|---|---|
| `DB_URL` | yes | `application.yml` | local: `.env` · prod: Neon (`jdbc:postgresql://<host>/<db>?sslmode=require`) |
| `DB_USERNAME` / `DB_PASSWORD` | yes | `application.yml` | local: `.env` · prod: Neon |
| `DB_NAME` | local only | `docker-compose.yml`, `scripts/docker-smoke.sh` | `.env` |
| `SPRING_PROFILES_ACTIVE` | prod: `prod` | Spring | Render env |
| `PORT` | no (8080) | `server.port` | Render lo inyecta |
| `RENDER_GIT_COMMIT` | no (`dev`) | `/api/health` `version` | Render lo inyecta |
| `DEMO_PASSWORD` | solo perfil `demo` | `config/DemoDataSeeder` | `.env` / Render |

`.env.example` se commitea y se mantiene sincronizado; `.env` nunca. Los tests no usan `.env` (Testcontainers + valores de Surefire en `pom.xml`).

## Rules

| File | Applies to |
|---|---|
| `.claude/rules/backend-java.md` | `src/main/java/**`, `src/test/java/**` |
| `.claude/rules/database.md` | migraciones, `elo/**`, Java |
| `.claude/rules/frontend.md` | `frontend/src/**`, `frontend/e2e/**` |
| `.claude/rules/testing.md` | `src/test/**`, tests de frontend, `frontend/e2e/**` |

Build order: `blueprints/futbol-manager/tasks.json` + `blueprints/futbol-manager/epics/`.

## Non-negotiable

1. Otro grupo = 404, nunca 403. Toda lectura de datos de grupo filtra por `groupId`.
2. Nunca editar `V1__spring_session.sql` ni `V2__core_schema.sql`; los cambios van en una migración nueva.
3. El ELO solo se escribe vía `EloReplayService` (cerrar = replay); los `INITIAL` nunca se recalculan.
4. Sin contraseñas por defecto: `DB_*` sin default en `application.yml`; secretos solo en `.env` local o en Render.
5. Nunca commitear `.env`, `target/`, `src/main/resources/static/` ni `node_modules/`.
6. No implementar nada de la tabla de Non-Goals (blueprint §1): fotos, votación de MVP, reclamo de perfil, atributos, gráficos, amigos, mail, OAuth, WebSockets.
7. Nunca marcar una tarea como hecha con un gate en rojo, ni editar un comando `verify`.

## Convenciones del dueño

- **Comandos para Windows.** Todo comando que se le muestre al dueño lleva su versión Windows (`.\mvnw.cmd` en vez de `./mvnw`), indicando la carpeta (raíz del proyecto, salvo que se diga otra) y la terminal (CMD/PowerShell, o Git Bash para `scripts/*.sh`).
- **Una tarea por vez.** Se trabaja una tarea de `tasks.json`; al terminarla se verifica con su acceptance gate, se hace commit y se frena hasta que el dueño diga que siga.
- **Cerrar con `--done`.** Al marcar una tarea `done` con `--done`, siempre se commitea el cambio de `tasks.json` (mensaje `E<n>-T<n>: mark task done`) y se crea el tag `step-XX` de su `checkpoint`, sin preguntar.
- **Orden al cerrar una tarea.** Primero se commitea el código (`E<n>-T<n>: <resumen>`, solo los archivos de la tarea) y después se corre `--done`; el orden no se pregunta.
- **Sin coautoría.** Los commits no llevan línea de coautoría (ni `Co-Authored-By` ni "Generated with Claude Code").
- **Decisiones inviables.** Si una decisión del blueprint resulta inviable, no se cambia en silencio: se explica el problema y se propone la alternativa. Si se aprueba, se actualiza primero el blueprint y después el código, en el mismo commit.
- **Retomar.** En una conversación nueva el punto de partida es `/the-architect:architect-next`.
