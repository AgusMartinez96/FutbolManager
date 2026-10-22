# Epic 01: Fundaciones y cuentas

> Al terminar existe un jar Spring Boot 4 con el esquema completo aplicado, un SPA React servido desde ese jar, una imagen Docker de producción que arranca, CI, y cuentas con sesión, CSRF y throttling, con sus pantallas de login y registro.

| | |
|---|---|
| **Epic id** | `01-fundaciones-y-cuentas` |
| **Tasks** | `E1-T1` … `E1-T6` |
| **Depends on** | nothing — start here (después de correr el Bootstrap de `blueprint.md` §10) |
| **Unlocks** | `02-grupos-jugadores-y-motores`, `03-en-vivo-elo-y-produccion` |
| **Parallel with** | ninguno (02 y 03 dependen de tareas de este epic) |

You do not need any other file to complete this epic. Everything below is repeated here on purpose.

---

## Stack

Spring Boot 4.1 (Spring MVC, Security 7, Data JPA/Hibernate 7, Session JDBC, Flyway) · Java 21 · React 19 + TypeScript 6 + Vite 8 · Tailwind CSS 4 · PostgreSQL 17 · sesión de servidor (cookie `FM_SESSION`) · Docker → Render Free + Neon.
Package managers: Maven Wrapper (`./mvnw`, Maven 3.9.16) y npm (`frontend/package-lock.json`). Runtime de Node fijado en `.nvmrc` (24). Las versiones están en `pom.xml` y en el lockfile — leelas, nunca adivines una.

Todos los comandos corren en **Git Bash** desde la raíz del proyecto (la carpeta que contiene `blueprints/futbol-manager/`).

| Task | Command |
|---|---|
| DB local | `docker compose up -d --wait db` / `docker compose down` (reset: `docker compose down -v`) |
| Backend: todo | `./mvnw -q verify` (requiere Docker: Testcontainers) |
| Backend: una clase | `./mvnw -q test -Dtest=<Clase>` |
| Package jar | `./mvnw -q -DskipTests package` → `target/futbol-manager.jar` |
| Smoke del jar | `bash scripts/smoke.sh` (pares opcionales `PATH TEXTO`) |
| Frontend | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` |
| Imagen | `docker build -t futbol-manager:local .` · `bash scripts/docker-smoke.sh` |

**Gate:** `./mvnw -q verify` (backend) y `npm --prefix frontend run format:check && npm --prefix frontend run lint && npm --prefix frontend run typecheck && npm --prefix frontend run test && npm --prefix frontend run build` (frontend) pasan antes de marcar cualquier tarea como hecha.

La base local (`docker-compose.yml`, `postgres:17` en el puerto de host **5433**) ya está en la raíz: la emitió el bundle y la levantó Bootstrap. Los tests de integración no la usan: levantan su propio `postgres:17` con Testcontainers.

## Directory subtree

```
pom.xml                                  # exists (workspace) — read-only
.env / .env.example                      # exist — DB_URL, DB_USERNAME, DB_PASSWORD, DB_NAME, DEMO_PASSWORD
scripts/smoke.sh, scripts/docker-smoke.sh # exist (workspace) — read-only
.dockerignore, .gitignore, .nvmrc        # exist — read-only
Dockerfile                               # NEW (E1-T3)
render.yaml                              # NEW (E1-T3)
.github/workflows/ci.yml                 # NEW (E1-T3)
src/main/java/com/futbolmanager/
  FutbolManagerApplication.java          # NEW (E1-T1)
  config/SecurityConfig.java             # NEW (E1-T1)
  config/SpaWebConfig.java               # NEW (E1-T2)
  common/HealthController.java           # NEW (E1-T1)
  common/ApiException.java               # NEW (E1-T1)
  common/ApiExceptionHandler.java        # NEW (E1-T4)
  auth/User.java, auth/AuthUser.java     # NEW (E1-T4)
  auth/AuthController.java               # NEW (E1-T4), edited (E1-T5)
  auth/AuthRateLimiter.java              # NEW (E1-T5)
src/main/resources/
  application.yml                        # exists (workspace) — read-only
  application-prod.yml                   # NEW (E1-T3)
  db/migration/V1__spring_session.sql    # exists — never edit
  db/migration/V2__core_schema.sql       # exists — never edit
src/test/java/com/futbolmanager/
  TestcontainersConfiguration.java       # exists (workspace) — @Import it in every integration test
  support/ApiTestClient.java             # exists (workspace) — MockMvc client with CSRF + FM_SESSION
  common/HealthIntegrationTest.java      # NEW (E1-T1)
  auth/AuthIntegrationTest.java          # NEW (E1-T4)
  auth/AuthRateLimiterTest.java          # NEW (E1-T5)
  auth/AuthThrottleIntegrationTest.java  # NEW (E1-T5)
frontend/
  package.json, vite.config.ts, tsconfig*.json, eslint.config.js, .prettierrc.json, index.html # exist — read-only
  playwright.config.ts, e2e/health.spec.ts # exist — read-only; first run by E1-T2 (`test:e2e`)
  public/theme-init.js                   # exists — sets data-theme before paint (key `fm-theme`)
  src/styles/tokens.css, src/styles/index.css, src/test/setup.ts # exist — read-only
  src/main.tsx                           # NEW (E1-T2)
  src/app/router.tsx                     # NEW (E1-T2), edited (E1-T6)
  src/app/AppShell.tsx                   # NEW (E1-T2)
  src/app/AppShell.test.tsx              # NEW (E1-T2)
  src/components/ui.tsx                  # NEW (E1-T3)
  src/lib/api.ts                         # NEW (E1-T6)
  src/features/auth/session.tsx          # NEW (E1-T6)
  src/features/auth/AuthPages.tsx        # NEW (E1-T6)
  src/features/auth/AuthPages.test.tsx   # NEW (E1-T6)
```

Everything outside this subtree is out of scope. If a task seems to require editing a file not listed here, stop and report — it means the epic boundary is wrong.

## Data model touched here

El esquema completo ya existe (`V2__core_schema.sql`, aplicado por Flyway al arrancar). Este epic solo mapea `users`.

| Entity | Fields this epic adds or reads | Notes |
|---|---|---|
| `users` | `id uuid`, `username text` (`^[A-Za-z0-9_.]{3,30}$`, único por `lower(username)`), `display_name text` (1–60), `password_hash text`, `created_at`, `updated_at` | `updated_at` lo mantiene un trigger: no mapear `created_at`/`updated_at` como escribibles |
| `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` | — | las maneja Spring Session JDBC (`initialize-schema: never`) |
| las demás 8 tablas | — | existen; las usan los epics 02 y 03 |

## Contracts

**Consumed** — already exists, do not rebuild:

| From | Interface | Guarantee |
|---|---|---|
| `workspace` | `application.yml` | `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` sin default; `.env` cargado por `spring.config.import: optional:file:.env[.properties]`; cookie `FM_SESSION` HttpOnly, SameSite=Lax, 30 días; `server.port: ${PORT:8080}`; `fm.version: ${RENDER_GIT_COMMIT:dev}`; `fm.auth.register-per-ip-per-hour: 10`; `fm.security.content-security-policy: ""` |
| `workspace` | `TestcontainersConfiguration` | `@Bean @ServiceConnection PostgreSQLContainer` (`postgres:17`) |
| `workspace` | `ApiTestClient` | `register`, `login`, `get`, `post`, `postWithoutCsrf`, `put`, `patch`, `delete`, `fromIp`, `sessionCookie`, `useSession`, `uniqueUsername`, `status`, `body`, `json(result, "$.path")` |
| `workspace` | `pom.xml` Surefire | durante los tests `DB_*` son placeholders (los reemplaza Testcontainers) y `fm.auth.register-per-ip-per-hour=100000` |

**Produced** — later epics depend on exactly these signatures. Changing one breaks them:

| Export | Signature | Used by |
|---|---|---|
| `common/ApiException` | `ApiException.notFound(String)`, `forbidden(String)`, `conflict(String)`, `unprocessable(String)`, `badRequest(String)`, `tooManyRequests(String, long seconds)` | 02, 03 |
| `common/ApiExceptionHandler` | `ApiException` → ProblemDetail (`detail` español, `Retry-After`); validación → 400 + `errors: [{field, message}]` | 02, 03 |
| `auth/AuthUser` | `record AuthUser(UUID id, String username, String displayName) implements Serializable` — principal vía `@AuthenticationPrincipal` | 02, 03 |
| `auth/User` | entidad + `User.Repo extends JpaRepository<User, UUID>` | 03 (seed demo) |
| `FutbolManagerApplication` | `@EnableJpaRepositories(considerNestedRepositories = true)`; `@Bean Clock`; listener de arranque que falla con `Falta la variable de entorno obligatoria <NOMBRE>` | todos |
| `frontend/src/lib/api.ts` | `apiFetch<T>(path: string, init?: {method?: string; body?: unknown}): Promise<T>`; `class ApiError {status; detail; errors?; retryAfterSeconds?}` | 02, 03 |
| `frontend/src/features/auth/session.tsx` | `useMe()`, `RequireAuth`, `UserMenu` | 02, 03 |
| `frontend/src/components/ui.tsx` | `Button` (variants `primary`/`secondary`/`danger`/`ghost`, sizes `md`/`live`), `Field`, `Card`, `Alert` | 02, 03 |
| `frontend/src/app/router.tsx` | `router`, `createAppRouter(initialEntries?)` | 02, 03 (los editan para agregar rutas) |
| `frontend/src/app/AppShell.tsx` | `AppShell({actions?: ReactNode})` con `<main id="main">` y `<Outlet />` | 02, 03 |

## Conventions that bite in this area

- **Boot 4 nombres:** `spring-boot-starter-webmvc` (no `-web`), `@MockitoBean` (no `@MockBean`), Jackson 3 (`tools.jackson.*`). Si un import de test no está donde esperás (p. ej. `AutoConfigureMockMvc`), buscá la clase en el artefacto `spring-boot-starter-webmvc-test` pineado; no cambies versiones.
- **Repositorios anidados:** `User.Repo` dentro de `User`. Funciona solo por `considerNestedRepositories = true` en `FutbolManagerApplication`.
- **IDs:** `@Id @GeneratedValue(strategy = GenerationType.UUID)`.
- **CSRF modo SPA:** `http.csrf(c -> c.spa())`; cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`; login/registro/logout también lo exigen.
- **Health:** sin repositorios, sin `HttpSession`, sin base.
- **Throttling (E1-T5), reglas exactas:** clave `lower(username)|ip`; si pasaron > 15 min desde el último fallo, el contador vuelve a 0; al 5.º fallo, bloqueo `30 s × 2^(fallos − 5)` con tope 900 s (5.º → 30 s, 6.º → 60 s, 7.º → 120 s); mientras está bloqueada, los intentos devuelven 429 y no cuentan ni verifican la contraseña; por IP, 50 fallos en ventana deslizante de 15 min → 429 para esa IP; éxito resetea la clave; registro: 10 por IP por hora (ventana deslizante). `Retry-After` = segundos restantes redondeados hacia arriba. Mensajes: `Demasiados intentos fallidos. Probá de nuevo en N segundos.` / `Demasiados registros desde esta conexión. Probá de nuevo en N segundos.`
- **Mensajes de auth:** 401 `Usuario o contraseña incorrectos.` · 409 `Ese nombre de usuario ya está en uso.` · validación 400 `Hay datos inválidos.` · 401 genérico `Tenés que iniciar sesión.` · 403 genérico `No tenés permiso para hacer esto.`
- **Frontend:** imports relativos sin extensión, sin alias; HTTP solo por `apiFetch`; sin colores hex (ESLint los rechaza); `react-router` (nunca `react-router-dom`); copy en español rioplatense.
- **Windows:** comandos en Git Bash; `scripts/*.sh` necesitan bash.

Full project rules: `CLAUDE.md`. Area rules: `.claude/rules/backend-java.md`, `.claude/rules/frontend.md`, `.claude/rules/testing.md`. Both sit in the project root — the builder copied them there from the bundle's `workspace/` before task one.

---

## Tasks

Listed in the same order as `tasks.json`. That order is the build order — work top to bottom and do not re-rank by priority or by what looks quick.

### `E1-T1` — Build backend skeleton with health and schema

**Depends on:** nothing · **Priority:** p0 — metadata for scope cuts, not a running order

Crear la aplicación con `@EnableJpaRepositories(considerNestedRepositories = true)` y un `@Bean Clock` (`Clock.systemUTC()`). `main` hace `new SpringApplication(FutbolManagerApplication.class)` y, antes de `run(args)`, le agrega un `ApplicationListener<ApplicationEnvironmentPreparedEvent>` (corre después de que `spring.config.import` cargó `.env`) que, para `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` en ese orden, lanza `IllegalStateException("Falta la variable de entorno obligatoria " + nombre)` si `getProperty(nombre)` es nulo o en blanco — lo exige el gate de E1-T3; los tests no pasan por `main`. `SecurityConfig`: `csrf(c -> c.spa())`, `permitAll` para `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/register`, `authenticated()` para el resto de `/api/**`, `permitAll` para todo lo demás; sin `formLogin`/`httpBasic`/`logout`; entry point 401 y access denied 403 como `application/problem+json`; headers frame DENY, nosniff, `strict-origin-when-cross-origin`; beans `BCryptPasswordEncoder` y `HttpSessionSecurityContextRepository` (también en `http.securityContext`). `HealthController` devuelve `record Health(String status, String version)` con `@Value("${fm.version}")`. `ApiException` lleva `HttpStatus`, `detail` y `retryAfterSeconds` opcional (su handler llega en E1-T4). El test usa `ApiTestClient` + `JdbcClient` (`select to_regclass('public.<tabla>') is not null`).

**Files**
- `src/main/java/com/futbolmanager/FutbolManagerApplication.java` — new
- `src/main/java/com/futbolmanager/config/SecurityConfig.java` — new
- `src/main/java/com/futbolmanager/common/HealthController.java` — new
- `src/main/java/com/futbolmanager/common/ApiException.java` — new
- `src/test/java/com/futbolmanager/common/HealthIntegrationTest.java` — new

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `./mvnw -q test -Dtest=HealthIntegrationTest` **THE SYSTEM SHALL** levantar un `postgres:17` con Testcontainers, aplicar `V1__spring_session.sql` y `V2__core_schema.sql` y comprobar con `to_regclass` que existen `spring_session`, `spring_session_attributes`, `users`, `groups`, `memberships`, `players`, `matches`, `match_call_ups`, `match_lineup_entries`, `match_events` y `elo_changes`.
2. **WHEN** se pide `GET /api/health` sin sesión **THE SYSTEM SHALL** responder 200 con `status` = `UP` y `version` = `dev` y **SHALL NOT** emitir la cookie `FM_SESSION`.
3. **WHEN** una petición sin sesión llega a `GET /api/groups` **THE SYSTEM SHALL** responder 401 con content type `application/problem+json`.
4. **WHEN** corre `bash scripts/smoke.sh` **THE SYSTEM SHALL** arrancar `target/futbol-manager.jar` contra la base de compose (puerto de host 5433, credenciales de `.env`) y salir con 0 después de que `GET /api/health` responda 200 con `"status":"UP"`.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
./mvnw -q test -Dtest=HealthIntegrationTest
./mvnw -q verify
bash scripts/smoke.sh
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T1: build backend skeleton with health and schema"
git tag step-01-backend-skeleton
```

Run both after the last `Verify` command exits 0, before starting the next task. The tag is this task's rollback target and the thing the build's final gate counts — if the next task goes wrong, `git reset --hard` to *this* tag rather than debugging forward.

### `E1-T2` — Serve the frontend shell from the jar

**Depends on:** `E1-T1` · **Priority:** p0

`main.tsx` importa `./styles/index.css` y monta `QueryClientProvider` (queries `retry: 1`) + `RouterProvider`. `router.tsx` exporta `router` (`createBrowserRouter`: `/` → `Navigate` a `/grupos`; `/grupos` → página provisoria con `<h1>Mis grupos</h1>` dentro de `AppShell`; `*` → "Página no encontrada") y `createAppRouter(initialEntries?)` con `createMemoryRouter` para tests. `AppShell`: skip link "Saltar al contenido" → `#main`, header con "Fútbol Manager" (no es `h1`), prop `actions`, `ThemeToggle` (alterna `data-theme` y guarda `fm-theme`), `<main id="main"><Outlet /></main>`, `max-w-240`. El test renderiza `AppShell` dentro de un `createMemoryRouter` propio (ruta hija índice con `<h1>Mis grupos</h1>`), así no depende de `router.tsx` ni de la auth de E1-T6. `SpaWebConfig`: `/**` → `classpath:/static/` con un `PathResourceResolver` que devuelve el recurso si existe, si no `index.html` para rutas sin `.` que no empiezan con `api/` ni `v3/`, y `null` en otro caso.

**Files**
- `frontend/src/main.tsx` — new
- `frontend/src/app/router.tsx` — new
- `frontend/src/app/AppShell.tsx` — new
- `frontend/src/app/AppShell.test.tsx` — new
- `src/main/java/com/futbolmanager/config/SpaWebConfig.java` — new

**Acceptance**

1. **WHEN** corre `npm --prefix frontend run test` **THE SYSTEM SHALL** pasar `src/app/AppShell.test.tsx`, que verifica un skip link a `#main`, exactamente un `h1`, un landmark `main` con id `main` y un botón de tema que alterna `document.documentElement.dataset.theme` entre `dark` y `light` y guarda la elección en `localStorage` bajo la clave `fm-theme`.
2. **WHEN** corren `format:check`, `lint` y `typecheck` sobre `frontend/` **THE SYSTEM SHALL** salir con 0 sin warnings.
3. **WHEN** corre `npm --prefix frontend run build` **THE SYSTEM SHALL** escribir `index.html`, `theme-init.js` y los assets con hash en `src/main/resources/static/`.
4. **WHEN** el jar empaquetado recibe `GET /` y el deep link `GET /grupos` **THE SYSTEM SHALL** responder 200 con el `index.html` del SPA que contiene `<div id="root"></div>`.
5. **WHEN** corre `npm --prefix frontend run test:e2e` **THE SYSTEM SHALL** arrancar el jar con el `webServer` de `frontend/playwright.config.ts` (cwd raíz, `.env`) y pasar `e2e/health.spec.ts` (emitido): `GET /api/health` → 200 con `status` = `UP`.

**Verify**

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
test -f src/main/resources/static/theme-init.js
./mvnw -q -DskipTests package
bash scripts/smoke.sh / '<div id="root"></div>' /grupos '<div id="root"></div>'
npm --prefix frontend run e2e:install
npm --prefix frontend run test:e2e
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T2: serve the frontend shell from the jar"
git tag step-02-frontend-shell
```

### `E1-T3` — Add Docker image, CI, prod profile and UI primitives

**Depends on:** `E1-T2` · **Priority:** p0

`Dockerfile` en tres etapas: `node:24-alpine` (`/app/frontend`: `npm ci`, `npm run build` → `/app/src/main/resources/static`), `eclipse-temurin:21-jdk` (`/app`: copia `.mvn/`, `mvnw`, `pom.xml`, `src/` y el `static/` de la etapa 1; `chmod +x mvnw`; `./mvnw -q -DskipTests package`), `eclipse-temurin:21-jre` (usuario de sistema `app`, `/app/app.jar`, `USER app`, `EXPOSE 8080`, `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]`). `ci.yml`: job `backend` con `run: chmod +x mvnw && ./mvnw -B verify`; job `frontend` en `frontend/` con `npm ci`, `format:check`, `lint`, `typecheck`, `test`, `build` (Node desde `.nvmrc`). `application-prod.yml`: cookie `secure: true`, `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false`, `fm.security.content-security-policy: "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'"`, `logging.structured.format.console: ecs`. `render.yaml`: web `futbol-manager`, `runtime: docker`, `plan: free`, `region: virginia`, `dockerfilePath: ./Dockerfile`, `dockerContext: .`, `healthCheckPath: /api/health`, `autoDeploy: true`, env `SPRING_PROFILES_ACTIVE=prod` y `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` con `sync: false`. `ui.tsx`: `Button` (cva; `primary` = `bg-primary text-on-primary`, `secondary` = `border border-border-strong`, `danger` = `bg-danger-bg text-on-danger`, `ghost`; tamaños `md` `min-h-10`, `live` `min-h-16 w-full text-xl`), `Field` (label + input + error con `aria-describedby`/`aria-invalid`), `Card`, `Alert` (`role="alert"` o `role="status"`).

**Files**
- `Dockerfile` — new
- `.github/workflows/ci.yml` — new
- `src/main/resources/application-prod.yml` — new
- `render.yaml` — new
- `frontend/src/components/ui.tsx` — new

**Acceptance**

1. **WHEN** corre `docker build -t futbol-manager:local .` **THE SYSTEM SHALL** compilar el frontend en `node:24-alpine`, empaquetar el jar en `eclipse-temurin:21-jdk` y producir una imagen `eclipse-temurin:21-jre` que corre como usuario no-root.
2. **WHEN** corre `bash scripts/docker-smoke.sh` **THE SYSTEM SHALL** correr la imagen con `SPRING_PROFILES_ACTIVE=prod` contra la base de compose y comprobar 200 con `"status":"UP"` en `/api/health`, un uid distinto de 0 y 404 en `/v3/api-docs` y `/swagger-ui.html`.
3. **WHEN** el jar arranca con `DB_URL` ausente del entorno y sin `.env` en el directorio de trabajo **THE SYSTEM SHALL** terminar con código 1 y escribir el error `Falta la variable de entorno obligatoria DB_URL` (lo lanza el listener de `FutbolManagerApplication`, paso 1).
4. **WHEN** se leen `.github/workflows/ci.yml` y `render.yaml` **THE SYSTEM SHALL** contener un job que corre `./mvnw -B verify` y un servicio web Docker con `healthCheckPath: /api/health`.
5. **WHEN** corren `format:check`, `lint` y `typecheck` del frontend con `src/components/ui.tsx` presente **THE SYSTEM SHALL** salir con 0.

**Verify**

```bash
./mvnw -q verify
status=0; (cd target && env -u DB_URL DB_USERNAME=x DB_PASSWORD=x java -jar futbol-manager.jar > missing-env.log 2>&1) || status=$?; test "$status" -eq 1
grep -qF "Falta la variable de entorno obligatoria DB_URL" target/missing-env.log
grep -q './mvnw -B verify' .github/workflows/ci.yml
grep -q 'healthCheckPath: /api/health' render.yaml
test -f frontend/src/components/ui.tsx
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
docker build -t futbol-manager:local .
bash scripts/docker-smoke.sh
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T3: add docker image, ci, prod profile and ui primitives"
git tag step-03-docker-ci
```

### `E1-T4` — Add accounts API with session and CSRF

**Depends on:** `E1-T1` · **Priority:** p0

`User` (entidad `users`, `User.Repo` con `findByUsernameIgnoreCase` vía `lower(...)`), `AuthUser` (record `Serializable`, va en la sesión). `AuthController` en `/api/auth`: `GET /csrf` (`CsrfToken` → `{headerName, token}`), `POST /register` (record con `@Size(min=3,max=30)` + `@Pattern("^[A-Za-z0-9_.]+$")` username, `@Size(max=60)` displayName, `@Size(min=8,max=72)` password; 409 si existe sin importar mayúsculas — también ante `DataIntegrityViolationException`; BCrypt; inicia sesión; 201), `POST /login` (si el usuario no existe, igual corre un `matches` contra un hash fijo; 401; en éxito `changeSessionId()` si había sesión y guarda un `UsernamePasswordAuthenticationToken(authUser, null, List.of())` con el `SecurityContextRepository`), `POST /logout` (invalida → 204), `GET /me`. `ApiExceptionHandler` (extiende `ResponseEntityExceptionHandler`): `ApiException` → ProblemDetail + `Retry-After`; `MethodArgumentNotValidException` → 400 `Hay datos inválidos.` + `errors`; `HttpMessageNotReadableException` → 400; `DataIntegrityViolationException` → 409; resto → 500 `Ocurrió un error inesperado.` con log ERROR.

**Files**
- `src/main/java/com/futbolmanager/auth/User.java` — new
- `src/main/java/com/futbolmanager/auth/AuthUser.java` — new
- `src/main/java/com/futbolmanager/auth/AuthController.java` — new
- `src/main/java/com/futbolmanager/common/ApiExceptionHandler.java` — new
- `src/test/java/com/futbolmanager/auth/AuthIntegrationTest.java` — new

**Acceptance**

1. **WHEN** `POST /api/auth/register` recibe `username`, `displayName` y `password` válidos con token CSRF **THE SYSTEM SHALL** responder 201 con `{id, username, displayName}`, guardar un hash BCrypt que empieza con `$2` en vez de la contraseña y emitir una cookie `FM_SESSION` con la que `GET /api/auth/me` responde 200.
2. **WHEN** `POST /api/auth/register` repite un username existente cambiando solo mayúsculas **THE SYSTEM SHALL** responder 409 con un ProblemDetail cuyo `detail` es `Ese nombre de usuario ya está en uso.` y no crear filas.
3. **WHEN** `POST /api/auth/register` recibe un username de 2 caracteres y una contraseña de 7 **THE SYSTEM SHALL** responder 400 con un arreglo `errors` cuyos `field` incluyen `username` y `password`.
4. **WHEN** `POST /api/auth/login` recibe una contraseña incorrecta **THE SYSTEM SHALL** responder 401 con `detail` `Usuario o contraseña incorrectos.`, y **WHEN** recibe la correcta **THE SYSTEM SHALL** responder 200 y una sesión con la que `GET /api/auth/me` devuelve ese usuario.
5. **WHEN** llega `POST /api/auth/login` sin token CSRF **THE SYSTEM SHALL** responder 403.
6. **WHEN** corre `POST /api/auth/logout` **THE SYSTEM SHALL** responder 204 y la cookie de sesión anterior **SHALL** recibir 401 en `GET /api/auth/me`.

**Verify**

```bash
./mvnw -q test -Dtest=AuthIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T4: add accounts api with session and csrf"
git tag step-04-auth-api
```

### `E1-T5` — Throttle login and registration attempts

**Depends on:** `E1-T4` · **Priority:** p1

`AuthRateLimiter` (`@Component`, `Clock` inyectado, `@Value("${fm.auth.register-per-ip-per-hour}")`) implementa las reglas exactas de *Conventions* con `ConcurrentHashMap` y actualizaciones atómicas (`compute`). API: `Optional<Duration> loginBlockedFor(username, ip)`, `recordLoginFailure`, `recordLoginSuccess`, `Optional<Duration> registerBlockedFor(ip)` (registra el intento si no está bloqueado). `AuthController` lo consulta antes de verificar la contraseña y antes de registrar; IP = `request.getRemoteAddr()`. El test unitario usa un `Clock` falso mutable; el de integración usa `@SpringBootTest(properties = "fm.auth.register-per-ip-per-hour=10")` y `ApiTestClient.fromIp(...)`.

**Files**
- `src/main/java/com/futbolmanager/auth/AuthRateLimiter.java` — new
- `src/main/java/com/futbolmanager/auth/AuthController.java` — edit: consulta y registra en el limiter
- `src/test/java/com/futbolmanager/auth/AuthRateLimiterTest.java` — new
- `src/test/java/com/futbolmanager/auth/AuthThrottleIntegrationTest.java` — new

**Acceptance**

1. **WHEN** corre `AuthRateLimiterTest` con un `Clock` falso **THE SYSTEM SHALL** comprobar que 4 fallos consecutivos de una clave (usuario, IP) no bloquean, que el 5.º bloquea 30 s, el 6.º 60 s y el 7.º 120 s, y que el bloqueo nunca supera 900 s.
2. **WHEN** una clave (usuario, IP) está bloqueada **THE SYSTEM SHALL** responder 429 a `POST /api/auth/login` con un header `Retry-After` en segundos enteros sin verificar la contraseña.
3. **WHEN** una IP acumula 50 logins fallidos en 15 minutos con usuarios distintos **THE SYSTEM SHALL** responder 429 a cualquier login desde esa IP hasta que el fallo más viejo salga de la ventana.
4. **WHEN** un login es correcto **THE SYSTEM SHALL** resetear a cero los fallos de esa clave (usuario, IP).
5. **WHEN** el usuario `u` está bloqueado desde la IP `10.0.0.1` **THE SYSTEM SHALL** igualmente responder 200 a la contraseña correcta de `u` desde la IP `10.0.0.2`.
6. **WHEN** una IP envía su registro número 11 dentro de una hora con `fm.auth.register-per-ip-per-hour=10` **THE SYSTEM SHALL** responder 429 con `Retry-After`.

**Verify**

```bash
./mvnw -q test -Dtest=AuthRateLimiterTest
./mvnw -q test -Dtest=AuthThrottleIntegrationTest
./mvnw -q verify
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T5: throttle login and registration attempts"
git tag step-05-login-throttling
```

### `E1-T6` — Build accessible login and register screens

**Depends on:** `E1-T3`, `E1-T4` · **Priority:** p0

`apiFetch` (`credentials: 'same-origin'`, JSON; en no-GET lee la cookie `XSRF-TOKEN` — si falta, `GET /api/auth/csrf` primero — y manda `X-XSRF-TOKEN`; no-2xx → `ApiError` desde el ProblemDetail y `Retry-After`; 204 → `undefined`). `session.tsx`: `useMe()` (query `['me']`, 401 → `null`), `RequireAuth` (esqueleto; sin sesión → `/ingresar?next=` + `encodeURIComponent(pathname + search)`; con sesión → `<AppShell actions={<UserMenu />} />`), `UserMenu` (nombre + "Salir": logout, `queryClient.clear()`, `/ingresar`). `AuthPages.tsx`: `LoginPage` (`h1` "Ingresar") y `RegisterPage` (`h1` "Crear cuenta") con `Field`/`Button`/`Alert`, `autocomplete` `username`/`current-password`/`new-password`, validación al enviar en español, foco al primer inválido, resumen `role="alert"`, errores del servidor por campo, redirección a `next` (solo rutas que empiezan con `/`) o `/grupos`. Router: `/ingresar` y `/registro` públicas; `/` y `/grupos` dentro de `RequireAuth`. Tests con `vi.stubGlobal('fetch', …)`.

**Files**
- `frontend/src/lib/api.ts` — new
- `frontend/src/features/auth/session.tsx` — new
- `frontend/src/features/auth/AuthPages.tsx` — new
- `frontend/src/app/router.tsx` — edit: rutas públicas y `RequireAuth`
- `frontend/src/features/auth/AuthPages.test.tsx` — new

**Acceptance**

1. **WHEN** `AuthPages.test.tsx` envía el formulario de login con el usuario vacío **THE SYSTEM SHALL** mostrar un error en español junto al campo, poner `aria-invalid="true"`, enlazarlo con `aria-describedby`, mover el foco al primer campo inválido y escribir un resumen en una región `role="alert"`.
2. **WHEN** la API simulada responde 401 a `POST /api/auth/login` **THE SYSTEM SHALL** mostrar `Usuario o contraseña incorrectos.` y conservar el usuario tipeado.
3. **WHEN** la API simulada responde 429 con `Retry-After: 30` **THE SYSTEM SHALL** mostrar un mensaje que contiene `30 segundos`.
4. **WHEN** `apiFetch` envía una petición no-GET **THE SYSTEM SHALL** mandar el header `X-XSRF-TOKEN` igual a la cookie `XSRF-TOKEN`, llamando antes a `GET /api/auth/csrf` si la cookie no existe.
5. **WHEN** un usuario sin sesión abre `/grupos` **THE SYSTEM SHALL** redirigir a `/ingresar?next=%2Fgrupos`.
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
git add -A && git commit -m "E1-T6: build accessible login and register screens"
git tag step-06-auth-ui
```

---

## Epic acceptance

The epic is done when every task is `done` **and**:

1. **WHEN** el jar empaquetado con el frontend arranca contra la base de compose **THE SYSTEM SHALL** responder 200 en `/api/health`, `/` y `/grupos`.
2. **WHEN** la imagen corre con el perfil `prod` **THE SYSTEM SHALL** cerrar Swagger con 404 y correr como usuario no-root.

```bash
./mvnw -q verify
npm --prefix frontend run build
./mvnw -q -DskipTests package
bash scripts/smoke.sh / '<div id="root"></div>' /grupos '<div id="root"></div>'
docker build -t futbol-manager:local .
bash scripts/docker-smoke.sh
```

## Pitfalls

- **Correr un `*.sh` desde CMD/PowerShell** — falla o abre otra shell; los gates son Git Bash.
- **Mapear `created_at`/`updated_at` como escribibles** — pisás el trigger; no los mapees (o `insertable = false, updatable = false`).
- **`AuthUser` no serializable** — Spring Session JDBC serializa el `SecurityContext`; sin `Serializable` el login explota al guardar la sesión.
- **Tests que comparten usernames** — usá `ApiTestClient.uniqueUsername(...)`; el contexto de Spring es compartido entre clases.
- **`AppShell.test.tsx` acoplado al router** — si renderiza `router.tsx`, se rompe cuando E1-T6 agrega `RequireAuth`; renderizalo con su propio memory router.
- **Copiar `workspace/` de nuevo** — nunca; Bootstrap ya lo hizo con un guard por marcador.

## Before moving on

- [ ] Every task in this epic is `done` in `tasks.json` — no task left `in_progress`.
- [ ] Every `verify` command of every task in this epic passed, not just the first one.
- [ ] No `verify` command was edited, and none was skipped because a file it names did not exist.
- [ ] **Every task in this epic has its `checkpoint` tag in version control** — `git tag -l 'step-*'` lists `step-01-backend-skeleton` … `step-06-auth-ui`.
- [ ] Gate command passes clean, run from the project root.
- [ ] Every "Produced" contract above exists with the stated signature.
- [ ] No file outside the subtree was modified.
- [ ] `.env.example` updated if this epic added a variable — this epic adds none.
- [ ] One commit per task, each prefixed with its task id, each followed by its checkpoint tag.
