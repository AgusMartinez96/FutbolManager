# Fútbol Manager — Blueprint

> Generado por The Architect el 2026-10-21
> Shape: SaaS web app (multi-tenant, tenant = grupo) · `knowledge/shapes/saas-webapp.md`
> Runtime track: **desvío** — Java 21 + Spring Boot 4 (backend) / React + TypeScript + Vite (frontend). No existe un track Java en la base de conocimiento; ver §2.
> Emission mode: bundle
> Blueprint version: 1
> Versions last verified: 2026-10-21 — ver §11 para la procedencia de cada paquete

**Cómo se usa este bundle.** El builder trabaja desde la raíz del proyecto (`D:/Proyectos Java VSC/futbol_manager`, donde vive este bundle en `blueprints/futbol-manager/`). Primero corre el bloque *Bootstrap* de §10 (copia `workspace/` a la raíz con una guarda re-ejecutable, inicializa git, genera el Maven wrapper, instala el frontend y hace el primer commit). Después ejecuta `tasks.json` en orden, abriendo solo el epic de cada tarea. Todos los comandos `Verify` corren en **Git Bash** desde la raíz.

---

## 1. Project Overview & Non-Goals

### Vision

Fútbol Manager es una app web para grupos de amigos que juegan al fútbol amateur (5, 7, 8 u 11 por lado) todas las semanas. Un grupo (5–40 jugadores) arma la convocatoria del partido, deja que la app proponga dos equipos parejos según el nivel (1,0–10,0, con un decimal) que el admin asigna a cada jugador (el ELO desempata), registra el partido en vivo desde un celular al costado de la cancha —goles, asistencias, atajadas, cortes y jugadones con botones grandes— y al cerrarlo actualiza un ranking ELO con libro contable auditable y estadísticas por jugador.

El problema que resuelve es concreto: los equipos se arman "a ojo" y siempre quedan desparejos, nadie se acuerda de quién hizo los goles, y el "quién es mejor" se discute sin datos. Es además un proyecto de portfolio: se despliega gratis (Render Free + Neon) y tiene que verse y comportarse como un producto real (seguridad de sesiones, aislamiento entre grupos, accesibilidad AA, tests de integración contra PostgreSQL real).

### Users

| Persona | What they come to do | Frequency |
|---|---|---|
| Organizador (ADMIN del grupo) | Crear el grupo, invitar, cargar jugadores invitados, crear el partido, armar equipos, cerrar el partido y elegir MVP | Semanal |
| Jugador (MEMBER) | Confirmar asistencia, mirar ranking y sus estadísticas, cargar eventos en vivo si el grupo lo permite | Semanal |
| Anotador de cancha (ADMIN o MEMBER habilitado) | Registrar goles/asistencias/atajadas/cortes/jugadones desde el celular durante el partido | Durante cada partido |
| Reclutador que mira el portfolio | Entrar a la demo, navegar el grupo "Los del jueves" | Una vez |

### Goals — v1 scope

1. Registro, login y logout con usuario y contraseña, sesión persistente de 30 días y throttling de login sin bloqueo de cuentas.
2. Grupos con código/link de invitación, roles ADMIN/MEMBER, gestión de miembros y aislamiento total entre grupos.
3. Jugadores por grupo: invitados creados por el admin y jugadores vinculados a un usuario (auto-creados al unirse), con ELO inicial congelado y un nivel de 1,0 a 10,0 (con un decimal) que solo ven los admins.
4. Partidos con convocatoria, armado automático de equipos parejos por nivel, con el ELO como desempate (búsqueda exhaustiva hasta 22 jugadores), ajuste manual y puesto por partido solo informativo (los arqueros rotan).
5. Modo en vivo mobile-first: gol/asistencia/atajada/corte/jugadón, deshacer, anular, idempotencia por UUID de cliente, polling cada 5 s.
6. Cierre con MVP elegido por el admin, ELO con libro contable (`elo_changes`) y recálculo determinístico ante correcciones o anulaciones.
7. Ranking del grupo y perfil de jugador con estadísticas e historial de ELO en tabla.
8. Despliegue en Render (Docker) + Neon (Postgres 17), Swagger cerrado en producción.

### Non-Goals — explicitly out of scope for v1

| Not building | Why not now | Revisit when |
|---|---|---|
| Reclamo de perfil invitado con código (un usuario "toma" un jugador invitado existente) | Requiere flujo de verificación y fusión de historial; v1 crea un jugador nuevo al unirse | v1 está en producción y un grupo real pide fusionar un invitado con su cuenta — se diseña en un segundo blueprint vía `/architect-brownfield` |
| Votación de MVP con ventana de 48 h (fila `MVP_BONUS` separada) | Necesita jobs programados y estado de votación; v1 usa MVP elegido por el admin | Hay ≥ 3 grupos activos y alguno se queja del MVP elegido a dedo |
| Puntuación de atributos entre compañeros (pase, técnica, definición, velocidad, físico, defensa) + radar. **No incluye el «nivel» único de 1,0 a 10,0 (con un decimal) que asigna el admin: ese sí está en v1 (§4)** | Modelo de datos y anti-abuso propios; no afecta al ELO | v1 en producción y el balanceo por nivel + ELO solo resulta insuficiente |
| Gráfico de historial de ELO (Recharts) | v1 expone el endpoint y una tabla; el gráfico es cosmético | El endpoint `elo-history` está estable en producción |
| Fotos de perfil y de grupo (en Postgres `bytea`, ≤ 2 MB, redimensionadas a 256×256 JPEG ~25 KB) | Pipeline de imágenes y storage; decisión ya tomada: Postgres, no Cloudinary | Fase B, tras medir el uso de almacenamiento de Neon |
| Amigos (solicitudes) y convocar usuarios de otros grupos | Rompe el modelo "tenant = grupo" y su aislamiento | Fase B, con un diseño explícito de visibilidad entre grupos |
| Verificación de mail, recuperación de contraseña por mail, OAuth | No hay proveedor de mail en v1; usuario+contraseña alcanza para grupos de amigos | Un usuario real pierde el acceso a su cuenta |
| WebSockets, push, PWA offline, i18n, pagos, multi-instancia | Polling de 5 s cubre el modo en vivo; instancia única gratis; producto en español | Más de una instancia o latencia de polling inaceptable medida en uso real |

**The builder must not implement anything in this table**, even if it seems like a small addition while working on an adjacent step. If a step appears to require a non-goal, that is a blueprint defect — stop and report it rather than expanding scope.

### Success metrics

| Metric | Target | How measured |
|---|---|---|
| Diferencia de ELO promedio entre equipos propuestos | ≤ 25 puntos en el 90 % de los partidos al 2026-12-31 | `select percentile_cont(0.9) …` sobre la `eloDifference` que devuelve `/balance`, registrada en el log del endpoint |
| Partidos cerrados por grupo activo | ≥ 3 por mes al 2026-12-31 | `select group_id, count(*) from matches where status = 'CLOSED' and closed_at > now() - interval '30 days' group by 1` |
| Primera carga en frío de la demo | < 60 s incluyendo el wake de Render y Neon | `curl -w '%{time_total}'` contra `/api/health` tras 20 min de inactividad (launch checklist §20.1) |
| Violaciones axe serias o críticas | 0 en todas las rutas cubiertas | Spec de Playwright del paso 28 |

---

## 2. Tech Stack

**Runtime track: desvío — Java 21 + Spring Boot 4 / React 19 + TypeScript + Vite.** No existe `knowledge/runtime-tracks/java*.md`; el usuario fijó el stack como restricción. Por eso no hay pins de respaldo de un track: **todas** las versiones vienen del informe de `stack-researcher` de esta sesión (2026-10-21). Esta tabla nombra *elecciones*; cada versión vive solo en §11.

| Layer | Choice | Why this, over what |
|---|---|---|
| Language / runtime | Java 21 (backend) · TypeScript 6.0 sobre Node 24 LTS (frontend) | Restricción del usuario (Java). Java 21 es LTS y Boot 4.1 lo soporta; TypeScript 6.0 en vez de 7.x porque `typescript-eslint` exige `< 6.1.0`. Node 24 LTS en vez del 25 instalado localmente porque 25 no es LTS. |
| Framework | Spring Boot 4.1 (Spring MVC, no WebFlux) · React 19 + Vite 8 como SPA | Boot 4.1 tiene soporte OSS hasta 2027-07-31 (3.5 ya terminó, 4.0 termina 2026-12-31). MVC bloqueante alcanza para decenas de grupos y es más simple que reactivo. SPA en vez de SSR: app 100 % detrás de login, sin SEO (desvío permitido por `frontend-architecture.md`). |
| Styling | Tailwind CSS 4 con tokens como CSS custom properties | Default de `styling.md`; los tokens en `tokens.css` permiten tema claro/oscuro sin tocar componentes. |
| Component layer | Componentes copy-in estilo shadcn sobre `radix-ui` + `class-variance-authority` + `tailwind-merge`; íconos `lucide-react` | Primitivas accesibles (foco, teclado, ARIA) sin depender de un kit con estilos propios. |
| Database | PostgreSQL 17 (Docker local, Neon en prod) | Relacional por las invariantes (membresías, libro contable); Neon gratis escala a cero. |
| ORM / data access | Spring Data JPA (Hibernate 7) para entidades + `JdbcClient` para el libro contable ELO y estadísticas; Flyway para el esquema | JPA para CRUD; SQL explícito donde el replay y los agregados necesitan control del orden y de las queries. Flyway es el único dueño del esquema (`ddl-auto: validate`). |
| Auth | Spring Security 7, usuario + contraseña BCrypt, sesión de servidor con Spring Session JDBC, CSRF modo SPA | Mismo origen → cookie de sesión, sin JWT ni rotación de refresh tokens; la sesión sobrevive reinicios/sleep de Render porque vive en Postgres y es revocable. Un solo dueño de identidad (`auth.md`). |
| Background work | Ninguno, salvo el `cleanup-cron` de Spring Session cada 6 h | Todo es request/response; el recálculo ELO es transaccional y corto (decenas de partidos). |
| Payments | NOT APPLICABLE — sin monetización en v1 (ver Non-Goals) | — |
| File storage | NOT APPLICABLE — las fotos son Fase B | — |
| Email / notifications | Ninguno | Sin proveedor de mail en v1 (Non-Goals). |
| Hosting | Render Web Service Free (Docker) + Neon Free | Gratis, Docker reproducible; Render Free da 750 h/mes ≥ 744 h de un mes de 31 días para un servicio siempre encendido. |
| Package manager | Maven 3.9.16 vía Maven Wrapper (`./mvnw`) · npm (con `package-lock.json`) | El wrapper fija Maven por proyecto; npm viene con Node y no requiere habilitar corepack en Windows. |

### Compatibility check

Checked against `knowledge/stack-compatibility.md` — none of the known-bad rows apply:

- *Linter que parsea CSS + Tailwind CSS-first:* no aplica. ESLint (con `@eslint/js` + `typescript-eslint`) no parsea CSS; Prettier formatea `@theme`/`@custom-variant` como at-rules genéricos.
- *In-memory state + host multi-instancia:* el throttling de login vive en memoria (`ConcurrentHashMap`), lo cual es correcto **solo** con una instancia. v1 corre una sola instancia en Render Free y "multi-instancia" está en Non-Goals; si eso cambia, el throttling pasa a una tabla o a Redis (riesgo 4, §20.2).
- *Conexiones serverless sin pool:* no aplica; el servidor es un proceso largo con Hikari (`maximum-pool-size: 5`, `minimum-idle: 0` para que Neon pueda escalar a cero).
- *Dos migradores:* Flyway es el único; el dashboard de Neon se usa solo para leer.
- *Dos proveedores de identidad:* uno solo (Spring Security + tabla `users`).

Restricciones de versiones cruzadas que este blueprint respeta (del informe de pins): ESLint queda en 9.x porque `eslint-plugin-jsx-a11y` 6.10.2 declara peer `eslint ≤ ^9`; TypeScript queda en 6.0.x por `typescript-eslint` 8.72.0 (`< 6.1.0`); `@vitejs/plugin-react` 6.x exige Vite 8; Flyway 12.4.0 lo maneja el BOM de Boot (no fijar 13.x a mano); en Boot 4 el starter web se llama `spring-boot-starter-webmvc` y Flyway necesita `spring-boot-starter-flyway`.

---

## 3. Directory Structure

Origen de cada archivo: **[W]** = emitido en `workspace/` y copiado a la raíz por Bootstrap (§10) · **[Sn]** = lo crea el paso n de §9 · **[B]** = generado por un comando de Bootstrap · **[G]** = generado por un comando de build (nunca a mano, nunca commiteado salvo que se indique).

```
futbol_manager/
  .claude/
    settings.json                      [W] permisos pre-aprobados para los gates (§19.3)
    settings.local.json                (del usuario; ignorado por git)
    rules/backend-java.md              [W] convenciones backend (§19.5)
    rules/database.md                  [W] migraciones y libro ELO
    rules/frontend.md                  [W] convenciones React/Tailwind/a11y
    rules/testing.md                   [W] convenciones de tests
    skills/add-migration/SKILL.md      [W] procedimiento para una migración nueva (§19.4)
    skills/add-group-endpoint/SKILL.md [W] procedimiento para un endpoint de grupo
  .github/workflows/ci.yml             [S3] CI: backend (Testcontainers) + frontend
  .mvn/wrapper/maven-wrapper.properties [B] Maven wrapper (commiteado)
  blueprints/futbol-manager/           este bundle (commiteado; excluido del contexto Docker)
  frontend/
    e2e/flows.spec.ts                  [S28] 3 recorridos Playwright + axe
    e2e/health.spec.ts                 [W] GET /api/health vía webServer (gate desde el paso 2)
    public/theme-init.js               [W] aplica el tema antes del primer paint (archivo externo por la CSP)
    src/
      app/AppShell.tsx                 [S2] layout: skip link, header, nav, main, toggle de tema
      app/AppShell.test.tsx            [S2]
      app/router.tsx                   [S2] única declaración de rutas (editado en S6, S9, S13, S15, S27)
      components/ui.tsx                [S3] primitivas copy-in: Button (cva), Field, Card, Alert (+ DialogShell en S25)
      features/auth/AuthPages.tsx      [S6] LoginPage + RegisterPage
      features/auth/AuthPages.test.tsx [S6]
      features/auth/session.tsx        [S6] useMe, RequireAuth, UserMenu (logout)
      features/groups/api.ts           [S9, S20] hooks TanStack Query de grupos, miembros y jugadores
      features/groups/GroupsPages.tsx  [S9] Mis grupos, crear, unirse (/unirse/:code)
      features/groups/GroupDetailPage.tsx [S9, S20] detalle: jugadores, miembros, ajustes
      features/groups/PlayersTab.tsx   [S20, S23] pestaña de jugadores (extraída de GroupDetailPage; nivel con un decimal en S23)
      features/groups/PlayerForm.tsx   [S20, S23] alta/edición de jugador («Nivel» con coma o punto en S23)
      features/groups/GroupsPages.test.tsx [S9, S20]
      features/matches/api.ts          [S13, S24] hooks de partidos, convocatoria, balance, lineup
      features/matches/MatchesPages.tsx [S13] lista + crear partido
      features/matches/MatchSetupPage.tsx [S13, S24] convocatoria + armado de equipos
      features/matches/MatchSetupPage.test.tsx [S13, S24]
      features/live/api.ts             [S15, S25] estado en vivo (polling 5 s), eventos (incl. jugadón), anular
      features/live/LivePage.tsx       [S15, S25] modo en vivo + resumen (editado en S27: cierre + MVP)
      features/live/EventDialog.tsx    [S25] diálogo de autor/asistencia (extraído de LivePage)
      features/live/LivePage.test.tsx  [S15, S25]
      features/stats/StatsPages.tsx    [S27] ranking + perfil de jugador
      lib/api.ts                       [S6] apiFetch: CSRF, ProblemDetail → ApiError
      main.tsx                         [S2] entry: QueryClientProvider + RouterProvider + estilos
      styles/index.css                 [W] Tailwind + fuentes + @theme (mapea tokens)
      styles/tokens.css                [W] tokens de color claro/oscuro (único archivo con hex)
      test/setup.ts                    [W] setup de Vitest (jest-dom, cleanup, matchMedia)
    .prettierignore                    [W]
    .prettierrc.json                   [W]
    eslint.config.js                   [W]
    index.html                         [W]
    package.json                       [W] manifiesto con versiones exactas
    package-lock.json                  [B] generado por `npm --prefix frontend install` (commiteado)
    playwright.config.ts               [W]
    tsconfig.json                      [W] referencias a app/node
    tsconfig.app.json                  [W]
    tsconfig.node.json                 [W]
    vite.config.ts                     [W] Vite + Vitest; build → ../src/main/resources/static
    node_modules/                      [B] ignorado
  scripts/
    smoke.sh                           [W] arranca el jar y verifica rutas HTTP
    docker-smoke.sh                    [W] corre la imagen con perfil prod y verifica
  src/
    main/
      java/com/futbolmanager/
        FutbolManagerApplication.java  [S1] @SpringBootApplication + repos anidados + bean Clock
        auth/AuthController.java       [S4] /api/auth/* (editado en S5)
        auth/AuthRateLimiter.java      [S5] throttling de login y registro (en memoria)
        auth/AuthUser.java             [S4] principal de sesión (record)
        auth/User.java                 [S4] entidad + User.Repo
        common/ApiException.java       [S1] excepción con status + detail en español
        common/ApiExceptionHandler.java [S4] ProblemDetail RFC 9457 + errores de validación
        common/HealthController.java   [S1] GET /api/health (sin DB, sin sesión)
        config/DemoDataSeeder.java     [S28] perfil `demo`: "Los del jueves"
        config/SecurityConfig.java     [S1] cadena de seguridad (editado en S28: headers prod)
        config/SpaWebConfig.java       [S2] fallback a index.html para rutas del SPA
        elo/EloCalculator.java         [S10, S16] matemática ELO pura (sin puestos)
        elo/EloReplayService.java      [S26] único escritor de TEAM_RESULT/BONUS
        group/FriendGroup.java         [S7] entidad `groups` + FriendGroup.Repo
        group/GroupAccess.java         [S7] guarda de tenancy (404/403)
        group/GroupController.java     [S7] grupos, invitación, miembros
        group/Membership.java          [S7] entidad + Membership.Repo
        live/LiveController.java       [S14] eventos en vivo (editado en S19 y S26)
        live/MatchEvent.java           [S14, S19] entidad + MatchEvent.Repo
        match/LineupEntry.java         [S12] entidad + LineupEntry.Repo
        match/Match.java               [S12] entidad + Match.Repo
        match/MatchCallUp.java         [S12] entidad + MatchCallUp.Repo
        match/MatchController.java     [S12, S18, S21] partidos, convocatoria, balance, lineup, start
        match/MatchLifecycleController.java [S26] cerrar, MVP, anular
        match/balance/TeamBalancer.java [S11, S18, S21] balanceo exhaustivo puro por nivel en décimas (ELO desempata)
        player/Player.java             [S8, S17, S21, S22] entidad + Player.Repo (nivel en décimas desde S22)
        player/PlayerController.java   [S8, S17, S22]
        player/PlayerService.java      [S8, S17, S22] alta con ELO inicial y nivel + alta automática al unirse (nivel 5,0)
        stats/StatsController.java     [S27] ranking, estadísticas, historial ELO
      resources/
        application.yml                [W] config base
        application-prod.yml           [S3] overrides de producción
        db/migration/V1__spring_session.sql [W]
        db/migration/V2__core_schema.sql    [W] esquema completo de §4
        db/migration/V3__player_level.sql   [S17] players.level 1–10 (existentes en 5; V5 lo reemplaza)
        db/migration/V4__match_event_play.sql [S19] tipo de evento PLAY (Jugadón)
        db/migration/V5__player_level_decimal.sql [S22] players.level_tenths 10–100 (nivel × 10; reemplaza players.level)
        static/                        [G] salida de `npm --prefix frontend run build` (ignorado)
    test/java/com/futbolmanager/
      TestcontainersConfiguration.java [W] postgres:17 + @ServiceConnection
      support/ApiTestClient.java       [W] cliente MockMvc con CSRF + cookie FM_SESSION
      common/HealthIntegrationTest.java [S1]
      auth/AuthIntegrationTest.java    [S4]
      auth/AuthRateLimiterTest.java    [S5]
      auth/AuthThrottleIntegrationTest.java [S5]
      group/GroupIntegrationTest.java  [S7]
      player/PlayerIntegrationTest.java [S8, S17]
      elo/EloCalculatorTest.java       [S10, S16]
      match/balance/TeamBalancerTest.java [S11, S18]
      match/MatchIntegrationTest.java  [S12, S17, S18]
      live/LiveIntegrationTest.java    [S14, S17, S19]
      elo/EloReplayIntegrationTest.java [S26]
      elo/EloReplayPropertyTest.java   [S26]
      stats/StatsIntegrationTest.java  [S27]
      config/ProdProfileIntegrationTest.java [S28]
  target/futbol-manager.jar            [G] `./mvnw … package` (ignorado)
  .dockerignore                        [W]
  .env                                 [B] copia local de .env.example (ignorado)
  .env.example                         [W] valores locales de desarrollo (commiteado)
  .gitattributes                       [W] LF en todo salvo *.cmd/*.bat
  .gitignore                           [W]
  .nvmrc                               [W] 24
  AGENTS.md                            [W]
  CLAUDE.md                            [W]
  Dockerfile                           [S3] multi-stage node → jdk → jre (usuario no-root)
  README.md                            [S28] runbook de deploy
  docker-compose.yml                   [W] postgres:17 en host 5433
  mvnw, mvnw.cmd                       [B] Maven wrapper (commiteados; mvnw con bit +x)
  pom.xml                              [W] proyecto Maven único
  render.yaml                          [S3] blueprint de Render
```

**Boundary rules**
- `elo/EloCalculator` y `match/balance/TeamBalancer` no importan nada de `org.springframework` ni `jakarta.persistence` (lo verifica un `grep` en los pasos 10, 11, 16 y 18).
- Un controller de una feature nunca importa el controller de otra; la comunicación entre features usa eventos (`GroupController.MemberJoined`) o servicios (`EloReplayService`, `GroupAccess`, `PlayerService`).
- `elo_changes` solo se lee/escribe con `JdbcClient` (no hay entidad JPA).
- En el frontend, `src/components/**` no importa de `features/**`; las páginas se importan solo desde `app/router.tsx`.
- Las convenciones de import (repositorios anidados en Java; specifiers relativos sin extensión ni alias en TS) están reconciliadas contra cada loader en §19.6, *Resolution convention matrix*.

La ruta de salida del build del frontend (`src/main/resources/static`) y la del jar (`target/futbol-manager.jar`) se toman literalmente de §19.6, *Cross-artifact value reconciliation*.

---

## 4. Data Model

Tenancy: **tenant = grupo**. Toda tabla con datos de un grupo lleva `group_id` directo (`memberships`, `players`, `matches`, `elo_changes`) o llega a él por `match_id → matches.group_id` (`match_call_ups`, `match_lineup_entries`, `match_events`). **Regla de acceso:** todo método de repositorio o query SQL sobre datos de grupo recibe `groupId` y lo usa en el `WHERE` (para tablas hijas de `matches`, el partido se carga primero con `findByIdAndGroupId`).

### Entities

**users** — una cuenta. Se crea en el registro; v1 no la borra.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` | |
| username | text | not null, `^[A-Za-z0-9_.]{3,30}$`, único por `lower(username)` | Se guarda como lo escribió el usuario; la unicidad ignora mayúsculas |
| display_name | text | not null, 1–60 chars | Nombre visible; nombre por defecto del jugador auto-creado |
| password_hash | text | not null | BCrypt (`$2a$…`/`$2b$…`); la contraseña en claro (8–72 chars) nunca se guarda |
| created_at / updated_at | timestamptz | not null, default now() | `updated_at` por trigger |

**groups** — un grupo de amigos (el tenant). Entidad Java `FriendGroup` (`Group` es palabra reservada de JPQL).

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK | |
| name | text | not null, 1–60 | |
| invite_code | text | not null, único, `^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$` | 8 chars sin ambiguos (sin I, L, O, 0, 1); regenerable por un admin |
| members_can_score | boolean | not null, default false | true → los MEMBER pueden cargar eventos en vivo |
| created_by_user_id | uuid | FK users, not null, ON DELETE RESTRICT | |
| created_at / updated_at | timestamptz | | |

**memberships** — pertenencia de un usuario a un grupo, con rol.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK | |
| group_id | uuid | FK groups ON DELETE CASCADE | |
| user_id | uuid | FK users ON DELETE CASCADE | |
| role | text | `ADMIN` \| `MEMBER` | El último ADMIN no puede ser degradado ni removido (409, en `GroupController`) |
| (group_id, user_id) | | unique | |

**players** — un jugador dentro de un grupo; invitado (`user_id` null) o vinculado a un usuario.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK | |
| group_id | uuid | FK groups ON DELETE CASCADE | |
| user_id | uuid | FK users ON DELETE SET NULL, nullable; único por grupo cuando no es null | |
| name | text | not null, 1–60 | |
| nickname | text | nullable, 1–30 | |
| preferred_position | text | `GK` \| `DEF` \| `MID` \| `FWD` | UI: ARQ / DEF / MED / DEL. **Solo informativo:** no afecta al ELO ni al balanceo (los arqueros rotan) |
| dominant_foot | text | `RIGHT` \| `LEFT` \| `BOTH` | |
| level_tenths | integer | not null, CHECK 10–100 (V5) | Nivel que asigna el admin, guardado en **décimas enteras** (85 = 8,5; 50 = 5,0): así el balanceo compara con `long` sin coma flotante. V3 creó `level` 1–10 y V5 lo convierte (`level_tenths = level * 10`) y lo elimina. La API lo expone como `level` decimal (`8.5`). **Solo lo ven los ADMIN del grupo** (la API lo omite para un MEMBER). El balanceo minimiza la diferencia de nivel promedio; el ELO solo desempata y alimenta el ranking. Los jugadores existentes quedan con el mismo valor (5 pasa a 5,0) y los creados al crear el grupo o al unirse nacen en 5,0 (50) |
| current_elo | integer | not null | Caché del libro: INITIAL.elo_after + Σ delta de sus otras filas |
| deleted_at | timestamptz | nullable | Soft delete: sale del ranking y de los selectores, conserva historial |

**matches** — un partido de un grupo.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK | |
| group_id | uuid | FK groups ON DELETE CASCADE | |
| played_at | timestamptz | not null | Orden ELO = `(played_at, id)` |
| location | text | not null, 1–120 | |
| format | integer | 5 \| 7 \| 8 \| 11 | jugadores por lado (informativo; el lineup puede tener otro tamaño) |
| status | text | `SCHEDULED` → `LIVE` → `CLOSED`; `ANNULLED` desde `SCHEDULED` o `CLOSED` | |
| score_a / score_b | integer | nullable, ambos o ninguno | Caché fijada al cerrar = goles no anulados por equipo |
| mvp_player_id | uuid | FK players, nullable | Elegido por el admin al cerrar |
| closed_at / annulled_at | timestamptz | nullable | |
| version | bigint | not null default 0 | `@Version` (bloqueo optimista en ediciones concurrentes) |

**match_call_ups** — convocatoria por jugador. `status`: `CONFIRMED` \| `MAYBE` \| `NOT_GOING`; único `(match_id, player_id)`.

**match_lineup_entries** — equipo y puesto por partido. `team`: `A` \| `B`; `position`: `GK` \| `DEF` \| `MID` \| `FWD` (el puesto es por partido y **solo informativo**: no afecta al ELO ni al balanceo; el arquero rota); único `(match_id, player_id)`.

**match_events** — eventos en vivo, append-only.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK **generado por el cliente** | Clave de idempotencia |
| match_id | uuid | FK matches ON DELETE CASCADE | |
| type | text | `GOAL` \| `SAVE` \| `TACKLE` \| `PLAY` | `PLAY` = Jugadón (gran jugada), agregado en V4 |
| team | text | `A` \| `B` | Equipo al que suma el evento (gol en contra: equipo beneficiado, `player_id` null) |
| player_id | uuid | FK players; obligatorio salvo en GOAL | GOAL: autor (null = en contra/desconocido); SAVE, TACKLE y PLAY: cualquier jugador del lineup de ese equipo |
| assist_player_id | uuid | FK players; solo en GOAL con autor, distinto del autor | |
| recorded_by_user_id | uuid | FK users, not null | Quién lo cargó (se muestra en el resumen) |
| voided_at / voided_by_user_id | | ambos o ninguno | Anulación; un evento anulado no cuenta para marcador, ELO ni estadísticas |
| created_at | timestamptz | | Orden del resumen |

**elo_changes** — libro contable ELO (solo `JdbcClient`).

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | uuid | PK | |
| group_id | uuid | FK groups | |
| player_id | uuid | FK players | |
| match_id | uuid | FK matches, null **solo** si `reason = INITIAL` | |
| reason | text | `INITIAL` \| `TEAM_RESULT` \| `BONUS` \| `MVP_BONUS` | `MVP_BONUS` reservado para Fase B; v1 nunca lo escribe |
| elo_before / elo_after / delta | integer | `elo_after = elo_before + delta` | INITIAL: before = after = ELO inicial, delta 0 |
| details | jsonb | nullable | Solo en BONUS: `{"goals":n,"assists":n,"mvp":0|3,"cleanSheet":0|2,"saves":0..3,"tackles":0..3,"plays":0..2,"uncapped":n,"total":n}` (puntos, no cantidades; en ese orden) |
| único | | `(match_id, player_id, reason)` si `match_id` no es null; un solo INITIAL por jugador | |

**SPRING_SESSION / SPRING_SESSION_ATTRIBUTES** — sesiones de Spring Session JDBC (DDL oficial de spring-session-jdbc 4.1.1, `V1__spring_session.sql`).

### Relationships

- users —(1:N)→ memberships ←(N:1)— groups. Borrar usuario/grupo borra membresías (CASCADE).
- groups —(1:N)→ players, matches, elo_changes: CASCADE (borrar grupos no existe en v1; la cascada solo protege la integridad).
- users —(1:N)→ players (vinculado): ON DELETE SET NULL (el jugador queda como invitado con su historial).
- matches —(1:N)→ match_call_ups, match_lineup_entries, match_events: CASCADE.
- players ←(N:1)— match_call_ups, match_lineup_entries, match_events (`player_id`, `assist_player_id`), matches.mvp_player_id: **RESTRICT** — los jugadores nunca se borran físicamente (soft delete).
- players —(1:N)→ elo_changes: CASCADE (solo alcanzable vía borrado de grupo).
- users ←(N:1)— match_events.recorded_by_user_id / voided_by_user_id, groups.created_by_user_id: RESTRICT.

### Indexes

| Table | Index | Why |
|---|---|---|
| users | `uq_users_username_lower (lower(username))` | Unicidad sin mayúsculas + login por username |
| groups | `uq_groups_invite_code` | Unirse por código |
| memberships | `uq_memberships_group_id_user_id`, `idx_memberships_user_id` | Guarda de tenancy; "mis grupos" |
| players | `idx_players_group_id`, `idx_players_user_id`, `uq_players_group_id_user_id` (parcial), `idx_players_ranking (group_id, current_elo DESC) WHERE deleted_at IS NULL` | Lista, auto-alta al unirse, ranking |
| matches | `idx_matches_group_id_played_at_id`, `idx_matches_mvp_player_id` | Lista de partidos y orden del replay |
| match_call_ups / match_lineup_entries | unique `(match_id, player_id)` + `player_id` | Upsert de convocatoria y lineup; estadísticas por jugador |
| match_events | `(match_id, created_at)` + cada FK | Estado en vivo ordenado |
| elo_changes | `group_id`, `player_id`, `match_id`, unique `(match_id, player_id, reason)`, unique INITIAL por jugador | Replay, historial, idempotencia del libro |

### Schema

Es el contenido literal de `src/main/resources/db/migration/V2__core_schema.sql`, emitido en §19.6 (`workspace/`) y aplicado por Flyway desde el paso 1. Se reproduce acá para que el blueprint sea autocontenido; si alguna vez difieren, manda el archivo. Los cambios posteriores (`players.level`, tipo de evento `PLAY`) viven en `V3__player_level.sql` y `V4__match_event_play.sql`, descritos en *Migrations*; esta vista de V2 no se edita.

```sql
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

CREATE TABLE memberships (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id   uuid        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  role       text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_memberships_role CHECK (role IN ('ADMIN', 'MEMBER'))
);

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
  CONSTRAINT ck_players_preferred_position CHECK (preferred_position IN ('GK', 'DEF', 'MID', 'FWD')),
  CONSTRAINT ck_players_dominant_foot CHECK (dominant_foot IN ('RIGHT', 'LEFT', 'BOTH'))
);

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
  CONSTRAINT ck_matches_format CHECK (format IN (5, 7, 8, 11)),
  CONSTRAINT ck_matches_status CHECK (status IN ('SCHEDULED', 'LIVE', 'CLOSED', 'ANNULLED'))
);

CREATE TABLE match_call_ups (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  match_id   uuid        NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
  player_id  uuid        NOT NULL REFERENCES players (id) ON DELETE RESTRICT,
  status     text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_match_call_ups_status CHECK (status IN ('CONFIRMED', 'MAYBE', 'NOT_GOING'))
);

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
```

Esta vista omite, por legibilidad, tres tipos de líneas que el archivo **sí** contiene: los `CREATE TRIGGER trg_<tabla>_updated_at` (uno por tabla con `updated_at`), los índices de la tabla *Indexes* de arriba, y los CHECK de longitud (`ck_players_name`, `ck_players_nickname`, `ck_matches_location`, `ck_matches_scores`). El DDL ejecutable es el archivo emitido `workspace/src/main/resources/db/migration/V2__core_schema.sql`; el builder no lo escribe ni lo edita: Bootstrap lo copia y Flyway lo aplica.

### Migrations

- Herramienta: Flyway (vía `spring-boot-starter-flyway`), corre automáticamente al arrancar la app y al arrancar cada contexto de test. Ubicación `classpath:db/migration`, nombres `V{n}__{snake_case}.sql`.
- V1 y V2 vienen emitidos en `workspace/` y se aplican desde el paso 1. Los pasos 1–15 no crean migraciones. El cambio de producto aprobado después del paso 15 agrega tres, con la skill `add-migration`: **`V3__player_level.sql`** (paso 17: `ADD COLUMN level integer NOT NULL DEFAULT 5`, `CHECK (level BETWEEN 1 AND 10)` y `DROP DEFAULT`, así los jugadores existentes quedan en 5 y el nivel lo exige la base) y **`V4__match_event_play.sql`** (paso 19: reemplaza `ck_match_events_type` para aceptar `PLAY`) y, con el nivel de un decimal, **`V5__player_level_decimal.sql`** (paso 22: agrega `level_tenths integer`, copia `level * 10`, lo hace `NOT NULL` con `CHECK (level_tenths BETWEEN 10 AND 100)` y elimina `level`, que arrastra a `ck_players_level`; los niveles existentes quedan igual, 5 pasa a 5,0). No hay migración de datos del libro: cuando se construyen, el paso 26 todavía no escribió ninguna fila `BONUS`.
- Producción: forward-only; nunca editar una migración aplicada; cambios destructivos en expand → migrate → contract. La migración corre al arrancar la nueva revisión en Render, antes de servir tráfico (si falla, la revisión no arranca y Render mantiene la anterior).
- Hibernate `ddl-auto: validate` falla el arranque si una entidad no coincide con el esquema.

### Seed data

- Local, primer uso: no hay seed automático; registrás un usuario en `/registro` y creás un grupo.
- Demo: `config/DemoDataSeeder` (paso 28), activo solo con el perfil `demo`. Crea (si no existe) el usuario `demo` con contraseña `DEMO_PASSWORD`, el grupo **"Los del jueves"** con 14 jugadores (incluido el del usuario demo; niveles variados de 1 a 10) y 6 partidos cerrados con eventos, cerrados vía `EloReplayService` para que el libro sea real. Idempotente: si el grupo ya existe, no hace nada. Siembra en `SmartInitializingSingleton.afterSingletonsInstantiated()`, antes de que el servidor web acepte tráfico, así el primer `200` de `/api/health` implica que la siembra ya está commiteada. Comando: `SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh` (arranca, siembra, verifica y apaga) o `SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run`.

---

## 5. API Design

### Conventions

- Base path: `/api`. JSON UTF-8. Sin versionado en la URL (una sola SPA, mismo deploy).
- Respuesta de éxito: el recurso o la lista directamente (sin envoltorio). Listas sin paginar: los volúmenes por grupo son chicos (≤ 40 jugadores, cientos de partidos); `GET …/matches` devuelve como máximo los 200 más recientes.
- Respuesta de error: **RFC 9457 ProblemDetail** (`application/problem+json`), siempre con `detail` en español:
  ```json
  {"type":"about:blank","title":"Bad Request","status":400,"detail":"Hay datos inválidos.","instance":"/api/auth/register","errors":[{"field":"username","message":"Debe tener entre 3 y 30 caracteres."}]}
  ```
  `errors` solo aparece en 400 de validación.
- Códigos: 400 validación · 401 sin sesión o credenciales inválidas · 403 sin CSRF o sin rol dentro de tu grupo · **404 recurso inexistente o de otro grupo (nunca 403 entre grupos)** · 409 conflicto de estado (username tomado, último admin, transición de estado inválida, evento con id repetido y payload distinto) · 422 regla de dominio (jugador fuera del lineup del equipo, evento sin el jugador obligatorio, jugador de otro grupo en el lineup) · 429 throttling con `Retry-After` (segundos).
- Validación: Bean Validation en los `record` de request (`@Valid @RequestBody`), mapeada por `ApiExceptionHandler`.
- Auth: cookie `FM_SESSION`; CSRF: cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` en todo método no-GET (incluidos login, registro y logout).
- Idempotencia: `POST …/events` usa el `id` UUID del cliente como clave (ver detalle).
- Rate limits: login (por usuario+IP y por IP) y registro (por IP), en memoria; ver detalle.
- CORS: deshabilitado. Mismo origen en prod; en dev, el proxy de Vite. Las peticiones cross-origin con credenciales no se aceptan.
- OpenAPI: `/v3/api-docs` y `/swagger-ui.html` en dev; deshabilitados (404) con el perfil `prod`.

### Routes

| Method | Path | Description | Auth | Rate limit |
|---|---|---|---|---|
| GET | /api/health | `{status:"UP", version}`; sin DB, sin sesión | public | — |
| GET | /api/auth/csrf | `{headerName, token}`; emite la cookie `XSRF-TOKEN` | public | — |
| POST | /api/auth/register | Crea usuario e inicia sesión | public | 10/h por IP |
| POST | /api/auth/login | Inicia sesión | public | throttling usuario+IP e IP |
| POST | /api/auth/logout | Invalida la sesión → 204 | user | — |
| GET | /api/auth/me | `{id, username, displayName}` | user | — |
| GET | /api/groups | Mis grupos `[{id, name, myRole, memberCount}]` | user | — |
| POST | /api/groups | Crea grupo; el creador es ADMIN y obtiene su jugador | user | — |
| POST | /api/groups/join | `{inviteCode}` → `{groupId}`; idempotente | user | — |
| GET | /api/groups/{groupId} | `{id, name, inviteCode, membersCanScore, myRole, myUserId}` | member | — |
| PATCH | /api/groups/{groupId} | `{name?, membersCanScore?}` | admin | — |
| POST | /api/groups/{groupId}/invite-code | Regenera el código | admin | — |
| GET | /api/groups/{groupId}/members | `[{userId, username, displayName, role}]` | member | — |
| PATCH | /api/groups/{groupId}/members/{userId} | `{role}`; 409 si deja al grupo sin ADMIN | admin | — |
| DELETE | /api/groups/{groupId}/members/{userId} | Quita miembro (o te vas vos); 409 si es el último ADMIN | admin, o el propio usuario | — |
| GET | /api/groups/{groupId}/players | Jugadores activos (con `level` solo para un ADMIN) | member | — |
| POST | /api/groups/{groupId}/players | Crea invitado `{name, nickname?, level, preferredPosition?, dominantFoot?}` (`level` decimal 1,0–10,0 con un solo decimal, p. ej. `8.5`, obligatorio) | admin | — |
| PATCH | /api/groups/{groupId}/players/{playerId} | Edita nombre/apodo/puesto/pie; `level` (mismo formato decimal) solo lo cambia un admin (403 para un MEMBER) | admin, o el usuario vinculado | — |
| DELETE | /api/groups/{groupId}/players/{playerId} | Soft delete → 204 | admin | — |
| GET | /api/groups/{groupId}/matches | Partidos (más recientes primero) | member | — |
| POST | /api/groups/{groupId}/matches | `{playedAt, location, format}` | admin | — |
| GET | /api/groups/{groupId}/matches/{matchId} | Detalle + convocatoria + lineup | member | — |
| PATCH | /api/groups/{groupId}/matches/{matchId} | Edita fecha/lugar/formato (solo SCHEDULED) | admin | — |
| PUT | /api/groups/{groupId}/matches/{matchId}/call-ups/{playerId} | `{status}` | admin, o el usuario vinculado a ese jugador | — |
| POST | /api/groups/{groupId}/matches/{matchId}/balance | sin cuerpo → propuesta por nivel con ELO de desempate (no guarda) | admin | — |
| PUT | /api/groups/{groupId}/matches/{matchId}/lineup | `{entries:[{playerId, team, position}]}` (solo SCHEDULED) | admin | — |
| POST | /api/groups/{groupId}/matches/{matchId}/start | SCHEDULED → LIVE | admin | — |
| GET | /api/groups/{groupId}/matches/{matchId}/live | Marcador + eventos + `canRecord` | member | — |
| POST | /api/groups/{groupId}/matches/{matchId}/events | Registra evento (idempotente por `id`) | admin, o member si `membersCanScore` (LIVE); solo admin en CLOSED | — |
| POST | /api/groups/{groupId}/matches/{matchId}/events/{eventId}/void | Anula evento | autor o admin (LIVE); solo admin en CLOSED | — |
| POST | /api/groups/{groupId}/matches/{matchId}/close | `{mvpPlayerId?}` LIVE → CLOSED + ELO | admin | — |
| PUT | /api/groups/{groupId}/matches/{matchId}/mvp | `{mvpPlayerId}` en CLOSED → recálculo | admin | — |
| POST | /api/groups/{groupId}/matches/{matchId}/annul | SCHEDULED/CLOSED → ANNULLED (+ recálculo si estaba CLOSED) | admin | — |
| GET | /api/groups/{groupId}/ranking | Ranking | member | — |
| GET | /api/groups/{groupId}/players/{playerId}/stats | Estadísticas del jugador | member | — |
| GET | /api/groups/{groupId}/players/{playerId}/elo-history | Historial de ELO | member | — |

### Critical endpoints — full detail

**POST /api/auth/register** — body `{username, displayName, password}`.
- Validación: `username` 3–30, `^[A-Za-z0-9_.]+$`; `displayName` 1–60 (trim); `password` 8–72 (límite de BCrypt). Fallas → 400 con `errors[]`.
- Username existente sin importar mayúsculas → 409 `"Ese nombre de usuario ya está en uso."`; no se crea fila (la unique `lower(username)` es la red final: `DataIntegrityViolationException` → mismo 409).
- 11.º registro desde una IP dentro de una hora (ventana deslizante) → 429 `"Demasiados registros desde esta conexión. Probá de nuevo en N segundos."` + `Retry-After: N`.
- Éxito → 201 `{id, username, displayName}`; guarda BCrypt; crea sesión (cookie `FM_SESSION`, HttpOnly, SameSite=Lax, Max-Age 30 días, Secure en prod) con el `SecurityContext` (principal `AuthUser`).

**POST /api/auth/login** — body `{username, password}`; requiere CSRF (sin token → 403).
- Orden: (1) si la IP está bloqueada o la clave (lower(username), IP) está bloqueada → 429 + `Retry-After` **sin chequear la contraseña**; (2) busca por `lower(username)`; si no existe, ejecuta igual un BCrypt contra un hash fijo (tiempo constante); (3) contraseña incorrecta → registra el fallo y 401 `"Usuario o contraseña incorrectos."`; (4) correcta → resetea la clave (username, IP), cambia el id de sesión y guarda el contexto → 200 `{id, username, displayName}`.
- Throttling (`AuthRateLimiter`, en memoria, `Clock` inyectable): por clave (username, IP) se cuentan fallos consecutivos; si pasaron más de 15 min desde el último fallo, el contador vuelve a 0. Al llegar al 5.º fallo la clave queda bloqueada `30 s × 2^(fallos − 5)` con tope de 900 s (5.º → 30 s, 6.º → 60 s, 7.º → 120 s, …, ≥ 10.º → 900 s). Mientras está bloqueada, los intentos devuelven 429 y no cuentan. Aparte, por IP: 50 fallos en una ventana deslizante de 15 min → 429 para esa IP hasta que el fallo más viejo salga de la ventana. Otra IP puede seguir entrando a la misma cuenta. Se resetea al reiniciar (aceptado: una instancia).
- `Retry-After` = segundos restantes redondeados hacia arriba; `detail` = `"Demasiados intentos fallidos. Probá de nuevo en N segundos."`.

**POST /api/groups/{groupId}/matches/{matchId}/events** — body `{id, type, team, playerId?, assistPlayerId?}`.
- 404 si no sos miembro o el partido no es del grupo. 409 si el partido está `SCHEDULED` o `ANNULLED`.
- Permiso: `LIVE` → ADMIN, o MEMBER si `members_can_score`; `CLOSED` → solo ADMIN (corrección). Si no → 403.
- Idempotencia: si existe un evento con ese `id` en ese partido y mismo `(type, team, playerId, assistPlayerId)` → 200 con el evento existente (sin duplicar); mismo `id` con otro payload (o de otro partido) → 409.
- Reglas (422): GOAL — `playerId` null o jugador del lineup en `team`; `assistPlayerId` solo con autor, del lineup en `team`, distinto del autor. SAVE, TACKLE y PLAY — `playerId` obligatorio y del lineup en `team` (cualquier jugador, no solo el arquero); PLAY no admite `assistPlayerId`. Tipos válidos: `GOAL`, `SAVE`, `TACKLE`, `PLAY` (Jugadón).
- Éxito → 201 `{id, type, team, playerId, assistPlayerId, recordedBy:{userId, displayName}, createdAt, voidedAt:null}`. En `CLOSED`, además ejecuta `EloReplayService.replayFrom(groupId, matchId)` en la misma transacción.

**POST /api/groups/{groupId}/matches/{matchId}/close** — body `{mvpPlayerId?}`; solo ADMIN.
- 409 si el partido no está `LIVE`. 422 si `mvpPlayerId` no está en el lineup.
- En una transacción: `score_a`/`score_b` = cantidad de GOAL no anulados por equipo; `status = CLOSED`, `closed_at = now()`, `mvp_player_id`; luego `EloReplayService.replayFrom(groupId, matchId)` (cerrar **es** un replay desde ese partido).
- 200 `{scoreA, scoreB, mvpPlayerId, eloChanges:[{playerId, name, eloBefore, eloAfter, delta}]}` (delta total del partido = TEAM_RESULT + BONUS).

**ELO — `EloCalculator` (puro, enteros) y `EloReplayService`.**
- Rating de equipo = promedio (double) del ELO previo al partido de sus jugadores. `E = 1 / (1 + 10^((R_rival − R_propio) / 400))`. `S` = 1 victoria, 0.5 empate, 0 derrota.
- `K` por jugador = 40 si tiene < 10 partidos contados previos en el grupo (partidos `CLOSED` en los que está en el lineup, ordenados antes de este por `(played_at, id)`), si no 24.
- `delta_equipo_i = Math.round(K_i × (S − E))` (semántica `Math.round` de Java: los .5 redondean hacia +∞).
- Bonus (solo jugadores del lineup, nunca negativo): gol +2 c/u; asistencia +1 c/u; MVP +3; valla invicta +2 para **cada** jugador del equipo cuyo rival hizo 0 goles (sin importar el puesto); atajada +1 c/u de cualquier jugador, tope +3; corte +1 c/u, tope +3; jugadón +1 c/u, tope +2; **bonus total con tope +10**. El puesto por partido es informativo y no interviene en el ELO (los arqueros rotan).
- Filas por jugador y partido: una `TEAM_RESULT` y, si bonus > 0, una `BONUS` (con `details`), encadenadas (`BONUS.elo_before = TEAM_RESULT.elo_after`). Partidos `ANNULLED`: sin filas.
- `replayFrom(groupId, matchId)`, una transacción: (1) `SELECT id FROM groups WHERE id = ? FOR UPDATE`; (2) pivote = `(played_at, id)` del partido; (3) borrar filas `TEAM_RESULT`/`BONUS`/`MVP_BONUS` de los partidos del grupo con `(played_at, id) >= pivote`; (4) `players.current_elo` = INITIAL.elo_after + Σ delta de sus filas restantes, para todo jugador del grupo; (5) re-aplicar en orden `(played_at, id)` todos los partidos `CLOSED` con `(played_at, id) >= pivote`. Las filas `INITIAL` nunca se tocan.

**Balanceo — `TeamBalancer` (puro).** Entrada: jugadores confirmados `{id, levelTenths, elo}` (el nivel 1,0–10,0 lo asigna el admin y entra como décimas enteras, 10–100; el puesto no interviene). > 22 jugadores → `IllegalArgumentException` (el controller responde 400 `"El armado automático admite hasta 22 jugadores."`); un nivel fuera de 10–100 décimas → `IllegalArgumentException`. Ordena por id; el equipo A siempre contiene al primero; recorre todos los subconjuntos A que lo contienen con `|A| ∈ {⌊n/2⌋, ⌈n/2⌉}` (22 jugadores → C(21,10) = 352.716). Objetivo, en este orden: (1) minimizar `|promNivelA − promNivelB|`; (2) ante empate exacto, minimizar `|promEloA − promEloB|`; (3) ante empate, la lista ordenada de ids de A lexicográficamente menor. Las diferencias se comparan como fracciones exactas `|sumA·nB − sumB·nA| / (nA·nB)` con `long` sobre décimas enteras (sin flotantes). El ELO solo desempata: el balance es por nivel. Salida: `{teamA:[id…], teamB:[id…], avgLevelA, avgLevelB, levelDifference, avgEloA, avgEloB, eloDifference}` (los promedios de nivel en unidades de nivel: suma de décimas ÷ (10·n)); el controller (solo ADMIN) la expande a `{teamA:[{playerId, name, currentElo, level, position}], …}` (`level` decimal con un decimal, p. ej. `8.5`) con `position` = puesto preferido, informativo.

**Nivel de jugador.** `players.level_tenths` (entero 10–100 = nivel × 10), expuesto en la API como `level` decimal 1,0–10,0 con un solo decimal (`8.5`), obligatorio al crear (400 sin él, fuera de rango, con más de un decimal o si no es un número), editable solo por un ADMIN (un MEMBER que lo envía recibe 403, incluso sobre su propio jugador). **Solo lo ven los admins del grupo:** `GET/POST/PATCH …/players` rellenan `level` únicamente cuando quien llama es ADMIN y lo omiten para un MEMBER; `balance` es solo ADMIN; el detalle del partido, el lineup, el ranking y el perfil no devuelven `level`. Los jugadores creados al crear el grupo o al unirse, y los existentes al aplicar V3, quedan en nivel 5; V5 los conserva (5 pasa a 5,0). El formulario acepta coma o punto como separador decimal y muestra el nivel con un decimal y coma (`5,0`).

**Estadísticas.** Sobre partidos `CLOSED` donde el jugador está en el lineup: `played`, `won`, `drawn`, `lost`; `goals`/`assists` = eventos no anulados de esos partidos; `mvps` = partidos cerrados con `mvp_player_id` = jugador; `streak` = `{type:"W"|"D"|"L", length}` contando desde el último partido hacia atrás mientras el resultado se repita (`null` sin partidos); `currentElo`; `provisional` = `played < 10`. Historial: `[{matchId:null, playedAt:null, eloAfter: INITIAL}, {matchId, playedAt, eloAfter}…]` en orden `(played_at, id)`, donde `eloAfter` = valor tras TEAM_RESULT + BONUS de ese partido. Ranking: jugadores no borrados por `current_elo DESC, name ASC`, con `played` y `provisional`.

---

## 6. Frontend Architecture

### Routes

| Route | Page | Data source | Auth |
|---|---|---|---|
| `/` | redirige a `/grupos` | — | user |
| `/ingresar` | `LoginPage` (acepta `?next=`) | `POST /api/auth/login` | public |
| `/registro` | `RegisterPage` | `POST /api/auth/register` | public |
| `/grupos` | Mis grupos + crear + unirse por código | `GET/POST /api/groups`, `POST /api/groups/join` | user |
| `/unirse/:code` | Unión automática por link | `POST /api/groups/join` | user (si no hay sesión → `/ingresar?next=/unirse/:code`) |
| `/grupos/:groupId` | Detalle: jugadores, miembros, ajustes, links a partidos y ranking | `GET /api/groups/{id}`, `/players`, `/members` | member |
| `/grupos/:groupId/partidos` | Lista + crear partido | `GET/POST …/matches` | member (crear: admin) |
| `/grupos/:groupId/partidos/:matchId` | Convocatoria + armado de equipos + iniciar | `GET …/matches/{id}`, `PUT call-ups`, `POST balance`, `PUT lineup`, `POST start` | member (armado: admin) |
| `/grupos/:groupId/partidos/:matchId/en-vivo` | Modo en vivo; en CLOSED muestra el resumen; cierre con MVP (admin) | `GET …/live` cada 5 s, `POST events`, `POST void`, `POST close` | member |
| `/grupos/:groupId/ranking` | Ranking | `GET …/ranking` | member |
| `/grupos/:groupId/jugadores/:playerId` | Perfil: estadísticas + tabla de historial ELO | `GET …/stats`, `GET …/elo-history` | member |
| `*` | No encontrado | — | public |

### Rendering strategy

SPA renderizada en el cliente (Vite + React 19), servida como estáticos por Spring Boot desde el mismo jar. Desvío deliberado del "server-first" de `frontend-architecture.md`: todo el producto está detrás de login y no tiene SEO, que es exactamente el caso de desvío que ese archivo nombra. Consecuencias que se manejan explícitamente:
- **Sin flash de tema:** `public/theme-init.js` (síncrono en `<head>`) fija `data-theme` antes del primer paint.
- **Sin flash de auth:** las rutas protegidas se envuelven en `RequireAuth`, que muestra un esqueleto mientras `GET /api/auth/me` resuelve y redirige a `/ingresar?next=…` con 401. La autorización real es del servidor (§8).
- **Deep links:** `config/SpaWebConfig` responde `index.html` para cualquier ruta GET sin punto que no empiece con `api/` ni `v3/`; los assets con hash se sirven con su nombre.
- Router: `createBrowserRouter` de `react-router` 7 en modo librería (sin loaders del framework); datos con TanStack Query.

### Component hierarchy

```
main.tsx
└─ QueryClientProvider (retry 1 en queries, 3 en mutaciones de eventos; refetchOnWindowFocus true)
   └─ RouterProvider (app/router.tsx)
      ├─ /ingresar, /registro → AuthPages (sin AppShell de app; header mínimo + toggle de tema)
      └─ RequireAuth (features/auth/session.tsx)
         └─ AppShell (skip link → #main, header con el nombre de la app como link a `/grupos`, UserMenu, ThemeToggle, <main id="main">)
            ├─ Breadcrumbs (`<nav aria-label="Migas de pan">` bajo el header en toda ruta `/grupos/:groupId/**`: «Mis grupos › {Grupo} › Partidos › Partido › En vivo», «… › Ranking › Jugador»; cada tramo salvo el actual es un link y el actual lleva `aria-current="page"`; el nombre del grupo sale de `GET /api/groups/{id}`, «Grupo» mientras carga)
            ├─ GroupsPages (lista con estados loading/empty/error, formulario crear, formulario unirse)
            ├─ GroupDetailPage
            │  ├─ PlayersPanel (lista + alta/edición/baja para admin; «Nivel» 1,0–10,0 con un decimal, coma o punto, visible y editable solo para admin)
            │  ├─ MembersPanel (rol, quitar, irse)
            │  └─ SettingsPanel (admin: nombre, members_can_score, código + regenerar + copiar link)
            ├─ MatchSetupPage
            │  ├─ CallUpList (estado por jugador; un MEMBER solo edita el suyo)
            │  ├─ TeamBuilder (Armar equipos → propuesta por nivel con nivel promedio y diferencia; botones "Pasar al equipo A/B"; select de puesto informativo)
            │  └─ StartButton
            └─ LivePage
               ├─ Scoreboard (sticky arriba, font-mono tabular-nums, role="status")
               ├─ RecordPanel (botones ≥ 64px: Gol, Atajada, Corte y Jugadón A/B; EventDialog de autor/asistencia sobre DialogShell, siempre por encima del Scoreboard)
               ├─ EventList (cada evento con "cargado por"; Anular para autor/admin)
               ├─ UndoButton ("Deshacer" = anular mi último evento no anulado)
               └─ CloseMatchDialog (admin, paso 27: elegir MVP opcional → POST close → resumen de ELO)
```

### State management

- **Server state:** solo TanStack Query, con claves que empiezan por el tenant: `['me']`, `['groups']`, `['groups', groupId]`, `['groups', groupId, 'players']`, `['groups', groupId, 'members']`, `['groups', groupId, 'matches']`, `['groups', groupId, 'matches', matchId]`, `['groups', groupId, 'matches', matchId, 'live']`, `['groups', groupId, 'ranking']`, `['groups', groupId, 'players', playerId, 'stats' | 'elo-history']`. Después de cada mutación se invalida el subárbol `['groups', groupId]` correspondiente; nunca se parchea la caché a mano.
- **Polling en vivo:** la query `live` usa `refetchInterval: 5000` y `refetchIntervalInBackground: false`; no hay WebSockets.
- **Eventos sin conexión estable:** la mutación de evento genera el `id` con `crypto.randomUUID()` **una vez** por toque y reintenta con el mismo `id` (hasta 3 veces); el servidor es idempotente. No hay UI optimista: el marcador se actualiza con la respuesta y el refetch.
- **Client state:** solo `useState` local (diálogos, selección de autor, propuesta de equipos no guardada). Tema en `localStorage` (`fm-theme`). **Sin store global.**
- **URL:** la pestaña activa del detalle de grupo va en `?tab=jugadores|miembros|ajustes`.
- **Formularios:** no controlados con validación al enviar; los errores del servidor (`errors[].field`) se muestran en el campo correspondiente.

### Loading, empty, and error states

| Surface | Loading | Empty | Error |
|---|---|---|---|
| Mis grupos | 3 filas esqueleto | "Todavía no tenés grupos" + Crear grupo + Unirme con código | "No pudimos cargar tus grupos." + Reintentar |
| Jugadores | esqueleto de lista | "Este grupo no tiene jugadores." + (admin) Agregar jugador | mensaje + Reintentar |
| Miembros | esqueleto | (imposible: siempre estás vos) | mensaje + Reintentar |
| Partidos | esqueleto | "No hay partidos todavía." + (admin) Crear partido | mensaje + Reintentar |
| Convocatoria / armado | esqueleto | "Nadie confirmó todavía." / "Necesitás al menos 2 confirmados para armar equipos." | 400/409/422 en `role="alert"` con el `detail` |
| En vivo | marcador con "–" | "Todavía no hay eventos." | banner "Sin conexión, reintentando…" mientras falle el polling; errores de envío en `role="alert"` |
| Ranking | esqueleto de tabla | "Sin jugadores activos." | mensaje + Reintentar |
| Perfil | esqueleto | "Todavía no jugó partidos." (historial solo con el punto inicial) | 404 → "Jugador no encontrado." |

---

## 7. Design System

Sistema "deportiva sobria" confirmado en la fase 3. `ui-ux-pro-max` no corrió en esta sesión; los valores vienen de la decisión del usuario y de `knowledge/capabilities/styling.md`, con contraste verificado abajo. Todos los literales viven en `frontend/src/styles/tokens.css` (emitido en §19.6) y se exponen como utilidades Tailwind vía `@theme inline` en `index.css`.

### Colors

| Token | Light | Dark | Usage |
|---|---|---|---|
| `--fm-primary` | `#15803d` | `#15803d` | fondo de botón primario |
| `--fm-on-primary` | `#ffffff` | `#ffffff` | texto sobre primario |
| `--fm-accent` | `#15803d` | `#4ade80` | links, texto destacado |
| `--fm-background` | `#f7faf8` | `#0b1210` | página |
| `--fm-surface` | `#ffffff` | `#111a17` | cards, paneles, diálogos |
| `--fm-surface-raised` | `#eef4f0` | `#18241f` | elementos elevados, filas hover |
| `--fm-border` | `#d4e0d9` | `#24332d` | divisores decorativos (no son límites de controles) |
| `--fm-border-strong` | `#7a8c82` | `#5b6f65` | **bordes de inputs, selects y botones secundarios** |
| `--fm-text` | `#0f1a14` | `#e6efe9` | texto |
| `--fm-text-muted` | `#4b5d53` | `#9db0a6` | texto secundario |
| `--fm-danger` | `#b91c1c` | `#f87171` | texto de error |
| `--fm-danger-bg` / `--fm-on-danger` | `#b91c1c` / `#ffffff` | `#b91c1c` / `#ffffff` | botón destructivo (Anular) |
| `--fm-mvp` | `#b45309` | `#f59e0b` | texto MVP |
| `--fm-mvp-badge` / `--fm-on-mvp` | `#f59e0b` / `#1a1203` | `#f59e0b` / `#1a1203` | badge MVP |
| `--fm-focus` | `#15803d` | `#4ade80` | anillo de foco (2px + offset 2px) |
| success | usa `--fm-accent` | usa `--fm-accent` | confirmaciones |

**Contrast (WCAG 2.2 AA), calculado con la fórmula de luminancia relativa:**

| Par | Ratio | Requisito |
|---|---|---|
| text-muted `#9db0a6` sobre background `#0b1210` | 8.3:1 | 4.5 ✔ |
| text-muted `#4b5d53` sobre background `#f7faf8` | 6.7:1 | 4.5 ✔ |
| on-primary `#ffffff` sobre primary `#15803d` | 5.0:1 | 4.5 ✔ |
| accent `#4ade80` sobre background `#0b1210` | 10.9:1 | 4.5 ✔ |
| accent `#15803d` sobre surface `#ffffff` | 5.0:1 | 4.5 ✔ |
| danger `#f87171` sobre `#0b1210` | 6.8:1 | 4.5 ✔ |
| danger `#b91c1c` sobre `#ffffff` / on-danger sobre danger-bg | 6.5:1 | 4.5 ✔ |
| mvp `#b45309` sobre `#ffffff` / sobre `#f7faf8` | 5.0:1 / 4.8:1 | 4.5 ✔ |
| on-mvp `#1a1203` sobre mvp-badge `#f59e0b` | 8.6:1 | 4.5 ✔ |
| focus `#15803d` sobre `#f7faf8` | 4.8:1 | 3 ✔ |
| primary `#15803d` (límite de botón) sobre `#0b1210` | 3.8:1 | 3 ✔ |
| **border `#24332d` sobre `#0b1210`** | **1.4:1** | 3 ✘ → **ajuste:** los controles usan `--fm-border-strong` `#5b6f65` (3.5:1); `--fm-border` queda solo para divisores decorativos |
| **border `#d4e0d9` sobre `#ffffff`** | **1.3:1** | 3 ✘ → **ajuste:** `--fm-border-strong` `#7a8c82` (3.6:1) |

Ajuste respecto de la paleta confirmada: se agregó `--fm-border-strong` en ambos temas; ningún otro valor cambió.

### Typography

| Role | Family | Size / line-height | Weight | Tracking |
|---|---|---|---|---|
| Display (marcador en vivo) | JetBrains Mono Variable, `tabular-nums` | 48px / 1.1 | 700 | -0.01em |
| Heading h1 / h2 / h3 | Inter Variable | 30px/1.2 · 24px/1.3 · 20px/1.4 | 700 · 600 · 600 | -0.01em |
| Body | Inter Variable | 16px / 1.6 | 400 | 0 |
| Small / caption | Inter Variable | 14px / 1.5 · 12px / 1.4 | 400 · 500 | 0 |
| Mono (ELO, tablas numéricas) | JetBrains Mono Variable, `tabular-nums` | 14–16px / 1.5 | 500 | 0 |

**Font loading:** self-hosted con `@fontsource-variable/inter` y `@fontsource-variable/jetbrains-mono`, importados en `index.css` (Vite los empaqueta en `assets/`), `font-display: swap` (default de Fontsource); fallback `ui-sans-serif, system-ui, sans-serif` y `ui-monospace, monospace`. Sin CDN (la CSP de prod es `'self'`).

### Spacing, radius, elevation

- Spacing: base 4px — 4, 8, 12, 16, 24, 32, 48, 64 (utilidades Tailwind `1, 2, 3, 4, 6, 8, 12, 16`).
- Radius: 8px botones/inputs (`rounded-lg`), 12px cards y diálogos (`rounded-xl`), `rounded-full` avatares/badges.
- Shadows: plano; solo diálogos `0 8px 24px rgb(0 0 0 / 0.35)`.
- Max content width: 960px (`max-w-240`) · Breakpoints: sm 640, md 768, lg 1024 (mobile-first, verificado a 375px).
- Targets: ≥ 24×24px en todo; botones de registro en vivo `min-h-16` (64px) a todo el ancho de su columna.

### Motion

150ms `ease-out` para hover/foco/aparición de diálogos (solo `opacity`/`transform`). Sin animaciones en bucle. `prefers-reduced-motion: reduce` anula duraciones globalmente (regla en `index.css`).

### Component style

Deportiva y sobria: fondos verde-negro casi planos, superficies separadas por bordes finos en vez de sombras, un único verde de acción, números en monoespaciada como un tablero. Un componente nuevo pertenece si usa solo tokens, tiene un único acento de color y no agrega sombras ni gradientes. Primitivas copy-in (`components/ui.tsx`): `Button` (variantes `primary`, `secondary`, `danger`, `ghost`; tamaños `md` 40px y `live` 64px), `Field` (label + input + error con `aria-describedby`), `Card`, `Alert` (`role="alert"`/`role="status"`).

---

## 8. Authentication & Authorization

### Provider and rationale

Autenticación propia con **Spring Security 7** (usuarios en nuestra tabla `users`, BCrypt) y sesiones de servidor con **Spring Session JDBC** en PostgreSQL. Es el caso "self-hosted auth library inside the app" de `auth.md`: la base es dueña de los usuarios (FKs reales, alta transaccional) y no hay costo por MAU. Se descartó Clerk/hosted porque el requisito es usuario+contraseña sin mail y el proyecto es de portfolio con Java.

### Flows

- **Registro:** `/registro` → `GET /api/auth/csrf` (si no hay cookie `XSRF-TOKEN`) → `POST /api/auth/register` → 201 con sesión → redirige a `next` o `/grupos`. Fallas: 400 (errores en los campos), 409 (username tomado, en el campo), 429 (mensaje con segundos).
- **Login:** `/ingresar` → `POST /api/auth/login` → 200 → `next` o `/grupos`. 401 → "Usuario o contraseña incorrectos." (conserva el usuario tipeado). 429 → "Demasiados intentos fallidos. Probá de nuevo en N segundos." Pegar y autocompletar siempre permitidos (`autocomplete="username"`, `"current-password"`, `"new-password"`).
- **Sesión expirada:** cualquier 401 en una query protegida invalida `['me']` y `RequireAuth` redirige a `/ingresar?next=<ruta actual>`.
- **Logout:** `POST /api/auth/logout` → 204 → `queryClient.clear()` → `/ingresar`.
- **Unirse por link sin sesión:** `/unirse/:code` → `/ingresar?next=/unirse/:code` → tras login vuelve y se une.
- **Recuperar contraseña / borrar cuenta / verificar mail:** no existen en v1 (Non-Goals).

### Route protection

| Surface | Rule | Enforced where |
|---|---|---|
| `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/register` | público | `config/SecurityConfig.java` (`permitAll`) |
| `/api/**` (resto) | autenticado; si no → 401 ProblemDetail | `config/SecurityConfig.java` |
| `/api/groups/{groupId}/**` | miembro del grupo; si no → 404 | `group/GroupAccess.requireMember`, primera línea de cada handler |
| acciones de admin del grupo | rol ADMIN; si no → 403 | `group/GroupAccess.requireAdmin` |
| eventos en vivo | ADMIN, o MEMBER con `members_can_score` | `live/LiveController` |
| rutas del SPA (`/grupos/**`, etc.) | cosméticas | `features/auth/session.tsx` `RequireAuth` |
| `/v3/api-docs`, `/swagger-ui.html` | 404 en `prod` | `application-prod.yml` (`springdoc.*.enabled: false`) + `SpaWebConfig` (no reenvía `v3/` ni rutas con punto) |

**Enforcement rule:** authorization is checked server-side on every request. Client-side route guards are cosmetic and never the only check. A hidden button is not a permission.

### Roles and permissions

| Role | Can | Cannot |
|---|---|---|
| ADMIN (por grupo) | Todo lo de MEMBER + editar grupo, regenerar código, cambiar roles, quitar miembros, crear/editar/borrar jugadores, crear/editar partidos, gestionar toda la convocatoria, balancear, guardar lineup, iniciar, cargar y anular cualquier evento, cerrar, cambiar MVP, anular partidos, corregir partidos cerrados | Dejar al grupo sin ADMIN (409) |
| MEMBER | Ver todo el grupo, cambiar su propia convocatoria, editar su propio jugador vinculado, irse del grupo, cargar eventos si `members_can_score` (solo LIVE), anular sus propios eventos (solo LIVE) | Acciones de admin (403) |
| No miembro | Unirse con código | Ver cualquier recurso del grupo (404) |

### Sessions

- Cookie de sesión opaca `FM_SESSION` (Spring Session JDBC, tablas `SPRING_SESSION*`): `HttpOnly`, `SameSite=Lax`, `Max-Age` 30 días, timeout de inactividad 30 días, `Secure` en `prod`. Sobrevive reinicios y el sleep de Render porque vive en Postgres; es revocable (logout borra la fila). Sin JWT ni refresh tokens. Limpieza de sesiones vencidas cada 6 h (`cleanup-cron`).
- Login cambia el id de sesión (protección de fijación).
- **CSRF:** `http.csrf(c -> c.spa())` (Spring Security 7): token en cookie `XSRF-TOKEN` (legible por JS) y validado en el header `X-XSRF-TOKEN` en todo método no seguro, incluidos login/registro/logout. El frontend obtiene la cookie con `GET /api/auth/csrf` si no la tiene.
- **CORS:** sin configuración → el navegador bloquea orígenes cruzados; mismo origen en prod, proxy de Vite en dev.

### Multi-tenancy / row-level isolation

Mecanismo doble, ambos obligatorios: (1) **guarda única** `GroupAccess.requireMember(groupId, user)` / `requireAdmin(...)`, primera línea de cada handler bajo `/api/groups/{groupId}/**`, que responde 404 si no hay membresía; (2) **alcance obligatorio en la capa de datos**: todo método de repositorio sobre datos de grupo recibe `groupId` (`findByIdAndGroupId`, `findAllByGroupId…`), y las queries `JdbcClient` lo llevan en el `WHERE`. Un id válido de otro grupo da 404 porque el `findByIdAndGroupId` no lo encuentra, aun si alguien olvidara la guarda. Los tests de aislamiento (pasos 7, 8, 12, 17) lo prueban en lectura, edición y borrado.

---

## 9. BUILD ORDER

### The rules of a step

1. **One sitting per step.** A step touches at most **5 files** and carries **2–6** acceptance criteria. If more is needed, split it.
2. **Every step carries all four fields:** `Do`, `Done when`, `Verify`, `Checkpoint`, ejecutados en ese orden. El `Checkpoint` es un bloque de shell literal: `git add -A && git commit -m "E<epic>-T<tarea>: <título>"` (el mismo mensaje que el bloque de la tarea en su epic) y `git tag step-NN-slug`.
3. **"Done when" es observable y decidible por un comando**, en forma EARS (**WHEN** … **THE SYSTEM SHALL** …).
4. **Prohibido "se ve bien"**, "funciona", "está implementado".
5. **`Verify` es shell literal** (Git Bash, desde la raíz del proyecto) y su código de salida decide: 0 = paso correcto.
6. **Un paso no está hecho hasta que pasan sus `Verify` y los de los pasos anteriores.** Los pasos de backend terminan con `./mvnw -q verify`, que corre la suite completa.
7. **Checkpoint en cada paso.** Si un paso sale mal: `git reset --hard <tag del paso anterior>` y reintentar; nunca depurar hacia adelante.
8. **Nunca saltar pasos.** Si el paso 7 está bloqueado, se frena y se reporta.
9. **Ningún paso rompe un gate anterior.** No hay validación de entorno por pasos: las únicas variables obligatorias (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) se requieren desde el paso 1 y las provee Bootstrap; `DEMO_PASSWORD` solo se lee con el perfil `demo` (paso 28). Ninguna regla de lint/tsconfig se endurece después del paso 2: la configuración emitida es la final.
10. **Ningún número derivado sin contar.** Los gates afirman propiedades (cada tabla de §4 existe, `exit 0`); los únicos números son invariantes de dominio (14 jugadores y 6 partidos del seed demo, cotas ELO calculadas en §5).
11. **Un `Verify` sale con 0 cuando el paso es correcto.** Los fallos esperados se escriben `status=0; cmd || status=$?; test "$status" -eq N`.
12. **Cada chequeo es posible en su medio.** El tamaño real de los botones (layout) solo lo mide Playwright; en jsdom se verifica la utilidad `min-h-16`.
13. **El primer paso que produce un ejecutable lo ejecuta**: el paso 1 arranca el jar y le pega a `/api/health`; el paso 3 corre la imagen Docker.
14. **Un `Verify` no depende de su propio `Checkpoint`.** Ningún `Verify` de este documento consulta `git status`, `git ls-files` ni tags; esas comprobaciones viven en §20.1.
15. **Salida byte-exacta:** este blueprint no autoría golden files (§19.6, *Byte-exact artifact reconciliation*: NOT APPLICABLE).
16. **Un gate falla por la razón correcta.** Ningún gate acepta "cualquier código distinto de 0".

### One step, one unit — the counting rule

> **One Section 9 step = one `tasks.json` task = one task block in an epic file.**

Este blueprint tiene **28 pasos** → 28 tareas en `tasks.json` → 4 epics (rango legal para 28 pasos: 4 a 5 epics; `ceil(28÷9)=4`, `floor(28÷5)=5`). El epic 03 conserva 6 pasos y el epic 04, agregado por el cambio de producto de arqueros rotativos, nivel y jugadón (aprobado después de construir el paso 15), tiene 10: los pasos 16–20 y 24–25 son del cambio original y los pasos 21–23 (nivel con un decimal, `E4-T8…E4-T10`) se agregaron después de construir el paso 20; los diez se ejecutan entre el 15 y el 26. El epic 04 supera la guía de 5–9 tareas por epic a pedido del dueño; el chequeo obligatorio es el del bundle (4 epics para 28 pasos) y se cumple.

| Epic | Pasos | Archivo |
|---|---|---|
| 01 — Fundaciones y cuentas | 1–6 | `epics/01-fundaciones-y-cuentas.md` |
| 02 — Grupos, jugadores y motores | 7–12 | `epics/02-grupos-jugadores-y-motores.md` |
| 03 — En vivo, ELO y producción | 13–15 y 26–28 | `epics/03-en-vivo-elo-y-produccion.md` |
| 04 — Arqueros rotativos, nivel y jugadón | 16–25 | `epics/04-arqueros-rotativos-nivel-y-jugadon.md` |

### Step map

| # | Step | Depends on | Touches | Gate |
|---|---|---|---|---|
| 1 | Esqueleto backend con health y esquema | — | `src/main/java/com/futbolmanager/FutbolManagerApplication.java`, `src/main/java/com/futbolmanager/config/SecurityConfig.java`, `src/main/java/com/futbolmanager/common/HealthController.java`, `src/main/java/com/futbolmanager/common/ApiException.java`, `src/test/java/com/futbolmanager/common/HealthIntegrationTest.java` | `bash scripts/smoke.sh` |
| 2 | Shell del frontend servido por el jar | 1 | `frontend/src/main.tsx`, `frontend/src/app/router.tsx`, `frontend/src/app/AppShell.tsx`, `frontend/src/app/AppShell.test.tsx`, `src/main/java/com/futbolmanager/config/SpaWebConfig.java` | `bash scripts/smoke.sh / '<div id="root"></div>' /grupos '<div id="root"></div>'` |
| 3 | Imagen Docker, CI, perfil prod y primitivas UI | 2 | `Dockerfile`, `.github/workflows/ci.yml`, `src/main/resources/application-prod.yml`, `render.yaml`, `frontend/src/components/ui.tsx` | `bash scripts/docker-smoke.sh` |
| 4 | API de cuentas con sesión y CSRF | 1 | `src/main/java/com/futbolmanager/auth/User.java`, `src/main/java/com/futbolmanager/auth/AuthUser.java`, `src/main/java/com/futbolmanager/auth/AuthController.java`, `src/main/java/com/futbolmanager/common/ApiExceptionHandler.java`, `src/test/java/com/futbolmanager/auth/AuthIntegrationTest.java` | `./mvnw -q test -Dtest=AuthIntegrationTest` |
| 5 | Throttling de login y registro | 4 | `src/main/java/com/futbolmanager/auth/AuthRateLimiter.java`, `src/main/java/com/futbolmanager/auth/AuthController.java`, `src/test/java/com/futbolmanager/auth/AuthRateLimiterTest.java`, `src/test/java/com/futbolmanager/auth/AuthThrottleIntegrationTest.java` | `./mvnw -q test -Dtest=AuthThrottleIntegrationTest` |
| 6 | Pantallas de login y registro | 3, 4 | `frontend/src/lib/api.ts`, `frontend/src/features/auth/session.tsx`, `frontend/src/features/auth/AuthPages.tsx`, `frontend/src/app/router.tsx`, `frontend/src/features/auth/AuthPages.test.tsx` | `npm --prefix frontend run test` |
| 7 | API de grupos, invitación y miembros | 4 | `src/main/java/com/futbolmanager/group/FriendGroup.java`, `src/main/java/com/futbolmanager/group/Membership.java`, `src/main/java/com/futbolmanager/group/GroupAccess.java`, `src/main/java/com/futbolmanager/group/GroupController.java`, `src/test/java/com/futbolmanager/group/GroupIntegrationTest.java` | `./mvnw -q test -Dtest=GroupIntegrationTest` |
| 8 | API de jugadores con ELO inicial | 7 | `src/main/java/com/futbolmanager/player/Player.java`, `src/main/java/com/futbolmanager/player/PlayerService.java`, `src/main/java/com/futbolmanager/player/PlayerController.java`, `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` | `./mvnw -q test -Dtest=PlayerIntegrationTest` |
| 9 | Pantallas de grupos, miembros y jugadores | 6, 8 | `frontend/src/features/groups/api.ts`, `frontend/src/features/groups/GroupsPages.tsx`, `frontend/src/features/groups/GroupDetailPage.tsx`, `frontend/src/app/router.tsx`, `frontend/src/features/groups/GroupsPages.test.tsx` | `npm --prefix frontend run test` |
| 10 | Calculadora ELO pura | 1 | `src/main/java/com/futbolmanager/elo/EloCalculator.java`, `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` | `./mvnw -q test -Dtest=EloCalculatorTest` |
| 11 | Balanceador de equipos puro | 1 | `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java`, `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` | `./mvnw -q test -Dtest=TeamBalancerTest` |
| 12 | API de partidos, convocatoria, balance y lineup | 8, 11 | `src/main/java/com/futbolmanager/match/Match.java`, `src/main/java/com/futbolmanager/match/MatchCallUp.java`, `src/main/java/com/futbolmanager/match/LineupEntry.java`, `src/main/java/com/futbolmanager/match/MatchController.java`, `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` | `./mvnw -q test -Dtest=MatchIntegrationTest` |
| 13 | Pantallas de partido, convocatoria y armado | 9, 12 | `frontend/src/features/matches/api.ts`, `frontend/src/features/matches/MatchesPages.tsx`, `frontend/src/features/matches/MatchSetupPage.tsx`, `frontend/src/app/router.tsx`, `frontend/src/features/matches/MatchSetupPage.test.tsx` | `npm --prefix frontend run test` |
| 14 | API de eventos en vivo | 12 | `src/main/java/com/futbolmanager/live/MatchEvent.java`, `src/main/java/com/futbolmanager/live/LiveController.java`, `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` | `./mvnw -q test -Dtest=LiveIntegrationTest` |
| 15 | Modo en vivo mobile-first | 13, 14 | `frontend/src/features/live/api.ts`, `frontend/src/features/live/LivePage.tsx`, `frontend/src/app/router.tsx`, `frontend/src/features/live/LivePage.test.tsx` | `npm --prefix frontend run test` |
| 16 | Reglas de bonus ELO sin puestos | 10, 15 | `src/main/java/com/futbolmanager/elo/EloCalculator.java`, `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` | `./mvnw -q test -Dtest=EloCalculatorTest` |
| 17 | Nivel de jugador (solo admin) | 15 | `src/main/resources/db/migration/V3__player_level.sql`, `src/main/java/com/futbolmanager/player/Player.java`, `src/main/java/com/futbolmanager/player/PlayerController.java`, `src/main/java/com/futbolmanager/player/PlayerService.java`, `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java`, `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java`, `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` | `./mvnw -q test -Dtest=PlayerIntegrationTest` |
| 18 | Balanceo por nivel | 17, 11 | `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java`, `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java`, `src/main/java/com/futbolmanager/match/MatchController.java`, `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` | `./mvnw -q test -Dtest=TeamBalancerTest` |
| 19 | Jugadón y atajadas abiertas en la API | 14 | `src/main/resources/db/migration/V4__match_event_play.sql`, `src/main/java/com/futbolmanager/live/MatchEvent.java`, `src/main/java/com/futbolmanager/live/LiveController.java`, `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` | `./mvnw -q test -Dtest=LiveIntegrationTest` |
| 20 | Nivel en las pantallas de jugadores | 17 | `frontend/src/features/groups/api.ts`, `frontend/src/features/groups/GroupDetailPage.tsx`, `frontend/src/features/groups/GroupsPages.test.tsx` | `npm --prefix frontend run test` |
| 21 | Balanceo en décimas de nivel | 18 | `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java`, `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java`, `src/main/java/com/futbolmanager/match/MatchController.java`, `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java`, `src/main/java/com/futbolmanager/player/Player.java` | `./mvnw -q test -Dtest=TeamBalancerTest` |
| 22 | Nivel de jugador con un decimal (en décimas) | 21, 17 | `src/main/resources/db/migration/V5__player_level_decimal.sql`, `src/main/java/com/futbolmanager/player/Player.java`, `src/main/java/com/futbolmanager/player/PlayerService.java`, `src/main/java/com/futbolmanager/player/PlayerController.java`, `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java`, `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` | `./mvnw -q test -Dtest=PlayerIntegrationTest` |
| 23 | Nivel decimal en el formulario de jugadores | 22, 20 | `frontend/src/features/groups/PlayerForm.tsx`, `frontend/src/features/groups/PlayersTab.tsx`, `frontend/src/features/groups/GroupsPages.test.tsx` | `npm --prefix frontend run test` |
| 24 | Nivel promedio en el armado de equipos | 18, 13, 21 | `frontend/src/features/matches/api.ts`, `frontend/src/features/matches/MatchSetupPage.tsx`, `frontend/src/features/matches/MatchSetupPage.test.tsx` | `npm --prefix frontend run test` |
| 25 | Jugadón y diálogos en el modo en vivo | 19, 15 | `frontend/src/components/ui.tsx`, `frontend/src/features/live/api.ts`, `frontend/src/features/live/EventDialog.tsx`, `frontend/src/features/live/LivePage.tsx`, `frontend/src/features/live/LivePage.test.tsx` | `npm --prefix frontend run test` |
| 26 | Cierre, libro ELO y recálculo | 10, 14, 16, 19 | `src/main/java/com/futbolmanager/elo/EloReplayService.java`, `src/main/java/com/futbolmanager/match/MatchLifecycleController.java`, `src/main/java/com/futbolmanager/live/LiveController.java`, `src/test/java/com/futbolmanager/elo/EloReplayIntegrationTest.java`, `src/test/java/com/futbolmanager/elo/EloReplayPropertyTest.java` | `./mvnw -q test -Dtest=EloReplayPropertyTest` |
| 27 | Ranking, estadísticas y cierre en la UI | 15, 25, 26 | `src/main/java/com/futbolmanager/stats/StatsController.java`, `src/test/java/com/futbolmanager/stats/StatsIntegrationTest.java`, `frontend/src/features/stats/StatsPages.tsx`, `frontend/src/features/live/LivePage.tsx`, `frontend/src/app/router.tsx` | `./mvnw -q test -Dtest=StatsIntegrationTest` |
| 28 | Hardening, demo, E2E y deploy | 3, 5, 20, 24, 27 | `src/main/java/com/futbolmanager/config/SecurityConfig.java`, `src/main/java/com/futbolmanager/config/DemoDataSeeder.java`, `src/test/java/com/futbolmanager/config/ProdProfileIntegrationTest.java`, `frontend/e2e/flows.spec.ts`, `README.md` | `npm --prefix frontend run test:e2e` |

Orden: esqueleto ejecutable (1–3, con el jar y la imagen corriendo desde el principio) → cuentas (4–6) → tenancy y jugadores (7–9) → motores puros (10–11) → partidos (12–13) → vivo (14–15) → cambio de producto: bonus sin puestos, nivel, balanceo por nivel, nivel con un decimal y jugadón (16–25) → ELO real (26) → lectura (27) → hardening y deploy (28).

---

#### Step 1 — Esqueleto backend con health y esquema

**Do**
Precondición: Bootstrap de §10 ya corrió (están `pom.xml`, `application.yml`, `V1`/`V2`, `TestcontainersConfiguration`, `ApiTestClient`, `scripts/smoke.sh`, `.env` y el Maven wrapper). Crear:
- `src/main/java/com/futbolmanager/FutbolManagerApplication.java` — `@SpringBootApplication` + `@EnableJpaRepositories(considerNestedRepositories = true)` (habilita los repositorios anidados en las entidades, §19.6) + `@Bean Clock clock()` que devuelve `Clock.systemUTC()`. `main` construye `new SpringApplication(FutbolManagerApplication.class)` y le agrega, antes de `run(args)`, un listener `ApplicationListener<ApplicationEnvironmentPreparedEvent>` (corre después de que `spring.config.import` cargó `.env`) que, para cada nombre de `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` en ese orden, si `event.getEnvironment().getProperty(nombre)` es nulo o en blanco lanza `IllegalStateException("Falta la variable de entorno obligatoria " + nombre)`. Así el arranque sin `DB_URL` falla con un mensaje propio del proyecto (gate del paso 3), no con un texto del runtime. Los tests no pasan por `main` (y Surefire define las tres), así que no los afecta. Además, lo primero que hace `main` es `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))`: un JVM de Windows en Argentina reporta la zona legacy `America/Buenos_Aires`, que PostgreSQL 17 rechaza al conectar (`FATAL: invalid value for parameter "TimeZone"`); los timestamps del proyecto son UTC de todos modos. Los tests no pasan por `main`, así que el `pom.xml` fuerza lo mismo con `-Duser.timezone=UTC` en el `argLine` de Surefire.
- `src/main/java/com/futbolmanager/config/SecurityConfig.java` — un `SecurityFilterChain`: `csrf(c -> c.spa())`; `permitAll` para `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/register`; `authenticated()` para el resto de `/api/**`; `permitAll` para todo lo demás (SPA, assets, Swagger en dev); `formLogin`, `httpBasic` y `logout` deshabilitados (los endpoints de auth son JSON propios, paso 4); `authenticationEntryPoint` que responde 401 `application/problem+json` con `detail` `"Tenés que iniciar sesión."` y `accessDeniedHandler` que responde 403 con `"No tenés permiso para hacer esto."`; headers `frameOptions(deny)`, `contentTypeOptions`, `referrerPolicy(STRICT_ORIGIN_WHEN_CROSS_ORIGIN)`. Beans: `PasswordEncoder` (`BCryptPasswordEncoder`) y `SecurityContextRepository` (`HttpSessionSecurityContextRepository`), registrado también en `http.securityContext(...)`.
- `src/main/java/com/futbolmanager/common/HealthController.java` — `GET /api/health` → `record Health(String status, String version)` con `version` de `@Value("${fm.version}")`. Sin repositorios, sin `HttpSession`, sin acceso a la base.
- `src/main/java/com/futbolmanager/common/ApiException.java` — `RuntimeException` con `HttpStatus status`, `String detail` (español) y `Long retryAfterSeconds` opcional; fábricas `badRequest`, `notFound`, `forbidden`, `conflict`, `unprocessable`, `tooManyRequests(detail, seconds)`. El handler llega en el paso 4.
- `src/test/java/com/futbolmanager/common/HealthIntegrationTest.java` — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)` (en Spring Boot 4 `AutoConfigureMockMvc` vive en `org.springframework.boot.webmvc.test.autoconfigure`, no en `org.springframework.boot.test.autoconfigure.web.servlet`; lo trae `spring-boot-starter-webmvc-test`); usa `ApiTestClient` y `JdbcClient`; verifica los criterios 1–3.

**Done when**
- [ ] WHEN corre `./mvnw -q test -Dtest=HealthIntegrationTest` THE SYSTEM SHALL levantar un `postgres:17` con Testcontainers, aplicar `V1__spring_session.sql` y `V2__core_schema.sql` y comprobar con `to_regclass` que existen `spring_session`, `spring_session_attributes`, `users`, `groups`, `memberships`, `players`, `matches`, `match_call_ups`, `match_lineup_entries`, `match_events` y `elo_changes`.
- [ ] WHEN se pide `GET /api/health` sin sesión THE SYSTEM SHALL responder 200 con `status` = `UP` y `version` = `dev` y SHALL NOT emitir la cookie `FM_SESSION`.
- [ ] WHEN una petición sin sesión llega a `GET /api/groups` THE SYSTEM SHALL responder 401 con content type `application/problem+json`.
- [ ] WHEN corre `bash scripts/smoke.sh` THE SYSTEM SHALL arrancar `target/futbol-manager.jar` contra la base de compose (puerto de host 5433, credenciales de `.env`) y salir con 0 después de que `GET /api/health` responda 200 con `"status":"UP"`.

**Verify**
```bash
./mvnw -q test -Dtest=HealthIntegrationTest   # expect: exit 0 — criterios 1–3 (requiere Docker)
./mvnw -q verify                              # expect: exit 0 — suite completa + empaqueta target/futbol-manager.jar
bash scripts/smoke.sh                         # expect: exit 0, imprime "smoke OK" — corre el jar de verdad
```

**Checkpoint**
```bash
git add -A && git commit -m "E1-T1: build backend skeleton with health and schema"
git tag step-01-backend-skeleton
```

---

#### Step 2 — Shell del frontend servido por el jar

**Do**
Crear:
- `frontend/src/main.tsx` — importa `./styles/index.css`, crea un `QueryClient` (queries `retry: 1`) y monta `<QueryClientProvider><RouterProvider router={router} /></QueryClientProvider>` en `#root` (`StrictMode`).
- `frontend/src/app/router.tsx` — `createBrowserRouter` de `react-router`: `/` → `<Navigate to="/grupos" replace />`; `/grupos` → página provisoria dentro de `AppShell` con `<h1>Mis grupos</h1>` (la reemplaza el paso 9); `*` → página "No encontrado" con `<h1>Página no encontrada</h1>` y link a `/grupos`. Exporta `router` y una función `createAppRouter(initialEntries?)` que usa `createMemoryRouter` para tests.
- `frontend/src/app/AppShell.tsx` — layout: link "Saltar al contenido" → `#main` (visible al foco), `<header>` con el nombre "Fútbol Manager" (no es `h1`), una prop `actions?: ReactNode` que el paso 6 usa para el menú de usuario, `ThemeToggle` (botón con nombre accesible "Cambiar a tema claro"/"Cambiar a tema oscuro" que alterna `document.documentElement.dataset.theme` y guarda `fm-theme` en `localStorage`), `<main id="main">` con `<Outlet />`; ancho máximo `max-w-240`, mobile-first.
- `frontend/src/app/AppShell.test.tsx` — renderiza `<AppShell />` directamente dentro de un `createMemoryRouter` propio del test (ruta `/` con `element: <AppShell />` y una ruta hija índice con `<h1>Mis grupos</h1>`), así no depende de `router.tsx` ni de la autenticación que agrega el paso 6, y verifica: skip link con `href="#main"`, exactamente un `heading` de nivel 1, `main` con id `main`, y que dos clics en el toggle llevan el tema `dark` → `light` → `dark` con `localStorage.getItem('fm-theme')` coherente.
- (Ya existe, emitido: `frontend/e2e/health.spec.ts`, que este paso ejecuta por primera vez con `test:e2e`; no se edita.)
- `src/main/java/com/futbolmanager/config/SpaWebConfig.java` — `WebMvcConfigurer` que registra `/**` → `classpath:/static/` con un `PathResourceResolver` que devuelve el recurso pedido si existe; si no existe y la ruta no empieza con `api/` ni `v3/` y no contiene `.`, devuelve `index.html` si existe; en cualquier otro caso `null` (404).

**Done when**
- [ ] WHEN corre `npm --prefix frontend run test` THE SYSTEM SHALL pasar `src/app/AppShell.test.tsx`, que verifica un skip link a `#main`, exactamente un `h1`, un landmark `main` con id `main` y un botón de tema que alterna `document.documentElement.dataset.theme` entre `dark` y `light` y guarda la elección en `localStorage` bajo la clave `fm-theme`.
- [ ] WHEN corren `format:check`, `lint` y `typecheck` sobre `frontend/` THE SYSTEM SHALL salir con 0 sin warnings.
- [ ] WHEN corre `npm --prefix frontend run build` THE SYSTEM SHALL escribir `index.html`, `theme-init.js` y los assets con hash en `src/main/resources/static/`.
- [ ] WHEN el jar empaquetado recibe `GET /` y el deep link `GET /grupos` THE SYSTEM SHALL responder 200 con el `index.html` del SPA que contiene `<div id="root"></div>`.
- [ ] WHEN corre `npm --prefix frontend run test:e2e` THE SYSTEM SHALL arrancar el jar con el `webServer` de `frontend/playwright.config.ts` (cwd raíz, `.env`) y pasar `e2e/health.spec.ts` (emitido): `GET /api/health` → 200 con `status` = `UP`.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0 — incluye los archivos de workspace/ ya copiados
npm --prefix frontend run lint           # expect: exit 0, 0 warnings (--max-warnings 0)
npm --prefix frontend run typecheck      # expect: exit 0
npm --prefix frontend run test           # expect: exit 0, 0 failed
npm --prefix frontend run build          # expect: exit 0 — salida en src/main/resources/static/
test -f src/main/resources/static/theme-init.js   # expect: exit 0
./mvnw -q -DskipTests package            # expect: exit 0 — el jar incluye static/
bash scripts/smoke.sh / '<div id="root"></div>' /grupos '<div id="root"></div>'   # expect: exit 0, "smoke OK"
npm --prefix frontend run e2e:install    # expect: exit 0 — descarga Chromium (una vez; health.spec no lo usa, flows.spec del paso 28 sí)
npm --prefix frontend run test:e2e       # expect: exit 0 — 1 test (health.spec.ts), 0 failed: contrato webServer ↔ jar ↔ .env
```

**Checkpoint**
```bash
git add -A && git commit -m "E1-T2: serve the frontend shell from the jar"
git tag step-02-frontend-shell
```

---

#### Step 3 — Imagen Docker, CI, perfil prod y primitivas UI

**Do**
Crear:
- `Dockerfile` — tres etapas: (1) `node:24-alpine` en `/app/frontend`: copia `frontend/package.json` y `frontend/package-lock.json`, `npm ci`, copia `frontend/`, `npm run build` (escribe en `/app/src/main/resources/static`); (2) `eclipse-temurin:21-jdk` en `/app`: copia `.mvn/`, `mvnw`, `pom.xml`, `src/`, y el `static/` de la etapa 1, `chmod +x mvnw`, `./mvnw -q -DskipTests package`; (3) `eclipse-temurin:21-jre`: crea usuario de sistema `app` (`groupadd --system app && useradd --system --gid app --no-create-home app`), copia `target/futbol-manager.jar` como `/app/app.jar`, `USER app`, `EXPOSE 8080`, `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]`. `.dockerignore` ya existe (emitido, excluye `blueprints/`).
- `.github/workflows/ci.yml` — en `push` a `main` y en `pull_request`: job `backend` (`ubuntu-latest`, `actions/checkout`, `actions/setup-java` temurin 21 con caché maven, paso `run: chmod +x mvnw && ./mvnw -B verify` — Docker está disponible en el runner para Testcontainers); job `frontend` (`actions/setup-node` con `node-version-file: .nvmrc` y caché npm de `frontend/package-lock.json`; en `frontend/`: `npm ci`, `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build`). Versiones de las actions: ver §11 (UNVERIFIED).
- `src/main/resources/application-prod.yml` — `server.servlet.session.cookie.secure: true`; `springdoc.api-docs.enabled: false`; `springdoc.swagger-ui.enabled: false`; `fm.security.content-security-policy: "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'"` (lo consume el paso 28); `logging.structured.format.console: ecs`.
- `render.yaml` — un servicio `type: web`, `name: futbol-manager`, `runtime: docker`, `plan: free`, `region: virginia`, `dockerfilePath: ./Dockerfile`, `dockerContext: .`, `healthCheckPath: /api/health`, `autoDeploy: true`, `envVars`: `SPRING_PROFILES_ACTIVE` = `prod`, y `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` con `sync: false` (se cargan a mano en Render).
- `frontend/src/components/ui.tsx` — primitivas copy-in con `cva` + `tailwind-merge` + `clsx`: `Button` (variantes `primary` = `bg-primary text-on-primary`, `secondary` = `border border-border-strong`, `danger` = `bg-danger-bg text-on-danger`, `ghost`; tamaños `md` `min-h-10` y `live` `min-h-16 w-full text-xl`), `Field` (label + input + texto de error con `id` enlazado por `aria-describedby` y `aria-invalid`), `Card`, `Alert` (`role="alert"` para errores, `role="status"` para info). Sin colores hex.

**Done when**
- [ ] WHEN corre `docker build -t futbol-manager:local .` THE SYSTEM SHALL compilar el frontend en `node:24-alpine`, empaquetar el jar en `eclipse-temurin:21-jdk` y producir una imagen `eclipse-temurin:21-jre` que corre como usuario no-root.
- [ ] WHEN corre `bash scripts/docker-smoke.sh` THE SYSTEM SHALL correr la imagen con `SPRING_PROFILES_ACTIVE=prod` contra la base de compose y comprobar 200 con `"status":"UP"` en `/api/health`, un uid distinto de 0 y 404 en `/v3/api-docs` y `/swagger-ui.html`.
- [ ] WHEN el jar arranca con `DB_URL` ausente del entorno y sin `.env` en el directorio de trabajo THE SYSTEM SHALL terminar con código 1 y escribir el error `Falta la variable de entorno obligatoria DB_URL` (lo lanza el listener de `FutbolManagerApplication`, paso 1).
- [ ] WHEN se leen `.github/workflows/ci.yml` y `render.yaml` THE SYSTEM SHALL contener un job que corre `./mvnw -B verify` y un servicio web Docker con `healthCheckPath: /api/health`.
- [ ] WHEN corren `format:check`, `lint` y `typecheck` del frontend con `src/components/ui.tsx` presente THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q verify   # expect: exit 0 — suite completa y target/futbol-manager.jar actualizado
status=0; (cd target && env -u DB_URL DB_USERNAME=x DB_PASSWORD=x java -jar futbol-manager.jar > missing-env.log 2>&1) || status=$?; test "$status" -eq 1
                   # expect: exit 0 — el jar terminó con 1 (cwd target/: no hay .env que cargar)
grep -qF "Falta la variable de entorno obligatoria DB_URL" target/missing-env.log   # expect: exit 0 — mensaje propio (paso 1), no del runtime
grep -q './mvnw -B verify' .github/workflows/ci.yml     # expect: exit 0
grep -q 'healthCheckPath: /api/health' render.yaml      # expect: exit 0
test -f frontend/src/components/ui.tsx                  # expect: exit 0
npm --prefix frontend run format:check                  # expect: exit 0
npm --prefix frontend run lint                          # expect: exit 0
npm --prefix frontend run typecheck                     # expect: exit 0
docker build -t futbol-manager:local .                  # expect: exit 0
bash scripts/docker-smoke.sh                            # expect: exit 0, "docker-smoke OK"
```

**Checkpoint**
```bash
git add -A && git commit -m "E1-T3: add docker image, ci, prod profile and ui primitives"
git tag step-03-docker-ci
```

---

#### Step 4 — API de cuentas con sesión y CSRF

**Do**
Crear:
- `src/main/java/com/futbolmanager/auth/User.java` — entidad `users` (`@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id` — convención para todas las entidades salvo `MatchEvent`, cuyo id lo trae el cliente), `username`, `displayName`, `passwordHash`; `created_at`/`updated_at` sin mapear. Interfaz anidada `Repo extends JpaRepository<User, UUID>` con `@Query("select u from User u where lower(u.username) = lower(:username)") Optional<User> findByUsernameIgnoreCase(String username)`.
- `src/main/java/com/futbolmanager/auth/AuthUser.java` — `record AuthUser(UUID id, String username, String displayName) implements Serializable` (va en la sesión JDBC, por eso `Serializable`).
- `src/main/java/com/futbolmanager/auth/AuthController.java` — `/api/auth`: `GET /csrf` (`CsrfToken` como parámetro → `{headerName, token}`), `POST /register` (record `RegisterRequest` con `@NotBlank @Size(min=3,max=30) @Pattern(regexp="^[A-Za-z0-9_.]+$") username`, `@NotBlank @Size(max=60) displayName`, `@NotBlank @Size(min=8,max=72) password`; 409 si existe sin importar mayúsculas, también ante `DataIntegrityViolationException`; guarda BCrypt; inicia sesión; 201), `POST /login` (busca por username sin mayúsculas; si no existe ejecuta igual `passwordEncoder.matches` contra un hash BCrypt fijo; 401 `"Usuario o contraseña incorrectos."`; en éxito `request.changeSessionId()` si ya había sesión, arma `UsernamePasswordAuthenticationToken(authUser, null, List.of())`, lo guarda con el `SecurityContextRepository` del paso 1 → 200), `POST /logout` (invalida la sesión, limpia el contexto → 204), `GET /me` (`@AuthenticationPrincipal AuthUser` → 200).
- `src/main/java/com/futbolmanager/common/ApiExceptionHandler.java` — `@RestControllerAdvice` que extiende `ResponseEntityExceptionHandler`: `ApiException` → `ProblemDetail` con su status y `detail` (y header `Retry-After` si trae segundos); `MethodArgumentNotValidException` → 400 `detail` `"Hay datos inválidos."` + propiedad `errors: [{field, message}]` (mensajes en español en las anotaciones); `HttpMessageNotReadableException` → 400 `"El cuerpo de la petición no es válido."`; `DataIntegrityViolationException` no capturada antes → 409 `"La operación entra en conflicto con datos existentes."`; cualquier otra excepción → 500 `"Ocurrió un error inesperado."` y log `ERROR` con stacktrace (nunca con la contraseña).
- `src/test/java/com/futbolmanager/auth/AuthIntegrationTest.java` — con `ApiTestClient`, cubre los 6 criterios.

**Done when**
- [ ] WHEN `POST /api/auth/register` recibe `username`, `displayName` y `password` válidos con token CSRF THE SYSTEM SHALL responder 201 con `{id, username, displayName}`, guardar un hash BCrypt que empieza con `$2` en vez de la contraseña y emitir una cookie `FM_SESSION` con la que `GET /api/auth/me` responde 200.
- [ ] WHEN `POST /api/auth/register` repite un username existente cambiando solo mayúsculas THE SYSTEM SHALL responder 409 con un ProblemDetail cuyo `detail` es `Ese nombre de usuario ya está en uso.` y no crear filas.
- [ ] WHEN `POST /api/auth/register` recibe un username de 2 caracteres y una contraseña de 7 THE SYSTEM SHALL responder 400 con un arreglo `errors` cuyos `field` incluyen `username` y `password`.
- [ ] WHEN `POST /api/auth/login` recibe una contraseña incorrecta THE SYSTEM SHALL responder 401 con `detail` `Usuario o contraseña incorrectos.`, y WHEN recibe la correcta THE SYSTEM SHALL responder 200 y una sesión con la que `GET /api/auth/me` devuelve ese usuario.
- [ ] WHEN llega `POST /api/auth/login` sin token CSRF THE SYSTEM SHALL responder 403.
- [ ] WHEN corre `POST /api/auth/logout` THE SYSTEM SHALL responder 204 y la cookie de sesión anterior SHALL recibir 401 en `GET /api/auth/me`.

**Verify**
```bash
./mvnw -q test -Dtest=AuthIntegrationTest   # expect: exit 0 — los 6 criterios
./mvnw -q verify                            # expect: exit 0 — gates de los pasos 1–3 siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "E1-T4: add accounts api with session and csrf"
git tag step-04-auth-api
```

---

#### Step 5 — Throttling de login y registro

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/auth/AuthRateLimiter.java` — `@Component` con `Clock` inyectado y `@Value("${fm.auth.register-per-ip-per-hour}") int registerLimit`. Estado en `ConcurrentHashMap`: por clave `lower(username) + "|" + ip` → `{failures, lastFailureAt, blockedUntil}`; por IP → `ArrayDeque<Instant>` de fallos (ventana deslizante 15 min); por IP → `ArrayDeque<Instant>` de registros (ventana 1 h). API: `Optional<Duration> loginBlockedFor(username, ip)`, `void recordLoginFailure(username, ip)`, `void recordLoginSuccess(username, ip)`, `Optional<Duration> registerBlockedFor(ip)` (que también registra el intento cuando no está bloqueado). Reglas exactas de §5 (*POST /api/auth/login*): reinicio del contador si pasaron > 15 min desde el último fallo; al 5.º fallo bloqueo de `30 s × 2^(fallos − 5)` con tope 900 s; 50 fallos por IP en 15 min → bloqueo de IP. Métodos `synchronized` por clave (o `compute`) para que dos requests concurrentes no pierdan fallos.
- `src/main/java/com/futbolmanager/auth/AuthController.java` (editar) — login: primero `loginBlockedFor` → `ApiException.tooManyRequests("Demasiados intentos fallidos. Probá de nuevo en N segundos.", N)` con N = segundos restantes redondeados hacia arriba; luego verifica; registra fallo o éxito. Registro: `registerBlockedFor` → 429 `"Demasiados registros desde esta conexión. Probá de nuevo en N segundos."`. IP = `request.getRemoteAddr()` (ya resuelto por `server.forward-headers-strategy: native` en el perfil `prod`; en local es `none` y `X-Forwarded-For` se ignora).
- `src/test/java/com/futbolmanager/auth/AuthRateLimiterTest.java` — JUnit puro con un `Clock` falso mutable (subclase de `Clock` con `advance(Duration)`).
- `src/test/java/com/futbolmanager/auth/AuthThrottleIntegrationTest.java` — `@SpringBootTest(properties = "fm.auth.register-per-ip-per-hour=10")` + Testcontainers; usa `ApiTestClient.fromIp(...)`.

**Done when**
- [ ] WHEN corre `AuthRateLimiterTest` con un `Clock` falso THE SYSTEM SHALL comprobar que 4 fallos consecutivos de una clave (usuario, IP) no bloquean, que el 5.º bloquea 30 s, el 6.º 60 s y el 7.º 120 s, y que el bloqueo nunca supera 900 s.
- [ ] WHEN una clave (usuario, IP) está bloqueada THE SYSTEM SHALL responder 429 a `POST /api/auth/login` con un header `Retry-After` en segundos enteros sin verificar la contraseña.
- [ ] WHEN una IP acumula 50 logins fallidos en 15 minutos con usuarios distintos THE SYSTEM SHALL responder 429 a cualquier login desde esa IP hasta que el fallo más viejo salga de la ventana.
- [ ] WHEN un login es correcto THE SYSTEM SHALL resetear a cero los fallos de esa clave (usuario, IP).
- [ ] WHEN el usuario `u` está bloqueado desde la IP `10.0.0.1` THE SYSTEM SHALL igualmente responder 200 a la contraseña correcta de `u` desde la IP `10.0.0.2`.
- [ ] WHEN una IP envía su registro número 11 dentro de una hora con `fm.auth.register-per-ip-per-hour=10` THE SYSTEM SHALL responder 429 con `Retry-After`.

**Verify**
```bash
./mvnw -q test -Dtest=AuthRateLimiterTest           # expect: exit 0 — criterios 1, 3 y 4 (reloj falso)
./mvnw -q test -Dtest=AuthThrottleIntegrationTest   # expect: exit 0 — criterios 2, 5 y 6
./mvnw -q verify                                    # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E1-T5: throttle login and registration attempts"
git tag step-05-login-throttling
```

---

#### Step 6 — Pantallas de login y registro

**Do**
Crear/editar:
- `frontend/src/lib/api.ts` — `apiFetch<T>(path, {method, body})`: `credentials: 'same-origin'`, JSON; en métodos no-GET lee la cookie `XSRF-TOKEN` (si falta, llama a `GET /api/auth/csrf` una vez) y manda `X-XSRF-TOKEN`; si la respuesta no es 2xx lanza `ApiError {status, detail, errors?: {field, message}[], retryAfterSeconds?: number}` a partir del ProblemDetail y del header `Retry-After`; 204 → `undefined`.
- `frontend/src/features/auth/session.tsx` — `useMe()` (query `['me']`, 401 → `null`), `RequireAuth` (esqueleto mientras carga; sin sesión → `<Navigate to={'/ingresar?next=' + encodeURIComponent(pathname + search)} replace />`; con sesión → `<AppShell actions={<UserMenu />} />`), `UserMenu` (nombre visible + botón "Salir" → `POST /api/auth/logout`, `queryClient.clear()`, navega a `/ingresar`).
- `frontend/src/features/auth/AuthPages.tsx` — `LoginPage` y `RegisterPage` con `Field`/`Button`/`Alert` del paso 3: `h1` "Ingresar"/"Crear cuenta", `autocomplete` correctos, validación al enviar (usuario 3–30, contraseña 8–72 en registro) con mensajes en español, foco al primer campo inválido, resumen en `role="alert"`, errores del servidor por campo (`errors[].field`), 401 → "Usuario o contraseña incorrectos.", 429 → `detail` del servidor (contiene "N segundos"), 409 en el campo usuario; éxito → `next` (solo rutas que empiezan con `/`) o `/grupos`. Links cruzados "¿No tenés cuenta? Creá una" / "¿Ya tenés cuenta? Ingresá".
- `frontend/src/app/router.tsx` (editar) — `/ingresar` y `/registro` públicas; `/` y `/grupos` dentro de `RequireAuth`.
- `frontend/src/features/auth/AuthPages.test.tsx` — `fetch` simulado con `vi.stubGlobal`; cubre los criterios 1–5.

**Done when**
- [ ] WHEN `AuthPages.test.tsx` envía el formulario de login con el usuario vacío THE SYSTEM SHALL mostrar un error en español junto al campo, poner `aria-invalid="true"`, enlazarlo con `aria-describedby`, mover el foco al primer campo inválido y escribir un resumen en una región `role="alert"`.
- [ ] WHEN la API simulada responde 401 a `POST /api/auth/login` THE SYSTEM SHALL mostrar `Usuario o contraseña incorrectos.` y conservar el usuario tipeado.
- [ ] WHEN la API simulada responde 429 con `Retry-After: 30` THE SYSTEM SHALL mostrar un mensaje que contiene `30 segundos`.
- [ ] WHEN `apiFetch` envía una petición no-GET THE SYSTEM SHALL mandar el header `X-XSRF-TOKEN` igual a la cookie `XSRF-TOKEN`, llamando antes a `GET /api/auth/csrf` si la cookie no existe.
- [ ] WHEN un usuario sin sesión abre `/grupos` THE SYSTEM SHALL redirigir a `/ingresar?next=%2Fgrupos`.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint           # expect: exit 0
npm --prefix frontend run typecheck      # expect: exit 0
npm --prefix frontend run test           # expect: exit 0 — incluye AuthPages.test.tsx y AppShell.test.tsx
npm --prefix frontend run build          # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E1-T6: build accessible login and register screens"
git tag step-06-auth-ui
```

---

#### Step 7 — API de grupos, invitación y miembros

**Do**
Crear:
- `src/main/java/com/futbolmanager/group/FriendGroup.java` — entidad mapeada a `groups` (`@Entity(name = "FriendGroup") @Table(name = "groups")`); `Repo` anidado con `Optional<FriendGroup> findByInviteCode(String)` y `boolean existsByInviteCode(String)`. Método estático `newInviteCode(SecureRandom)`: 8 caracteres del alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789`; el controller reintenta si `existsByInviteCode`.
- `src/main/java/com/futbolmanager/group/Membership.java` — entidad `memberships` con `enum Role { ADMIN, MEMBER }`; `Repo` anidado con `findByGroupIdAndUserId`, `findAllByGroupId`, `findAllByUserId`, `long countByGroupIdAndRole(UUID, Role)`.
- `src/main/java/com/futbolmanager/group/GroupAccess.java` — `@Component`: `Membership requireMember(UUID groupId, AuthUser user)` → `ApiException.notFound("Grupo no encontrado.")` si no hay membresía; `Membership requireAdmin(UUID groupId, AuthUser user)` → miembro sin rol ADMIN → `ApiException.forbidden("Solo un administrador del grupo puede hacer esto.")`.
- `src/main/java/com/futbolmanager/group/GroupController.java` — rutas de grupos y miembros de §5 (`GET/POST /api/groups`, `POST /api/groups/join`, `GET/PATCH /api/groups/{groupId}`, `POST /api/groups/{groupId}/invite-code`, `GET /api/groups/{groupId}/members`, `PATCH/DELETE /api/groups/{groupId}/members/{userId}`), DTOs como records anidados, `@Transactional`. Regla del último admin: degradar o quitar a un ADMIN cuando `countByGroupIdAndRole(groupId, ADMIN) == 1` → `ApiException.conflict("El grupo tiene que tener al menos un administrador.")`. `join` con código inexistente → 404 `"Código de invitación inválido."`; ya miembro → 200 con el mismo `groupId`. Publica eventos de dominio (records anidados públicos) con `ApplicationEventPublisher`: `MemberJoined(UUID groupId, UUID userId, String displayName)` al crear el grupo (para el creador) y al unirse, y `MemberRemoved(UUID groupId, UUID userId)` al quitar a alguien; el paso 8 los escucha. `GET /api/groups/{groupId}` devuelve `{id, name, inviteCode, membersCanScore, myRole, myUserId}`; el frontend identifica "mi jugador" buscando en `GET /api/groups/{groupId}/players` el que tiene `userId` = `myUserId` (así este archivo no se edita en el paso 8).
- `src/test/java/com/futbolmanager/group/GroupIntegrationTest.java` — dos o tres `ApiTestClient` (uno por usuario); cubre los 6 criterios.

**Done when**
- [ ] WHEN un usuario autenticado hace `POST /api/groups` con `{name}` THE SYSTEM SHALL responder 201 con un `inviteCode` que cumple `^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$` y dejar al creador como `ADMIN`.
- [ ] WHEN otro usuario hace `POST /api/groups/join` con ese código THE SYSTEM SHALL crear exactamente una membresía `MEMBER`, y una segunda unión SHALL responder 200 dejando una sola membresía.
- [ ] WHEN un usuario que no es miembro pide `GET /api/groups/{groupId}`, `GET /api/groups/{groupId}/members`, `PATCH /api/groups/{groupId}` o `DELETE /api/groups/{groupId}/members/{userId}` de ese grupo THE SYSTEM SHALL responder 404 en todos, nunca 403.
- [ ] WHEN un `MEMBER` llama a `PATCH /api/groups/{groupId}`, `POST /api/groups/{groupId}/invite-code` o `PATCH /api/groups/{groupId}/members/{userId}` THE SYSTEM SHALL responder 403.
- [ ] WHEN se degrada o se quita al único `ADMIN` THE SYSTEM SHALL responder 409 y dejar su rol sin cambios.
- [ ] WHEN un admin regenera el código THE SYSTEM SHALL devolver un código nuevo y unirse con el código viejo SHALL responder 404.

**Verify**
```bash
./mvnw -q test -Dtest=GroupIntegrationTest   # expect: exit 0
./mvnw -q verify                             # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E2-T1: add groups, invites and members api"
git tag step-07-groups-api
```

---

#### Step 8 — API de jugadores con ELO inicial

> **Cambio posterior:** el paso 17 (E4-T2) agrega `players.level` (V3), obligatorio al crear, editable solo por admin y oculto a los MEMBER. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear:
- `src/main/java/com/futbolmanager/player/Player.java` — entidad `players` con `enum Position { GK, DEF, MID, FWD }` y `enum Foot { RIGHT, LEFT, BOTH }`; `Repo` anidado con `findByIdAndGroupId`, `findAllByGroupIdAndDeletedAtIsNullOrderByNameAsc`, `findByGroupIdAndUserId`.
- `src/main/java/com/futbolmanager/player/PlayerService.java` — `@Service`: `Player create(groupId, userId|null, name, nickname, position, foot)` que, en la transacción del llamador, calcula el ELO inicial = `Math.round` del promedio de `current_elo` de los jugadores activos del grupo (1000 si no hay), guarda el jugador e inserta con `JdbcClient` la fila `elo_changes` (`reason 'INITIAL'`, `match_id NULL`, `elo_before = elo_after = inicial`, `delta 0`). `@EventListener` (síncrono, misma transacción) para `GroupController.MemberJoined`: si existe un jugador del grupo con ese `user_id`, lo restaura (`deleted_at = null`); si no, crea uno con `name = displayName`, `preferredPosition MID`, `dominantFoot RIGHT`. `@EventListener` para `GroupController.MemberRemoved`: soft-delete del jugador vinculado (conserva historial).
- `src/main/java/com/futbolmanager/player/PlayerController.java` — `GET/POST /api/groups/{groupId}/players`, `PATCH/DELETE /api/groups/{groupId}/players/{playerId}`. Request con `@NotBlank @Size(max=60) name`, `@Size(min=1,max=30) nickname` opcional, `@Pattern(regexp = "GK|DEF|MID|FWD") preferredPosition` y `@Pattern(regexp = "RIGHT|LEFT|BOTH") dominantFoot` como `String` (así un valor inválido da 400 con `errors[].field` y no un error de deserialización). Respuesta `{id, name, nickname, preferredPosition, dominantFoot, currentElo, userId}`. Crear/borrar: admin. Editar: admin o el usuario vinculado.
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` — cubre los 6 criterios; para el criterio 2 fija `current_elo = 1101` de un jugador con `JdbcClient` antes de crear el invitado.

**Done when**
- [ ] WHEN se crea un grupo THE SYSTEM SHALL auto-crear el jugador del creador con `currentElo` 1000 y una fila `elo_changes` con `reason` `INITIAL`, `match_id` null, `elo_before` = `elo_after` = 1000 y `delta` = 0.
- [ ] WHEN un admin crea un jugador mientras los jugadores activos del grupo tienen ELO 1000 y 1101 THE SYSTEM SHALL asignarle `currentElo` 1051 y escribir su fila `INITIAL` con ese valor.
- [ ] WHEN un usuario se une a un grupo THE SYSTEM SHALL crear exactamente un jugador vinculado a ese usuario con `name` igual a su `displayName`, `preferredPosition` `MID` y `dominantFoot` `RIGHT`, y WHEN vuelve a unirse después de haber sido quitado THE SYSTEM SHALL restaurar ese mismo jugador en vez de crear otro.
- [ ] WHEN un admin borra un jugador THE SYSTEM SHALL poner `deleted_at`, conservar sus filas de `elo_changes` y omitirlo de `GET /api/groups/{groupId}/players`.
- [ ] WHEN un `MEMBER` crea un jugador THE SYSTEM SHALL responder 403, y WHEN alguien que no es miembro lista los jugadores THE SYSTEM SHALL responder 404.
- [ ] WHEN el body trae `preferredPosition` fuera de `GK`, `DEF`, `MID`, `FWD` THE SYSTEM SHALL responder 400 con un error en el campo `preferredPosition`.

**Verify**
```bash
./mvnw -q test -Dtest=PlayerIntegrationTest   # expect: exit 0
./mvnw -q verify                              # expect: exit 0 — GroupIntegrationTest sigue verde con los listeners activos
```

**Checkpoint**
```bash
git add -A && git commit -m "E2-T2: add players api with frozen initial elo"
git tag step-08-players-api
```

---

#### Step 9 — Pantallas de grupos, miembros y jugadores

> **Cambio posterior:** el paso 20 (E4-T5) agrega el campo «Nivel» solo para admin. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear/editar:
- `frontend/src/features/groups/api.ts` — hooks TanStack Query: `useGroups`, `useCreateGroup`, `useJoinGroup`, `useGroup(groupId)`, `useUpdateGroup`, `useRegenerateCode`, `useMembers`, `useChangeRole`, `useRemoveMember`, `usePlayers`, `useCreatePlayer`, `useUpdatePlayer`, `useDeletePlayer`; claves de §6; cada mutación invalida `['groups', groupId]` (o `['groups']`).
- `frontend/src/features/groups/GroupsPages.tsx` — `GroupsPage` (`h1` "Mis grupos"; lista con esqueleto / vacío "Todavía no tenés grupos" con acciones "Crear grupo" y "Unirme con código" / error "No pudimos cargar tus grupos." + "Reintentar"; formulario crear → navega a `/grupos/{id}`; formulario unirse con código) y `JoinByLinkPage` (`/unirse/:code`: hace la unión al montar y navega al grupo; 404 → "Código de invitación inválido.").
- `frontend/src/features/groups/GroupDetailPage.tsx` — `h1` con el nombre del grupo; pestañas por `?tab=jugadores|miembros|ajustes`; panel jugadores (lista con ELO en `font-mono tabular-nums`, puesto ARQ/DEF/MED/DEL, link al perfil `/grupos/{groupId}/jugadores/{playerId}`; admin: "Agregar jugador", editar, borrar con confirmación); panel miembros (rol; admin: "Cambiar rol", "Quitar"; cualquiera: "Salir del grupo"); panel ajustes (admin: nombre, `membersCanScore`, código + "Copiar link" + "Regenerar código"); links "Partidos" (`/grupos/{groupId}/partidos`) y "Ranking" (`/grupos/{groupId}/ranking`). Mensajes 403/404/409 del servidor en `Alert`.
- `frontend/src/app/router.tsx` (editar) — `/grupos` → `GroupsPage`, `/unirse/:code` → `JoinByLinkPage`, `/grupos/:groupId` → `GroupDetailPage` (todas dentro de `RequireAuth`).
- `frontend/src/features/groups/GroupsPages.test.tsx` — `fetch` simulado; cubre los criterios 1–5.

**Done when**
- [ ] WHEN la API simulada de grupos devuelve `[]` THE SYSTEM SHALL mostrar `Todavía no tenés grupos` con las acciones `Crear grupo` y `Unirme con código`.
- [ ] WHEN se envía el formulario de crear grupo THE SYSTEM SHALL hacer `POST /api/groups` con `{name}` y navegar a `/grupos/{id}`.
- [ ] WHEN un usuario autenticado abre `/unirse/ABCD2345` THE SYSTEM SHALL hacer `POST /api/groups/join` con `{"inviteCode":"ABCD2345"}` y navegar a `/grupos/{groupId}`.
- [ ] WHEN el detalle de grupo se muestra para un `MEMBER` THE SYSTEM SHALL NOT mostrar los controles de admin `Agregar jugador`, `Cambiar rol` ni `Regenerar código`.
- [ ] WHEN falla la petición de la lista de grupos THE SYSTEM SHALL mostrar `No pudimos cargar tus grupos.` y un botón `Reintentar` que repite la petición.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint           # expect: exit 0
npm --prefix frontend run typecheck      # expect: exit 0
npm --prefix frontend run test           # expect: exit 0 — GroupsPages, AuthPages y AppShell
npm --prefix frontend run build          # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E2-T3: build groups, members and players screens"
git tag step-09-groups-ui
```

---

#### Step 10 — Calculadora ELO pura

> **Cambio posterior:** el paso 16 (E4-T1) quita los puestos del cálculo: atajada de cualquier jugador, jugadón y valla invicta +2 para todo el equipo. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear:
- `src/main/java/com/futbolmanager/elo/EloCalculator.java` — clase final sin dependencias de Spring/JPA. Entrada (records anidados): `MatchInput(List<PlayerInput> teamA, List<PlayerInput> teamB, int scoreA, int scoreB, UUID mvpPlayerId)` y `PlayerInput(UUID playerId, int eloBefore, int previousCountedMatches, Position position, int goals, int assists, int saves, int tackles)` con `enum Position { GK, DEF, MID, FWD }` propio (no importa la entidad). Salida: `List<PlayerResult(UUID playerId, int teamDelta, int bonus, BonusBreakdown breakdown)>` con `BonusBreakdown(int goals, int assists, int mvp, int cleanSheet, int saves, int tackles, int uncapped, int total)` en puntos. Implementa exactamente las fórmulas de §5 (*ELO*).
- `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` — JUnit 5 puro (sin contexto Spring), un test por regla.

**Done when**
- [ ] WHEN dos equipos promedian 1000 y gana el equipo A con todos sus jugadores en K=40 THE SYSTEM SHALL dar `teamDelta` +20 a cada jugador de A y −20 a cada jugador de B, y un empate SHALL dar 0.
- [ ] WHEN un equipo con promedio 1000 juega contra uno con promedio 1400 THE SYSTEM SHALL dar al lado de 1000 un `teamDelta` de +36 (K=40) o +22 (K=24) si gana, −4 (K=40) o −2 (K=24) si pierde, y +16 (K=40) si empata.
- [ ] WHEN un jugador tiene 9 partidos contados previos THE SYSTEM SHALL usar K=40, y WHEN tiene 10 THE SYSTEM SHALL usar K=24.
- [ ] WHEN un jugador hace 4 goles, da 2 asistencias y es MVP THE SYSTEM SHALL dar `bonus` 10 (tope total), WHEN el GK por partido registra 5 atajadas THE SYSTEM SHALL sumar 3 por atajadas, WHEN un jugador registra 4 cortes THE SYSTEM SHALL sumar 3 por cortes, y el `bonus` nunca SHALL ser negativo.
- [ ] WHEN el rival hace 0 goles THE SYSTEM SHALL sumar 3 de valla invicta a los jugadores con puesto por partido `GK` o `DEF` y 0 a `MID` y `FWD`, también en un equipo sin `GK` asignado.
- [ ] WHEN los equipos tienen 5 y 6 jugadores THE SYSTEM SHALL usar el promedio de cada equipo, y WHEN recibe dos veces la misma entrada THE SYSTEM SHALL devolver resultados iguales.

**Verify**
```bash
./mvnw -q test -Dtest=EloCalculatorTest   # expect: exit 0
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/elo/EloCalculator.java || status=$?; test "$status" -eq 1
                                          # expect: exit 0 — 1 = sin imports prohibidos · 2 = archivo ausente, y eso falla
./mvnw -q verify                          # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E2-T4: implement the pure elo calculator"
git tag step-10-elo-calculator
```

---

#### Step 11 — Balanceador de equipos puro

> **Cambio posterior:** el paso 18 (E4-T3) lo reemplaza por un balanceo por nivel con ELO de desempate y elimina `oneGoalkeeperPerSide`. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear:
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` — clase final sin Spring/JPA. `Result balance(List<Candidate> candidates, boolean oneGoalkeeperPerSide)` con `Candidate(UUID id, int elo, Position preferredPosition)` y `Result(List<Assignment> teamA, List<Assignment> teamB, double avgEloA, double avgEloB, double eloDifference, boolean goalkeeperConstraintApplied)`, `Assignment(UUID playerId, Position position)`, `enum Position { GK, DEF, MID, FWD }` propio. Menos de 2 candidatos o más de 22 → `IllegalArgumentException`. Algoritmo exacto de §5 (*Balanceo*): enumeración de subconjuntos con el primer id fijo en A (bitmask o combinaciones iterativas, sin recursión profunda), comparación con fracciones exactas en `long`, desempate lexicográfico por ids de A.
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` — JUnit 5 puro; incluye un oráculo de fuerza bruta independiente para n ≤ 10 y un test de rendimiento con `assertTimeoutPreemptively(Duration.ofSeconds(2), …)` para 22 jugadores.

**Done when**
- [ ] WHEN se balancean 10 jugadores THE SYSTEM SHALL devolver equipos de 5 y 5 cuya diferencia de promedios es igual al mínimo que encuentra el oráculo de fuerza bruta del test.
- [ ] WHEN se balancean 2, 3 u 11 jugadores THE SYSTEM SHALL devolver tamaños 1 y 1, 2 y 1, y 6 y 5 respectivamente (en algún orden).
- [ ] WHEN `oneGoalkeeperPerSide` es true y hay 2 o más jugadores con puesto preferido `GK` THE SYSTEM SHALL dejar al menos uno por lado, asignar `GK` a exactamente uno por lado y devolver `goalkeeperConstraintApplied` true, y WHEN hay solo 1 THE SYSTEM SHALL ignorar la opción y devolver `goalkeeperConstraintApplied` false.
- [ ] WHEN recibe la misma entrada dos veces, en distinto orden THE SYSTEM SHALL devolver la misma partición, con el jugador de menor id en el equipo A.
- [ ] WHEN recibe 23 jugadores THE SYSTEM SHALL lanzar `IllegalArgumentException`.
- [ ] WHEN recibe 22 jugadores THE SYSTEM SHALL terminar en menos de 2 segundos.

**Verify**
```bash
./mvnw -q test -Dtest=TeamBalancerTest   # expect: exit 0
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/match/balance/TeamBalancer.java || status=$?; test "$status" -eq 1
                                         # expect: exit 0 — 1 = sin imports prohibidos · 2 = archivo ausente, y eso falla
./mvnw -q verify                         # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E2-T5: implement the pure exhaustive team balancer"
git tag step-11-team-balancer
```

---

#### Step 12 — API de partidos, convocatoria, balance y lineup

> **Cambio posterior:** el paso 18 (E4-T3) cambia `balance` (sin cuerpo; nivel promedio y diferencia de nivel). Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear:
- `src/main/java/com/futbolmanager/match/Match.java` — entidad `matches` con `enum Status { SCHEDULED, LIVE, CLOSED, ANNULLED }`, `@Version long version`; `Repo` con `findByIdAndGroupId`, `findTop200ByGroupIdOrderByPlayedAtDescIdDesc`.
- `src/main/java/com/futbolmanager/match/MatchCallUp.java` — entidad `match_call_ups` con `enum Status { CONFIRMED, MAYBE, NOT_GOING }`; `Repo` con `findAllByMatchId`, `findByMatchIdAndPlayerId`.
- `src/main/java/com/futbolmanager/match/LineupEntry.java` — entidad `match_lineup_entries` con `team` (`A`/`B`) y `position`; `Repo` con `findAllByMatchId`, `deleteAllByMatchId`.
- `src/main/java/com/futbolmanager/match/MatchController.java` — rutas de partidos de §5 hasta `start` inclusive. `POST`: `@NotNull playedAt` (`Instant`), `@NotBlank @Size(max=120) location`, `format` validado con `@Pattern`/lista `5|7|8|11` → 400 en el campo `format`. Convocatoria: admin cualquier jugador; MEMBER solo el jugador con su `userId` (si no → 403); jugador de otro grupo o borrado → 422. `balance`: toma los `CONFIRMED` no borrados, mapea a `TeamBalancer.Candidate` con su `current_elo`, `IllegalArgumentException` → 400 `"El armado automático admite hasta 22 jugadores."` (o `"Necesitás al menos 2 confirmados para armar equipos."`); no escribe nada. `lineup` (solo `SCHEDULED`): reemplaza todas las entradas en una transacción; un `playerId` de otro grupo, borrado o repetido → 422 sin tocar el lineup previo. `start`: `SCHEDULED` con ≥ 1 jugador en A y en B → `LIVE`; si no → 409 `"Guardá los equipos antes de empezar."`; desde otro estado → 409. Detalle `GET` incluye `callUps` y `lineup` con nombre y ELO de cada jugador.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` — cubre los 6 criterios.

**Done when**
- [ ] WHEN un admin hace `POST /api/groups/{groupId}/matches` con `{playedAt, location, format: 7}` THE SYSTEM SHALL responder 201 con `status` `SCHEDULED`, y WHEN `format` es 6 THE SYSTEM SHALL responder 400 con un error en el campo `format`.
- [ ] WHEN un `MEMBER` pone en `CONFIRMED` la convocatoria de su propio jugador vinculado THE SYSTEM SHALL responder 200, y WHEN intenta cambiar la de otro jugador THE SYSTEM SHALL responder 403.
- [ ] WHEN un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` con 10 confirmados THE SYSTEM SHALL devolver dos equipos de 5 con `eloDifference` y `position` por jugador sin escribir filas en `match_lineup_entries`, y WHEN hay 23 confirmados THE SYSTEM SHALL responder 400.
- [ ] WHEN un admin guarda un lineup que incluye un jugador de otro grupo o un jugador borrado THE SYSTEM SHALL responder 422 y conservar el lineup anterior.
- [ ] WHEN se llama a `POST /api/groups/{groupId}/matches/{matchId}/start` sin un lineup guardado con al menos un jugador por equipo THE SYSTEM SHALL responder 409, WHEN lo tiene THE SYSTEM SHALL responder 200 con `status` `LIVE`, y un segundo `start` SHALL responder 409.
- [ ] WHEN alguien que no es miembro pide `GET /api/groups/{groupId}/matches/{matchId}` THE SYSTEM SHALL responder 404.

**Verify**
```bash
./mvnw -q test -Dtest=MatchIntegrationTest   # expect: exit 0
./mvnw -q verify                             # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E2-T6: add matches, call-ups, balance and lineup api"
git tag step-12-matches-api
```

---

#### Step 13 — Pantallas de partido, convocatoria y armado

> **Cambio posterior:** el paso 24 (E4-T6) quita «Un arquero por equipo» y muestra el nivel promedio (solo admin). Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear/editar:
- `frontend/src/features/matches/api.ts` — hooks: `useMatches`, `useCreateMatch`, `useMatch`, `useSetCallUp`, `useBalance` (mutación que devuelve la propuesta, no invalida nada), `useSaveLineup`, `useStartMatch`; claves de §6.
- `frontend/src/features/matches/MatchesPages.tsx` — `MatchesPage` (`h1` "Partidos"; lista con fecha local `es-AR`, lugar, formato y estado; esqueleto/vacío "No hay partidos todavía." + (admin) "Crear partido"/error + "Reintentar"; formulario de alta con `datetime-local` convertido a ISO-8601 con zona (`new Date(value).toISOString()`), lugar y formato 5/7/8/11).
- `frontend/src/features/matches/MatchSetupPage.tsx` — `h1` con fecha y lugar; convocatoria (un `select` "Asistencia de {nombre}" por jugador; un MEMBER solo edita el suyo); `TeamBuilder` para admin: checkbox "Un arquero por equipo", botón "Armar equipos" → muestra Equipo A / Equipo B con ELO promedio y "Diferencia: N"; por jugador un botón "Pasar al equipo B"/"Pasar al equipo A" (alternativa al arrastre, WCAG 2.5.7) y un `select` de puesto con etiqueta accesible "Puesto de {nombre}" (ARQ/DEF/MED/DEL); "Guardar equipos" → `PUT lineup`; "Empezar partido" (habilitado con lineup guardado) → `POST start` → navega a `/grupos/{groupId}/partidos/{matchId}/en-vivo`. Si el partido ya no está `SCHEDULED`, muestra link al modo en vivo.
- `frontend/src/app/router.tsx` (editar) — `/grupos/:groupId/partidos` → `MatchesPage`, `/grupos/:groupId/partidos/:matchId` → `MatchSetupPage`.
- `frontend/src/features/matches/MatchSetupPage.test.tsx` — `fetch` simulado; cubre los criterios 1–5 (el 1 y el 2 renderizan `MatchesPage`).

**Done when**
- [ ] WHEN la API simulada de partidos devuelve `[]` para un admin THE SYSTEM SHALL mostrar `No hay partidos todavía.` y la acción `Crear partido`.
- [ ] WHEN el admin envía el formulario de crear partido THE SYSTEM SHALL hacer `POST /api/groups/{groupId}/matches` con `playedAt` en ISO-8601, `location` y `format`, y navegar a `/grupos/{groupId}/partidos/{matchId}`.
- [ ] WHEN se activa `Armar equipos` THE SYSTEM SHALL llamar a `POST /api/groups/{groupId}/matches/{matchId}/balance` y mostrar ambos equipos con su ELO promedio y la diferencia.
- [ ] WHEN se activa con el teclado el botón `Pasar al equipo B` de un jugador THE SYSTEM SHALL moverlo de equipo sin arrastrar, y cada select de puesto SHALL tener un nombre accesible que incluye el nombre del jugador.
- [ ] WHEN se activa `Guardar equipos` THE SYSTEM SHALL hacer `PUT /api/groups/{groupId}/matches/{matchId}/lineup` con una entrada `{playerId, team, position}` por jugador.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint           # expect: exit 0
npm --prefix frontend run typecheck      # expect: exit 0
npm --prefix frontend run test           # expect: exit 0
npm --prefix frontend run build          # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E3-T1: build match, call-up and team builder screens"
git tag step-13-match-setup-ui
```

---

#### Step 14 — API de eventos en vivo

> **Cambio posterior:** el paso 19 (E4-T4) agrega el tipo `PLAY` (Jugadón) y permite atajadas de cualquier jugador (V4). Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear:
- `src/main/java/com/futbolmanager/live/MatchEvent.java` — entidad `match_events` con `@Id UUID id` **asignado por el cliente** e implementando `Persistable<UUID>` (`isNew()` true hasta persistir) para que `save` haga `INSERT` sin `SELECT` previo; `enum Type { GOAL, SAVE, TACKLE }`; sin `updated_at`. `Repo` con `findAllByMatchIdOrderByCreatedAtAsc`, `findById`.
- `src/main/java/com/futbolmanager/live/LiveController.java` — `GET /api/groups/{groupId}/matches/{matchId}/live` → `{matchId, status, scoreA, scoreB, canRecord, isAdmin, myUserId, events:[{id, type, team, playerId, playerName, assistPlayerId, assistPlayerName, recordedBy:{userId, displayName}, createdAt, voidedAt}]}` con el marcador **derivado** de los GOAL no anulados. `POST …/events` y `POST …/events/{eventId}/void` con las reglas exactas de §5 (*POST /api/groups/{groupId}/matches/{matchId}/events*): permisos, idempotencia (200 mismo payload / 409 distinto), validaciones 422, 409 en `SCHEDULED`/`ANNULLED`. Anular: autor o admin en `LIVE`, solo admin en `CLOSED`; ya anulado → 200 sin cambios. En este paso, los eventos en `CLOSED` **no** existen todavía (cerrar llega en el paso 26).
- `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` — arma grupo, jugadores, partido, lineup y `start` por API; cubre los 6 criterios.

**Done when**
- [ ] WHEN un admin envía un `GOAL` con un `id` UUID nuevo a un partido `LIVE` THE SYSTEM SHALL responder 201 y sumar 1 al marcador de ese equipo en `GET /api/groups/{groupId}/matches/{matchId}/live`; WHEN reenvía el mismo `id` con el mismo payload THE SYSTEM SHALL responder 200 sin cambiar el marcador; WHEN reenvía el mismo `id` con otro payload THE SYSTEM SHALL responder 409.
- [ ] WHEN se envía un `SAVE` de un jugador que no es el `GK` por partido de ese equipo, o un `TACKLE` de un jugador que no está en el lineup de ese equipo THE SYSTEM SHALL responder 422.
- [ ] WHEN un `MEMBER` envía un evento con `membersCanScore` false THE SYSTEM SHALL responder 403, y WHEN `membersCanScore` es true THE SYSTEM SHALL responder 201.
- [ ] WHEN el autor anula su evento THE SYSTEM SHALL responder 200 y descontarlo del marcador; WHEN otro `MEMBER` intenta anularlo THE SYSTEM SHALL responder 403; WHEN un admin anula cualquier evento THE SYSTEM SHALL responder 200.
- [ ] WHEN se envía un evento a un partido `SCHEDULED` THE SYSTEM SHALL responder 409.
- [ ] WHEN se pide el estado en vivo THE SYSTEM SHALL devolver los eventos en orden de `createdAt`, cada uno con `recordedBy.displayName`, y `canRecord` true solo para admins o para miembros cuando `membersCanScore` es true.

**Verify**
```bash
./mvnw -q test -Dtest=LiveIntegrationTest   # expect: exit 0
./mvnw -q verify                            # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E3-T2: add idempotent live events api"
git tag step-14-live-api
```

---

#### Step 15 — Modo en vivo mobile-first

> **Cambio posterior:** el paso 25 (E4-T7) agrega `Jugadón A/B`, quita la preselección del arquero y arma los diálogos con `DialogShell`. El diálogo de este paso ya se corrigió para quedar por encima del marcador (`z-40`/`z-50` sobre `z-10`). Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear/editar:
- `frontend/src/features/live/api.ts` — `useLive(groupId, matchId)` con `refetchInterval: 5000` y `refetchIntervalInBackground: false`; `useRecordEvent` (genera `id = crypto.randomUUID()` una sola vez por toque y lo reutiliza en hasta 3 reintentos ante error de red); `useVoidEvent`. Ambas mutaciones invalidan la clave `live`.
- `frontend/src/features/live/LivePage.tsx` — `h1` "En vivo" (o "Resumen" si `CLOSED`); `Scoreboard` pegado arriba (`sticky top-0`, `role="status"`, números `font-mono tabular-nums`, equipos A/B; el contenedor de la página usa `scroll-pt-28` para que el foco nunca quede tapado, WCAG 2.4.11); si `canRecord`: botones `Button size="live"` (`min-h-16`): "Gol A", "Gol B", "Atajada A", "Atajada B", "Corte A", "Corte B"; un diálogo (`radix-ui` Dialog) para elegir autor (o "En contra / no sé") y asistencia opcional en goles, el arquero se preselecciona en atajadas; "Deshacer" anula mi último evento no anulado; lista de eventos (más nuevos primero) con "cargado por {displayName}" y "Anular" si soy el autor o admin; eventos anulados tachados con texto "anulado" (no solo color); banner "Sin conexión, reintentando…" mientras el polling falle; errores 403/409/422 en `Alert`.
- `frontend/src/app/router.tsx` (editar) — `/grupos/:groupId/partidos/:matchId/en-vivo` → `LivePage`.
- `frontend/src/features/live/LivePage.test.tsx` — `vi.useFakeTimers({ shouldAdvanceTime: true })` + `fetch` simulado; cubre los criterios 1–5.

**Done when**
- [ ] WHEN `LivePage.test.tsx` avanza los timers falsos 5000 ms THE SYSTEM SHALL pedir `GET /api/groups/{groupId}/matches/{matchId}/live` por segunda vez.
- [ ] WHEN se activa `Gol A` y se elige el autor THE SYSTEM SHALL hacer `POST /api/groups/{groupId}/matches/{matchId}/events` con `type` `GOAL`, `team` `A`, el `playerId` elegido y un `id` UUID, y reenviar el mismo `id` si el primer intento falla por red.
- [ ] WHEN se activa `Deshacer` THE SYSTEM SHALL hacer `POST /api/groups/{groupId}/matches/{matchId}/events/{eventId}/void` sobre el último evento no anulado cargado por el usuario actual.
- [ ] WHEN un `MEMBER` ve la lista de eventos THE SYSTEM SHALL mostrar `Anular` solo en sus propios eventos, y WHEN la ve un `ADMIN` THE SYSTEM SHALL mostrarlo en todos los no anulados.
- [ ] WHEN se renderiza el panel de registro THE SYSTEM SHALL dar a cada botón de registro la utilidad `min-h-16` y mostrar el marcador en una región `role="status"`.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

El tamaño real en píxeles de los botones no es observable en jsdom (no hay layout): este paso verifica la utilidad y el paso 28 mide el alto real con Playwright.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint           # expect: exit 0
npm --prefix frontend run typecheck      # expect: exit 0
npm --prefix frontend run test           # expect: exit 0
npm --prefix frontend run build          # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E3-T3: build mobile-first live mode"
git tag step-15-live-ui
```

---

> **Cambio de producto (aprobado después del paso 15).** Los pasos 16–25 aplican arqueros rotativos, jugadón y nivel de jugador (con un decimal desde el paso 21); son las tareas `E4-T1…E4-T10` del epic 04 y se ejecutan antes del paso 26. Los pasos 21–23 (`E4-T8…E4-T10`) se agregaron después de construir el paso 20 y van entre el 20 y el 24. Reemplazan el comportamiento descrito en los pasos 8–15 (marcados como "Cambio posterior").

#### Step 16 — Reglas de bonus ELO sin puestos

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/elo/EloCalculator.java` (editar) — se elimina `Position` y el campo `position` de `PlayerInput`, que pasa a `(playerId, eloBefore, previousCountedMatches, goals, assists, saves, tackles, plays)`. Bonus por jugador (nunca negativo): gol +2 c/u; asistencia +1 c/u; MVP +3; valla invicta +2 para **cada** jugador del equipo cuyo rival hizo 0 goles, sin importar el puesto; atajada +1 c/u de **cualquier** jugador, tope +3; corte +1 c/u, tope +3; jugadón +1 c/u, tope +2; **bonus total con tope +10**. `BonusBreakdown(goals, assists, mvp, cleanSheet, saves, tackles, plays, uncapped, total)`. Sigue puro: sin `org.springframework` ni `jakarta.persistence`.
- `src/test/java/com/futbolmanager/elo/EloCalculatorTest.java` (editar) — el helper de `PlayerInput` pierde el puesto y gana `plays`; se borran `ignoresSavesForPlayersThatAreNotTheMatchGoalkeeper` y `awardsCleanSheetToDefenderOnATeamWithoutGoalkeeper`; se reescriben `capsGoalkeeperSavesAtThree`, `awardsCleanSheetOnlyToGoalkeepersAndDefenders` y `addsCleanSheetAndCappedSavesForAGoalkeeper`; se agregan tests del tope de jugadones, de la valla para todo el equipo y de bonus nunca negativo con `plays`.

**Done when**
- [ ] WHEN un jugador de cualquier puesto registra 5 atajadas THE SYSTEM SHALL calcular `saves` 3 en su desglose (tope por jugador).
- [ ] WHEN un jugador registra 3 jugadones THE SYSTEM SHALL calcular `plays` 2, y con 1 jugadón `plays` 1.
- [ ] WHEN el rival hizo 0 goles THE SYSTEM SHALL calcular `cleanSheet` 2 para todos los jugadores del equipo sin importar el puesto, y WHEN el rival anotó THE SYSTEM SHALL calcular `cleanSheet` 0.
- [ ] WHEN goles, asistencias, MVP, valla, atajadas, cortes y jugadones suman más de 10 THE SYSTEM SHALL devolver `bonus` 10 con `uncapped` igual a la suma real, y el `bonus` nunca SHALL ser negativo, tampoco con contadores negativos.
- [ ] WHEN corre `grep` sobre `EloCalculator.java` THE SYSTEM SHALL no encontrar imports de `org.springframework` ni de `jakarta.persistence`.
- [ ] WHEN corren `EloCalculatorTest` y `./mvnw -q verify` THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=EloCalculatorTest   # expect: exit 0
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/elo/EloCalculator.java || status=$?; test "$status" -eq 1   # expect: exit 0 — sin imports prohibidos (grep sale con 1)
./mvnw -q verify   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T1: rework elo bonus rules"
git tag step-16-elo-bonus-rules
```

---

#### Step 17 — Nivel de jugador (solo admin)

> **Cambio posterior:** los pasos 21 y 22 (E4-T8, E4-T9) pasan el nivel a un decimal: V5 reemplaza `players.level` por `level_tenths` (décimas enteras) y la API acepta y devuelve `8.5`. El resto (solo admin, 403 para un MEMBER) sigue igual. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear/editar:
- `src/main/resources/db/migration/V3__player_level.sql` — `ALTER TABLE players ADD COLUMN level integer NOT NULL DEFAULT 5;` (los jugadores existentes quedan en 5), `ALTER TABLE players ADD CONSTRAINT ck_players_level CHECK (level BETWEEN 1 AND 10);` y `ALTER TABLE players ALTER COLUMN level DROP DEFAULT;` (a partir de acá el nivel lo exige la base). Se crea con la skill `add-migration`; V1 y V2 no se tocan.
- `src/main/java/com/futbolmanager/player/Player.java` — campo `level` (int, no nulo), parámetro en el constructor y getter/setter.
- `src/main/java/com/futbolmanager/player/PlayerService.java` — `create(...)` recibe `level`; el jugador que se crea al crear el grupo o al unirse (`onMemberJoined`) nace con nivel 5.
- `src/main/java/com/futbolmanager/player/PlayerController.java` — `CreatePlayerRequest.level` con `@NotNull @Min(1) @Max(10) Integer`; `UpdatePlayerRequest.level` con `@Min(1) @Max(10) Integer` (opcional); `PlayerResponse.level` es `Integer` y se omite del JSON cuando es null (`@JsonInclude(JsonInclude.Include.NON_NULL)`). **El nivel solo lo ven los ADMIN del grupo:** `GET`, `POST` y `PATCH` rellenan `level` solo si quien llama es ADMIN; para un MEMBER es null (se omite). `PATCH` con `level` de un MEMBER, incluso sobre su propio jugador → 403 `Solo un administrador puede cambiar el nivel.`.
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` — los altas mandan `level`; casos nuevos de falta de nivel, rango, permisos, visibilidad por rol, valor por defecto y CHECK de la base (insert directo con `JdbcClient`). En `MatchIntegrationTest` y `LiveIntegrationTest` solo se agrega `level` al alta de jugadores.

**Excepción deliberada a la regla de 5 archivos (7 archivos):** `MatchIntegrationTest` y `LiveIntegrationTest` solo cambian una línea cada uno (el alta de jugadores por API ahora exige `level`); no se pueden diferir sin dejar el gate de este paso en rojo.

**Done when**
- [ ] WHEN se envía `POST /api/groups/{groupId}/players` sin `level`, o con `level` 0 u 11, THE SYSTEM SHALL responder 400 con un error en el campo `level`, y WHEN se inserta un jugador con `level` fuera de 1–10 directamente en la base THE SYSTEM SHALL rechazarlo por el CHECK `ck_players_level`.
- [ ] WHEN un admin envía `PATCH` con un `level` válido THE SYSTEM SHALL responder 200 con el nuevo nivel, y WHEN un `MEMBER` envía `level`, incluso sobre su propio jugador, THE SYSTEM SHALL responder 403.
- [ ] WHEN un `MEMBER` pide `GET /api/groups/{groupId}/players` o edita su propio jugador con `PATCH` THE SYSTEM SHALL devolver jugadores sin el campo `level`, y WHEN lo pide un `ADMIN` THE SYSTEM SHALL incluir `level` en cada jugador.
- [ ] WHEN se crea el jugador del creador de un grupo o de quien se une THE SYSTEM SHALL asignarle nivel 5, y WHEN `V3__player_level.sql` se aplica THE SYSTEM SHALL dejar los jugadores existentes en nivel 5 (`ADD COLUMN level integer NOT NULL DEFAULT 5`).
- [ ] WHEN corren `PlayerIntegrationTest` y `./mvnw -q verify` THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=PlayerIntegrationTest   # expect: exit 0
grep -q 'ADD COLUMN level integer NOT NULL DEFAULT 5' src/main/resources/db/migration/V3__player_level.sql   # expect: exit 0 — el relleno de los existentes
./mvnw -q verify   # expect: exit 0 — Flyway aplica V1..V3 y Hibernate valida
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T2: add admin-only player level"
git tag step-17-player-level
```

---

#### Step 18 — Balanceo por nivel

> **Cambio posterior:** el paso 21 (E4-T8) hace que el balanceador reciba el nivel en décimas enteras (`Candidate(UUID id, int levelTenths, int elo)`, rango 10–100) y que `balance` devuelva `level` como número con un decimal. El objetivo y los desempates no cambian. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` (editar) — `Candidate(UUID id, int level, int elo)`; se eliminan `Position`, `Assignment`, `oneGoalkeeperPerSide` y `goalkeeperConstraintApplied`. `balance(List<Candidate>)` devuelve `Result(List<UUID> teamA, List<UUID> teamB, double avgLevelA, double avgLevelB, double levelDifference, double avgEloA, double avgEloB, double eloDifference)`. Mismo recorrido exhaustivo (A contiene al menor id, `|A| ∈ {⌊n/2⌋, ⌈n/2⌉}`); objetivo en este orden: (1) minimizar `|promNivelA − promNivelB|`, (2) ante empate exacto, minimizar `|promEloA − promEloB|`, (3) ante empate, la lista de ids de A lexicográficamente menor. Las comparaciones usan fracciones exactas con `long` (sin flotantes). Niveles fuera de 1–10 → `IllegalArgumentException`. Sigue puro.
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` (editar) — se borran los 4 tests de arquero; el oráculo de fuerza bruta compara la tupla (diferencia de nivel, diferencia de ELO); tests nuevos de nivel antes que ELO, ELO como desempate, validación del rango y desempate por ids.
- `src/main/java/com/futbolmanager/match/MatchController.java` (editar) — `POST …/balance` sin cuerpo (se elimina `BalanceRequest`); `BalanceResponse(teamA, teamB, avgLevelA, avgLevelB, levelDifference, avgEloA, avgEloB, eloDifference)` con `BalancedPlayer(playerId, name, currentElo, level, position)`; `position` es el puesto preferido del jugador (informativo). El endpoint es solo ADMIN, así que el nivel nunca llega a un MEMBER; ni el detalle del partido ni el lineup devuelven `level`.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` (editar) — el balance sin `oneGoalkeeperPerSide` y con el contrato nuevo; un MEMBER recibe 403 en `balance` y ni el detalle del partido ni el lineup que ve contienen `level`.

**Done when**
- [ ] WHEN `TeamBalancerTest` compara contra el oráculo de fuerza bruta (n ≤ 10) THE SYSTEM SHALL producir una partición con la mínima diferencia de nivel promedio y, entre las empatadas, la de menor diferencia de ELO promedio.
- [ ] WHEN dos particiones empatan en diferencia de nivel y de ELO THE SYSTEM SHALL elegir la que tiene la lista de ids de A lexicográficamente menor, con el menor id siempre en A.
- [ ] WHEN se balancean 22 jugadores THE SYSTEM SHALL terminar en menos de 2 segundos.
- [ ] WHEN un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` sin cuerpo THE SYSTEM SHALL responder cada jugador con `level` y `position` y los totales `avgLevelA`, `avgLevelB`, `levelDifference`, `avgEloA`, `avgEloB`, `eloDifference`, sin `goalkeeperConstraintApplied`.
- [ ] WHEN un `MEMBER` pide `balance` THE SYSTEM SHALL responder 403, y WHEN un `MEMBER` pide el detalle del partido o el lineup THE SYSTEM SHALL responder sin ningún campo `level`.
- [ ] WHEN corren `TeamBalancerTest`, `MatchIntegrationTest` y `./mvnw -q verify`, y `grep` no encuentra imports de `org.springframework` ni `jakarta.persistence` en `TeamBalancer.java`, THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=TeamBalancerTest   # expect: exit 0
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/match/balance/TeamBalancer.java || status=$?; test "$status" -eq 1   # expect: exit 0 — sin imports prohibidos (grep sale con 1)
./mvnw -q test -Dtest=MatchIntegrationTest   # expect: exit 0
./mvnw -q verify   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T3: balance teams by level"
git tag step-18-level-balancer
```

---

#### Step 19 — Jugadón y atajadas abiertas en la API

**Do**
Crear/editar:
- `src/main/resources/db/migration/V4__match_event_play.sql` — `ALTER TABLE match_events DROP CONSTRAINT ck_match_events_type;` y `ALTER TABLE match_events ADD CONSTRAINT ck_match_events_type CHECK (type IN ('GOAL', 'SAVE', 'TACKLE', 'PLAY'));`. Se crea con la skill `add-migration`.
- `src/main/java/com/futbolmanager/live/MatchEvent.java` (editar) — `enum Type { GOAL, SAVE, TACKLE, PLAY }`.
- `src/main/java/com/futbolmanager/live/LiveController.java` (editar) — `EventRequest.type` acepta `PLAY`. Reglas 422 nuevas: `SAVE`, `TACKLE` y `PLAY` exigen `playerId` del lineup de `team` (la atajada ya no exige ser el arquero: se elimina la comprobación de `position == GK`); `PLAY` no admite `assistPlayerId`. El resto (permisos, idempotencia, anulación, marcador derivado de los GOAL) no cambia; los `PLAY` no alteran el marcador.
- `src/test/java/com/futbolmanager/live/LiveIntegrationTest.java` (editar) — `saveMustComeFromThatTeamsGoalkeeperAndTackleFromItsLineup` se reescribe como `saveAndTackleAndPlayComeFromThatTeamsLineup`; casos nuevos de `PLAY` (201, idempotencia, 422, anulación, orden en el estado en vivo) y del CHECK de la base.

**Done when**
- [ ] WHEN un admin envía un `PLAY` con un jugador del lineup de `team` y un `id` UUID nuevo THE SYSTEM SHALL responder 201; WHEN reenvía el mismo `id` con el mismo payload THE SYSTEM SHALL responder 200 sin duplicar; WHEN reenvía el mismo `id` con otro payload THE SYSTEM SHALL responder 409.
- [ ] WHEN se envía un `PLAY` sin `playerId`, con un jugador fuera del lineup de `team` o con `assistPlayerId` THE SYSTEM SHALL responder 422.
- [ ] WHEN se envía un `SAVE` de cualquier jugador del lineup de `team`, sea arquero o de campo, THE SYSTEM SHALL responder 201, y WHEN el jugador falta o no está en ese lineup THE SYSTEM SHALL responder 422.
- [ ] WHEN se pide `GET …/live` con eventos `PLAY` THE SYSTEM SHALL incluirlos en orden de `createdAt` sin cambiar `scoreA` ni `scoreB`.
- [ ] WHEN se inserta en `match_events` un `type` distinto de `GOAL`, `SAVE`, `TACKLE` o `PLAY` THE SYSTEM SHALL rechazarlo por el CHECK `ck_match_events_type`, y `PLAY` SHALL ser aceptado.
- [ ] WHEN corren `LiveIntegrationTest` y `./mvnw -q verify` THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=LiveIntegrationTest   # expect: exit 0
grep -q "'GOAL', 'SAVE', 'TACKLE', 'PLAY'" src/main/resources/db/migration/V4__match_event_play.sql   # expect: exit 0 — el CHECK nuevo
./mvnw -q verify   # expect: exit 0 — Flyway aplica V1..V4 y Hibernate valida
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T4: add jugadon events and open saves"
git tag step-19-play-events
```

---

#### Step 20 — Nivel en las pantallas de jugadores

> **Cambio posterior:** el paso 23 (E4-T10) cambia el campo «Nivel» para aceptar un decimal con coma o punto (`8,5`) y mostrarlo con un decimal. Los criterios de este paso quedan como historial de lo construido; el comportamiento vigente está en §4, §5 y en el paso indicado.

**Do**
Crear/editar:
- `frontend/src/features/groups/api.ts` (editar) — `Player.level?: number` (ausente para un MEMBER) y `PlayerInput.level?: number`.
- `frontend/src/features/groups/GroupDetailPage.tsx` (editar) — solo con `myRole === 'ADMIN'`: campo obligatorio «Nivel (1 a 10)» (`input type=number`, `min=1`, `max=10`, con `label`; error con `aria-invalid` + `aria-describedby`) en el alta y la edición de jugadores, y el nivel junto al nombre en la lista. Un MEMBER no ve el campo, no lo envía y no ve ningún nivel.
- `frontend/src/features/groups/GroupsPages.test.tsx` (editar) — el alta manda `level`; casos de validación, edición por admin y ausencia total del nivel para un MEMBER.

**Done when**
- [ ] WHEN el admin envía el alta de un jugador con «Nivel» 7 THE SYSTEM SHALL hacer `POST /api/groups/{groupId}/players` con `level` 7 como entero.
- [ ] WHEN el campo «Nivel» está vacío o fuera de 1–10 THE SYSTEM SHALL no enviar la petición y mostrar el error asociado al campo con `aria-invalid`.
- [ ] WHEN el admin edita un jugador y cambia el nivel THE SYSTEM SHALL hacer `PATCH /api/groups/{groupId}/players/{playerId}` con `level`.
- [ ] WHEN un `MEMBER` ve la lista de jugadores o edita su propio jugador THE SYSTEM SHALL no mostrar ni enviar el nivel en ninguna parte de la pantalla.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint   # expect: exit 0
npm --prefix frontend run typecheck   # expect: exit 0
npm --prefix frontend run test   # expect: exit 0
npm --prefix frontend run build   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T5: add admin-only level to player screens"
git tag step-20-level-ui
```

---

#### Step 21 — Balanceo en décimas de nivel

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/match/balance/TeamBalancer.java` (editar) — `Candidate(UUID id, int levelTenths, int elo)`; `levelTenths` es el nivel × 10 (8,5 → 85) y debe estar entre 10 y 100, si no `IllegalArgumentException`. Las sumas y los numeradores de la comparación exacta siguen siendo `long`, ahora sobre décimas enteras; el orden de desempate (nivel, ELO, ids de A) no cambia. `Result` conserva su forma y expresa los promedios de nivel en unidades de nivel (`suma de décimas ÷ (10 · tamaño)`). `levelDifference` se calcula con una sola división, `|sumA·nB − sumB·nA| ÷ (10·nA·nB)`, para no arrastrar error de coma flotante (p. ej. 8,5 contra 4,8 da exactamente 3.7). Sigue puro: sin `org.springframework` ni `jakarta.persistence`.
- `src/test/java/com/futbolmanager/match/balance/TeamBalancerTest.java` (editar) — los candidatos pasan a décimas y el oráculo de fuerza bruta compara en décimas; casos nuevos de niveles decimales (8,5 y 4,8), de empate por una décima, del rango (9 y 101 rechazados; 10 y 100 aceptados) y de 22 jugadores en menos de 2 s.
- `src/main/java/com/futbolmanager/player/Player.java` (editar) — `getLevelTenths()` devuelve `level * 10`: es un puente transitorio que el paso 22 reemplaza por la columna nueva, para que este paso no rompa la compilación de `MatchController`.
- `src/main/java/com/futbolmanager/match/MatchController.java` (editar) — arma `Candidate` con `getLevelTenths()`; `BalancedPlayer.level` pasa a `BigDecimal.valueOf(levelTenths, 1)` (un número con un decimal, p. ej. `5.0`). `balance` sigue siendo solo ADMIN y el detalle del partido y el lineup siguen sin nivel.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` (editar) — `$.teamB[*].level` se lee como números con un decimal en vez de enteros; un MEMBER sigue recibiendo 403 en `balance` y sin `level` en detalle y lineup.

**Done when**
- [ ] WHEN `TeamBalancerTest` compara contra el oráculo de fuerza bruta (n ≤ 10) con niveles decimales como 8,5 y 4,8 THE SYSTEM SHALL producir una partición con la mínima diferencia exacta de nivel promedio y, entre las empatadas, la de menor diferencia de ELO promedio.
- [ ] WHEN dos particiones difieren en una sola décima de nivel THE SYSTEM SHALL elegir la de menor diferencia sin errores de coma flotante, comparando décimas enteras con `long`.
- [ ] WHEN un candidato trae `levelTenths` 9 o 101 THE SYSTEM SHALL lanzar `IllegalArgumentException`, y WHEN trae 10 o 100 THE SYSTEM SHALL aceptarlo.
- [ ] WHEN se balancean 22 jugadores THE SYSTEM SHALL terminar en menos de 2 segundos.
- [ ] WHEN un admin pide `POST /api/groups/{groupId}/matches/{matchId}/balance` sin cuerpo THE SYSTEM SHALL responder cada jugador con `level` como número con un decimal (por ejemplo 5.0) y los totales `avgLevelA`, `avgLevelB`, `levelDifference`, `avgEloA`, `avgEloB` y `eloDifference`, y WHEN un `MEMBER` pide `balance` THE SYSTEM SHALL responder 403.
- [ ] WHEN corren `TeamBalancerTest`, `MatchIntegrationTest` y `./mvnw -q verify`, y `grep` no encuentra imports de `org.springframework` ni `jakarta.persistence` en `TeamBalancer.java`, THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=TeamBalancerTest   # expect: exit 0
status=0; grep -nE 'import (org\.springframework|jakarta\.persistence)' src/main/java/com/futbolmanager/match/balance/TeamBalancer.java || status=$?; test "$status" -eq 1   # expect: exit 0 — sin imports prohibidos (grep sin coincidencias)
./mvnw -q test -Dtest=MatchIntegrationTest   # expect: exit 0
./mvnw -q verify   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T8: balance teams by decimal level in tenths"
git tag step-21-balance-tenths
```

---

#### Step 22 — Nivel de jugador con un decimal (en décimas)

**Do**
Crear/editar:
- `src/main/resources/db/migration/V5__player_level_decimal.sql` — `ALTER TABLE players ADD COLUMN level_tenths integer;`, `UPDATE players SET level_tenths = level * 10;`, `ALTER TABLE players ALTER COLUMN level_tenths SET NOT NULL;`, `ALTER TABLE players ADD CONSTRAINT ck_players_level_tenths CHECK (level_tenths BETWEEN 10 AND 100);` y `ALTER TABLE players DROP COLUMN level;` (arrastra `ck_players_level`). Los niveles existentes quedan igual (5 pasa a 50 décimas = 5,0). Se crea con la skill `add-migration`; V1–V4 no se tocan.
- `src/main/java/com/futbolmanager/player/Player.java` (editar) — el campo `level` pasa a `levelTenths` (`@Column(name = "level_tenths")`, int, no nulo) con su parámetro de constructor y getter/setter; se elimina el puente `getLevelTenths()` del paso 21 a favor del getter real.
- `src/main/java/com/futbolmanager/player/PlayerService.java` (editar) — `create(...)` recibe `levelTenths`; el jugador que se crea al crear el grupo o al unirse nace con 50.
- `src/main/java/com/futbolmanager/player/PlayerController.java` (editar) — `CreatePlayerRequest.level` pasa a `BigDecimal` con `@NotNull @DecimalMin("1.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1)`; `UpdatePlayerRequest.level` igual pero opcional. Se convierte con `movePointRight(1).intValueExact()` (el texto del JSON se lee exacto, sin pasar por `double`). `PlayerResponse.level` es `BigDecimal` (`BigDecimal.valueOf(tenths, 1)`, p. ej. `5.0`) y se omite del JSON cuando es null. El nivel sigue siendo solo para ADMIN y un MEMBER que lo envía recibe 403.
- `src/test/java/com/futbolmanager/player/PlayerIntegrationTest.java` (editar) — `levelOf` lee `level_tenths`; el alta y el `PATCH` con 8.5 y 4.8; rechazo de 0.9, 10.1, 8.55, 0, 11, falta de nivel y valor no numérico; CHECK `ck_players_level_tenths` con insert directo (9 y 101); autocreados en 50 y respuesta `5.0`; visibilidad por rol sin cambios.
- `src/test/java/com/futbolmanager/match/MatchIntegrationTest.java` (editar) — caso de punta a punta: crea por la API dos jugadores con nivel 8.5 y 4.8, los confirma en un partido, pide `POST …/balance` y verifica que devuelve `level` 8.5 y 4.8 y `levelDifference` 3.7.

**Excepción deliberada a la regla de 5 archivos (6 archivos):** `MatchIntegrationTest` es el único test que arma partidos, y sin él el recorrido decimal de punta a punta (alta por la API → balance) no tendría prueba de integración; aprobado por el dueño como la excepción de E4-T2.

**Done when**
- [ ] WHEN un admin envía `POST /api/groups/{groupId}/players` con `level` 8.5 y otro con 4.8 THE SYSTEM SHALL responder 201 con `level` 8.5 y 4.8 y guardar 85 y 48 en `players.level_tenths`, y WHEN `level` es 0.9, 10.1, 8.55, 0, 11 o falta THE SYSTEM SHALL responder 400 con un error en el campo `level`, tanto en el alta como en `PATCH`, y WHEN `level` no es un número THE SYSTEM SHALL responder 400.
- [ ] WHEN se inserta un jugador con `level_tenths` 9 o 101 directamente en la base THE SYSTEM SHALL rechazarlo por el CHECK `ck_players_level_tenths`.
- [ ] WHEN se crea el jugador del creador de un grupo o de quien se une THE SYSTEM SHALL asignarle nivel 5.0 (50 décimas), y WHEN `V5__player_level_decimal.sql` se aplica THE SYSTEM SHALL convertir los niveles existentes con `level_tenths = level * 10`.
- [ ] WHEN un `MEMBER` pide `GET /api/groups/{groupId}/players` o edita su propio jugador THE SYSTEM SHALL devolver jugadores sin el campo `level`, y WHEN un `MEMBER` envía `level` en `PATCH` THE SYSTEM SHALL responder 403.
- [ ] WHEN un admin crea por la API dos jugadores con `level` 8.5 y 4.8, los confirma en un partido y pide `POST /api/groups/{groupId}/matches/{matchId}/balance` THE SYSTEM SHALL devolver esos dos jugadores con `level` 8.5 y 4.8 y `levelDifference` 3.7.
- [ ] WHEN corren `PlayerIntegrationTest`, `MatchIntegrationTest` y `./mvnw -q verify` THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=PlayerIntegrationTest   # expect: exit 0
./mvnw -q test -Dtest=MatchIntegrationTest   # expect: exit 0 — el recorrido decimal de punta a punta
grep -q 'level_tenths BETWEEN 10 AND 100' src/main/resources/db/migration/V5__player_level_decimal.sql   # expect: exit 0 — el CHECK nuevo
grep -q 'SET level_tenths = level \* 10' src/main/resources/db/migration/V5__player_level_decimal.sql   # expect: exit 0 — la conversión de los existentes
./mvnw -q verify   # expect: exit 0 — Flyway aplica V1..V5 y Hibernate valida
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T9: store player level in tenths"
git tag step-22-level-tenths
```

---

#### Step 23 — Nivel decimal en el formulario de jugadores

**Do**
Crear/editar:
- `frontend/src/features/groups/PlayerForm.tsx` (editar) — el campo «Nivel (1 a 10)» pasa de `type=number` a `type=text` con `inputMode="decimal"` y `placeholder` «Ej.: 8,5» (el `number` no acepta la coma de forma consistente). Acepta coma o punto: se normaliza la coma a punto y se valida `^\d{1,2}(\.\d)?$` con rango 1,0–10,0; inválido (vacío, 0,9, 10,1, 8,55, texto) no envía y marca el campo con `aria-invalid` + `aria-describedby`. Envía `level` como número (`8.5`). Al editar precarga el nivel con un decimal y coma (`5,0`). `formatLevel(n)` (un decimal, coma) se exporta para la lista.
- `frontend/src/features/groups/PlayersTab.tsx` (editar) — la lista muestra `Nivel 5,0` con `formatLevel`, solo para ADMIN.
- `frontend/src/features/groups/GroupsPages.test.tsx` (editar) — el alta con `8,5`, `8.5` y `4,8` envía 8.5, 8.5 y 4.8; casos inválidos (vacío, `0,9`, `10,1`, `8,55`, `abc`); la edición precarga `5,0` y envía el nivel nuevo; un MEMBER no ve ni envía nivel. `Nivel 5` y `toHaveValue(5)` pasan a `Nivel 5,0` y `"5,0"`.

**Done when**
- [ ] WHEN el admin envía el alta con «Nivel» `8,5` u `8.5` THE SYSTEM SHALL hacer `POST /api/groups/{groupId}/players` con `level` 8.5 como número, y con `4,8` THE SYSTEM SHALL enviar 4.8.
- [ ] WHEN el campo «Nivel» está vacío, fuera de 1,0–10,0 (`0,9`, `10,1`), con más de un decimal (`8,55`) o no es un número THE SYSTEM SHALL no enviar la petición y mostrar el error asociado al campo con `aria-invalid`.
- [ ] WHEN el admin edita un jugador THE SYSTEM SHALL precargar el nivel con un decimal y coma (`5,0`), y WHEN cambia el nivel THE SYSTEM SHALL hacer `PATCH /api/groups/{groupId}/players/{playerId}` con `level`.
- [ ] WHEN un `ADMIN` ve la lista de jugadores THE SYSTEM SHALL mostrar `Nivel 5,0` con un decimal, y WHEN un `MEMBER` ve la lista o edita su propio jugador THE SYSTEM SHALL no mostrar ni enviar el nivel en ninguna parte de la pantalla.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint   # expect: exit 0
npm --prefix frontend run typecheck   # expect: exit 0
npm --prefix frontend run test   # expect: exit 0
npm --prefix frontend run build   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T10: accept decimal level in the player form"
git tag step-23-level-decimal-form
```

---

#### Step 24 — Nivel promedio en el armado de equipos

**Do**
Crear/editar:
- `frontend/src/features/matches/api.ts` (editar) — `BalanceResult`/`BalancedPlayer` con el contrato de E4-T3 (`level`, `avgLevelA/B`, `levelDifference`; sin `goalkeeperConstraintApplied`); `useBalance` es una mutación sin argumentos que hace `POST …/balance` sin cuerpo.
- `frontend/src/features/matches/MatchSetupPage.tsx` (editar) — se elimina la casilla «Un arquero por equipo»; en el armado (solo admin) cada equipo muestra «Nivel promedio» y debajo «Diferencia de nivel: N», y como dato secundario el ELO promedio y la diferencia de ELO; el select «Puesto de {nombre}» se conserva como dato informativo. Un MEMBER ve convocatoria y equipos guardados sin ningún nivel.
- `frontend/src/features/matches/MatchSetupPage.test.tsx` (editar) — el balance sin cuerpo y con el contrato nuevo; se borran los chequeos de `Un arquero por equipo`; caso de MEMBER sin niveles.

**Done when**
- [ ] WHEN se activa `Armar equipos` THE SYSTEM SHALL hacer `POST …/balance` sin `oneGoalkeeperPerSide` y mostrar ambos equipos con nivel promedio, `Diferencia de nivel: N` y, como dato secundario, el ELO promedio y la diferencia de ELO.
- [ ] WHEN se renderiza el armado de equipos THE SYSTEM SHALL no mostrar ninguna casilla `Un arquero por equipo`.
- [ ] WHEN se activa con el teclado `Pasar al equipo B` THE SYSTEM SHALL mover al jugador sin arrastrar, y cada select de puesto SHALL conservar el nombre accesible `Puesto de {nombre}`.
- [ ] WHEN un `MEMBER` abre el detalle del partido THE SYSTEM SHALL mostrar la convocatoria y los equipos guardados sin controles de armado y sin ningún valor de nivel.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint   # expect: exit 0
npm --prefix frontend run typecheck   # expect: exit 0
npm --prefix frontend run test   # expect: exit 0
npm --prefix frontend run build   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T6: show level balance in team builder"
git tag step-24-balance-level-ui
```

---

#### Step 25 — Jugadón y diálogos en el modo en vivo

**Do**
Crear/editar:
- `frontend/src/components/ui.tsx` (editar) — nuevo `DialogShell` sobre `radix-ui` Dialog: overlay con `fixed inset-0 z-40 bg-background/80` y contenido con `z-50` (por encima del marcador sticky `z-10`), `max-h-[calc(100dvh-8rem)] overflow-y-auto` y la sombra de diálogo del sistema de diseño. **Todo diálogo del modo en vivo (autor/asistencia ahora, cierre de partido en el paso 27) se arma con `DialogShell`**, así la capa correcta no depende de cada pantalla.
- `frontend/src/features/live/api.ts` (editar) — `EventType` suma `'PLAY'`.
- `frontend/src/features/live/EventDialog.tsx` — el diálogo de autor/asistencia extraído de `LivePage.tsx` (mantiene cada archivo ≤ 400 líneas y cada componente ≤ 300) sobre `DialogShell`; en atajadas, cortes y jugadones lista a todos los jugadores del lineup del equipo **sin preseleccionar a nadie** (el arquero ya no es especial); el gol sigue ofreciendo `En contra / no sé` y asistencia opcional.
- `frontend/src/features/live/LivePage.tsx` (editar) — botones `Button size="live"` `Jugadón A` y `Jugadón B`; la lista de eventos muestra `Jugadón del equipo {equipo}: {jugador}`.
- `frontend/src/features/live/LivePage.test.tsx` (editar) — casos de `Jugadón`, de la atajada sin preselección y del chequeo de capas. `.claude/rules/testing.md` admite, junto a `min-h-16`, el chequeo de las utilidades `z-40`/`z-50` de los diálogos, porque jsdom no tiene layout.

**Done when**
- [ ] WHEN se renderiza el panel de registro THE SYSTEM SHALL mostrar `Jugadón A` y `Jugadón B` con la utilidad `min-h-16`, y WHEN se activa `Jugadón A` y se elige el jugador THE SYSTEM SHALL hacer `POST …/events` con `type` `PLAY`, `team` `A`, el `playerId` elegido y un `id` UUID.
- [ ] WHEN se activa `Atajada A` THE SYSTEM SHALL abrir el diálogo con todos los jugadores del lineup del equipo A, sin preseleccionar a nadie, y exigir elegir uno antes de registrar.
- [ ] WHEN la lista de eventos incluye un `PLAY` THE SYSTEM SHALL mostrarlo como `Jugadón del equipo {equipo}: {jugador}` con `cargado por {displayName}` y `Anular` según las reglas de autor y admin.
- [ ] WHEN se abre cualquier diálogo del modo en vivo THE SYSTEM SHALL renderizar su overlay con la utilidad `z-40` y su contenido con `z-50`, ambos por encima del marcador sticky (`z-10`), porque todos se arman con `DialogShell`.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend THE SYSTEM SHALL salir con 0.

**Verify**
```bash
npm --prefix frontend run format:check   # expect: exit 0
npm --prefix frontend run lint   # expect: exit 0
npm --prefix frontend run typecheck   # expect: exit 0
npm --prefix frontend run test   # expect: exit 0
npm --prefix frontend run build   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E4-T7: add jugadon and open saves to live mode"
git tag step-25-live-play-ui
```

---

#### Step 26 — Cierre, libro ELO y recálculo

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/elo/EloReplayService.java` — `@Service`, `@Transactional void replayFrom(UUID groupId, UUID matchId)` con el algoritmo de §5 (*ELO*), todo con `JdbcClient`: bloqueo `SELECT id FROM groups WHERE id = ? FOR UPDATE`; borrado de filas `TEAM_RESULT`/`BONUS`/`MVP_BONUS` de los partidos con `(played_at, id) >= pivote`; reseteo de `current_elo` desde el libro; re-aplicación de cada partido `CLOSED` en orden: lineup, `current_elo` vigente, cantidad de partidos contados previos por jugador, agregados de eventos no anulados (goles, asistencias, atajadas, cortes, jugadones, marcador), MVP → `EloCalculator` → `INSERT` de `TEAM_RESULT` y, si `bonus > 0`, `BONUS` con `details` (`?::jsonb`, JSON armado con `JsonMapper` en el orden de §4) → `UPDATE players.current_elo`. Las filas `INITIAL` nunca se tocan.
- `src/main/java/com/futbolmanager/match/MatchLifecycleController.java` — `POST …/close` (`LIVE` → `CLOSED`, marcador desde eventos, MVP opcional del lineup o 422, `closed_at`, luego `replayFrom`; responde `{scoreA, scoreB, mvpPlayerId, eloChanges:[{playerId, name, eloBefore, eloAfter, delta}]}`), `PUT …/mvp` (solo `CLOSED`, luego `replayFrom`), `POST …/annul` (`SCHEDULED` → `ANNULLED`; `CLOSED` → `ANNULLED` + `replayFrom` desde ese partido; otro estado → 409). Todo admin; si no → 403.
- `src/main/java/com/futbolmanager/live/LiveController.java` (editar) — en `CLOSED`, alta y anulación de eventos solo para admin y, tras guardar, recalcula `score_a`/`score_b` y llama a `replayFrom(groupId, matchId)` en la misma transacción.
- `src/test/java/com/futbolmanager/elo/EloReplayIntegrationTest.java` — por API: cierre 2–1 y filas del libro; 409/403 de cierre; anulación de un partido cerrado; corrección (evento y MVP) sobre un partido cerrado; `INITIAL` intactas.
- `src/test/java/com/futbolmanager/elo/EloReplayPropertyTest.java` — `Random` con semilla fija (`42`): un grupo con 12 jugadores, 8 partidos con lineups y eventos aleatorios (GOAL, SAVE, TACKLE y PLAY; atajadas de cualquier jugador) cerrados uno por uno vía API en orden de `played_at`; captura el libro (`match_id, player_id, reason, elo_before, elo_after, delta`, ordenado) y los `current_elo`; ejecuta `replayFrom` desde el primer partido; compara igualdad exacta.

**Done when**
- [ ] WHEN un admin cierra un partido `LIVE` con goles 2–1 THE SYSTEM SHALL dejarlo `CLOSED` con `score_a` 2 y `score_b` 1, escribir una fila `TEAM_RESULT` por jugador del lineup y una `BONUS` solo para quienes tienen bonus mayor que 0, cada una con `elo_after` igual a `elo_before` + `delta`, y actualizar `current_elo`.
- [ ] WHEN se intenta cerrar un partido que no está `LIVE` THE SYSTEM SHALL responder 409, y WHEN lo intenta un `MEMBER` THE SYSTEM SHALL responder 403.
- [ ] WHEN `EloReplayPropertyTest` cierra uno por uno una secuencia aleatoria de partidos con semilla fija (eventos GOAL, SAVE, TACKLE y PLAY, con atajadas de cualquier jugador) y después ejecuta `replayFrom` desde el primero THE SYSTEM SHALL producir filas `(match_id, player_id, reason, elo_before, elo_after, delta)` y valores de `current_elo` idénticos.
- [ ] WHEN un admin anula un partido `CLOSED` THE SYSTEM SHALL borrar sus filas `TEAM_RESULT` y `BONUS`, recalcular los partidos posteriores y dejar cada `current_elo` igual al que produce un `replayFrom` completo posterior.
- [ ] WHEN un admin agrega o anula un evento o cambia el MVP de un partido `CLOSED` THE SYSTEM SHALL recalcular ese partido y todos los posteriores, y WHEN lo intenta un `MEMBER` THE SYSTEM SHALL responder 403.
- [ ] WHEN corre cualquier recálculo THE SYSTEM SHALL dejar intactas las filas `INITIAL` (mismos ids y valores).

**Verify**
```bash
./mvnw -q test -Dtest=EloReplayIntegrationTest   # expect: exit 0
./mvnw -q test -Dtest=EloReplayPropertyTest      # expect: exit 0
./mvnw -q verify                                 # expect: exit 0 — LiveIntegrationTest sigue verde con el cambio de CLOSED
```

**Checkpoint**
```bash
git add -A && git commit -m "E3-T4: close matches with elo ledger and replay"
git tag step-26-close-elo-replay
```

---

#### Step 27 — Ranking, estadísticas y cierre en la UI

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/stats/StatsController.java` — `GET /api/groups/{groupId}/ranking`, `GET /api/groups/{groupId}/players/{playerId}/stats`, `GET /api/groups/{groupId}/players/{playerId}/elo-history`, con `JdbcClient` y las definiciones de §5 (*Estadísticas*). Respuestas: ranking `[{playerId, name, nickname, currentElo, played, provisional}]`; stats `{playerId, name, played, won, drawn, lost, goals, assists, mvps, streak:{type, length}|null, currentElo, provisional}`; historial `[{matchId, playedAt, eloAfter}]`.
- `src/test/java/com/futbolmanager/stats/StatsIntegrationTest.java` — arma por API 3 partidos cerrados (victoria, victoria, empate para el jugador observado), uno anulado y eventos anulados; cubre los criterios 1–5.
- `frontend/src/features/stats/StatsPages.tsx` — `RankingPage` (`h1` "Ranking"; tabla con posición, nombre, ELO `font-mono tabular-nums`, partidos y badge "Provisorio") y `PlayerProfilePage` (`h1` con el nombre; tarjetas PJ/G/E/P, goles, asistencias, MVPs con badge `bg-mvp-badge text-on-mvp`, racha "3 victorias seguidas"; tabla de historial ELO — sin gráfico, Non-Goal). Incluye sus propios hooks de query (claves de §6).
- `frontend/src/features/live/LivePage.tsx` (editar) — para admin con partido `LIVE`: botón "Cerrar partido" → diálogo (`DialogShell` de `components/ui`, siempre por encima del marcador sticky) con `select` "MVP (opcional)" de los jugadores del lineup → `POST …/close` → muestra el resumen de ELO (`eloChanges`, delta con signo y en texto, no solo color) e invalida `['groups', groupId]`; en `CLOSED`, el admin ve "Cambiar MVP" (`PUT …/mvp`) y "Anular partido" (`POST …/annul`, con confirmación).
- `frontend/src/app/router.tsx` (editar) — `/grupos/:groupId/ranking` → `RankingPage`, `/grupos/:groupId/jugadores/:playerId` → `PlayerProfilePage`.

**Done when**
- [ ] WHEN se pide `GET /api/groups/{groupId}/ranking` THE SYSTEM SHALL devolver los jugadores no borrados ordenados por `currentElo` descendente y `name` ascendente, cada uno con `played` y `provisional` igual a `played < 10`.
- [ ] WHEN un jugador tiene 3 partidos cerrados con resultados victoria, victoria y empate (el empate es el último) THE SYSTEM SHALL devolver `played` 3, `won` 2, `drawn` 1, `lost` 0 y `streak` `{"type":"D","length":1}`.
- [ ] WHEN se calculan goles y asistencias THE SYSTEM SHALL contar solo eventos no anulados de partidos `CLOSED`, sin contar partidos `ANNULLED`.
- [ ] WHEN se pide `GET /api/groups/{groupId}/players/{playerId}/elo-history` THE SYSTEM SHALL devolver primero el punto `INITIAL` y luego un punto por partido cerrado cuyo `eloAfter` incluye `TEAM_RESULT` y `BONUS`.
- [ ] WHEN alguien que no es miembro pide el ranking, las estadísticas o el historial THE SYSTEM SHALL responder 404.
- [ ] WHEN corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend con `StatsPages.tsx` y el diálogo de cierre THE SYSTEM SHALL salir con 0.

**Verify**
```bash
./mvnw -q test -Dtest=StatsIntegrationTest   # expect: exit 0 — criterios 1–5
./mvnw -q verify                             # expect: exit 0
npm --prefix frontend run format:check       # expect: exit 0
npm --prefix frontend run lint               # expect: exit 0
npm --prefix frontend run typecheck          # expect: exit 0
npm --prefix frontend run test               # expect: exit 0 — LivePage.test.tsx sigue verde con el diálogo
npm --prefix frontend run build              # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "E3-T5: add ranking, stats and close dialog"
git tag step-27-stats-ranking
```

---

#### Step 28 — Hardening, demo, E2E y deploy

**Do**
Crear/editar:
- `src/main/java/com/futbolmanager/config/SecurityConfig.java` (editar) — inyecta `@Value("${fm.security.content-security-policy}") String csp`; si no está vacío agrega `headers.contentSecurityPolicy(c -> c.policyDirectives(csp))` (solo `application-prod.yml` lo define, paso 3). `Referrer-Policy`, `X-Content-Type-Options` y `X-Frame-Options: DENY` ya están desde el paso 1.
- `src/main/java/com/futbolmanager/config/DemoDataSeeder.java` — `@Profile("demo")` `@Component` que implementa `SmartInitializingSingleton` (**no** `ApplicationRunner`: `afterSingletonsInstantiated()` corre después de Flyway y antes de que arranque el conector web, así `/api/health` no responde hasta que la siembra quedó commiteada y `scripts/smoke.sh` no puede matar la JVM a mitad de la transacción), con `@Value("${DEMO_PASSWORD}")` y un `TransactionTemplate` inyectado: si existe un grupo llamado `Los del jueves`, no hace nada (idempotente); si no, dentro de `transactionTemplate.executeWithoutResult(...)` crea el usuario `demo` (display "Demo"), el grupo (vía las mismas entidades/servicios: el creador obtiene su jugador por `MemberJoined`), 13 invitados más (14 jugadores activos, con niveles variados de 1 a 10; el puesto preferido es solo informativo), 6 partidos en jueves pasados consecutivos con lineups balanceados por `TeamBalancer` (por nivel), eventos generados con `Random(42)`, estado `CLOSED` y `EloReplayService.replayFrom` del primero.
- `src/test/java/com/futbolmanager/config/ProdProfileIntegrationTest.java` — `@ActiveProfiles("prod")` + Testcontainers; criterio 1.
- `frontend/e2e/flows.spec.ts` — 3 tests (`registro y grupo`, `partido completo`, `throttling de login`) con usuarios únicos por corrida (`e2e_` + 8 caracteres aleatorios); al crear jugadores completa el campo «Nivel»; helper `expectNoSeriousA11yViolations(page)` con `AxeBuilder` (tags `wcag2a`, `wcag2aa`, `wcag21aa`, `wcag22aa`; falla si hay `impact` `serious` o `critical`) y `expectNoHorizontalScroll(page)` (`document.documentElement.scrollWidth <= clientWidth`), llamados en `/ingresar`, `/registro`, `/grupos`, el detalle de grupo y el modo en vivo; en el modo en vivo mide con `boundingBox()` que los botones de registro tienen alto ≥ 64.
- `README.md` — qué es, stack, requisitos (JDK 21, Docker Desktop, Node 24 LTS — Node 25 instala con warning `EBADENGINE`), Bootstrap, comandos (Git Bash y CMD/PowerShell), modo demo, y el runbook de deploy de §12 (Neon + Render + pinger opcional + rollback).

**Done when**
- [ ] WHEN corre `ProdProfileIntegrationTest` con el perfil `prod` THE SYSTEM SHALL devolver en `/api/health` los headers `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Content-Type-Options: nosniff` y `X-Frame-Options: DENY`, y 404 en `/v3/api-docs` y `/swagger-ui.html`.
- [ ] WHEN el jar arranca dos veces con `SPRING_PROFILES_ACTIVE=demo` THE SYSTEM SHALL dejar exactamente un grupo `Los del jueves` con 14 jugadores activos y 6 partidos `CLOSED`.
- [ ] WHEN corre el test de Playwright `registro y grupo` THE SYSTEM SHALL registrar un usuario, crear un grupo y mostrar 3 jugadores invitados agregados en la lista de jugadores.
- [ ] WHEN corre el test de Playwright `partido completo` THE SYSTEM SHALL crear un partido, confirmar 4 jugadores, armar y guardar equipos, empezar, cargar 2 goles y 1 jugadón del equipo A, cerrar el partido y mostrar en el ranking a cada jugador del equipo A con ELO mayor a 1000.
- [ ] WHEN corre el test de Playwright `throttling de login` y un usuario falla la contraseña 5 veces y lo intenta una sexta THE SYSTEM SHALL mostrar un mensaje que contiene `Demasiados intentos fallidos`.
- [ ] WHEN los tests de Playwright analizan con axe `/ingresar`, `/registro`, `/grupos`, el detalle de grupo y el modo en vivo a 375 px de ancho THE SYSTEM SHALL reportar 0 violaciones `serious` o `critical`, ningún scroll horizontal y botones de registro en vivo de al menos 64 px de alto.

**Verify**
```bash
./mvnw -q test -Dtest=ProdProfileIntegrationTest   # expect: exit 0 — criterio 1
./mvnw -q verify                                   # expect: exit 0
npm --prefix frontend run format:check             # expect: exit 0 — incluye e2e/flows.spec.ts
npm --prefix frontend run lint                     # expect: exit 0
npm --prefix frontend run typecheck                # expect: exit 0 — tsconfig.node.json incluye e2e/
npm --prefix frontend run build                    # expect: exit 0
./mvnw -q -DskipTests package                      # expect: exit 0 — jar con el frontend final
SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh  # expect: exit 0 — primer arranque siembra
SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh  # expect: exit 0 — segundo arranque no duplica
test "$(docker compose exec -T db psql -U futbol -d futbol -tAc "select count(*) from groups where name = 'Los del jueves'")" = 1
                                                   # expect: exit 0
test "$(docker compose exec -T db psql -U futbol -d futbol -tAc "select count(*) from players p join groups g on g.id = p.group_id where g.name = 'Los del jueves' and p.deleted_at is null")" = 14
                                                   # expect: exit 0
test "$(docker compose exec -T db psql -U futbol -d futbol -tAc "select count(*) from matches m join groups g on g.id = m.group_id where g.name = 'Los del jueves' and m.status = 'CLOSED'")" = 6
                                                   # expect: exit 0
npm --prefix frontend run e2e:install              # expect: exit 0 — descarga Chromium (una vez)
npm --prefix frontend run test:e2e                 # expect: exit 0 — 4 tests (health.spec + los 3 de flows.spec), 0 failed (criterios 3–6)
```

**Checkpoint**
```bash
git add -A && git commit -m "E3-T6: harden prod, seed demo, run e2e, document deploy"
git tag step-28-hardening-e2e
```

---

### 9.1 Parity and cutover

NOT APPLICABLE — greenfield build, no system is being replaced.

---

## 10. Environment Setup

### Prerequisites

| Tool | Version | Check |
|---|---|---|
| Git (con Git Bash en Windows) | cualquiera reciente, con `user.name` y `user.email` configurados (Bootstrap hace un commit) | `git --version && git config user.email` |
| JDK | 21 (Temurin recomendado) | `java -version` |
| Maven (global, solo para generar el wrapper la primera vez) | 3.9.16 instalado globalmente | `mvn -v` |
| Node.js | **24 LTS** (`.nvmrc` = `24`). Node 25.9.0 instalado localmente funciona con un warning `EBADENGINE` de npm (vitest 5 declara `^22.12.0 \|\| ^24.0.0 \|\| >=26.0.0`); recomendado cambiar a 24 con nvm-windows o fnm | `node -v` |
| npm | el que trae Node | `npm -v` |
| Docker Desktop | corriendo (Testcontainers en `./mvnw verify`, compose para la base local, build de la imagen) | `docker info` |
| curl, bash | los de Git Bash | `curl --version` |
| Puerto 5433 libre | la base de compose publica `5433:5432` (el 5432 lo usa el PostgreSQL nativo) | — |
| Puertos 8080 y 8081 libres | 8080: jar local / Playwright · 8081: `scripts/docker-smoke.sh` | — |

Todos los comandos de los gates corren en **Git Bash** desde la raíz del proyecto. Equivalentes humanos en CMD/PowerShell: `mvnw.cmd …` / `.\mvnw.cmd …` (la tabla completa está en §19.1). Los scripts `scripts/*.sh` solo corren en Git Bash.

### Accounts to create first

| Service | Signup | First needed |
|---|---|---|
| GitHub (repo + Actions) | https://github.com/signup | Después del paso 3, para que corra `ci.yml` (no es gate del build) |
| Neon (Postgres 17, plan Free) | https://console.neon.tech/signup | Launch checklist (§20.1) |
| Render (Web Service Free, Docker) | https://dashboard.render.com/register | Launch checklist (§20.1) |
| UptimeRobot (opcional) | https://uptimerobot.com/signUp | Launch checklist (§20.1) |

Ningún paso de §9 necesita estas cuentas: todos los gates corren local.

### Environment variables

| Variable | Purpose | Where to get it | Required by step | Secret? |
|---|---|---|---|---|
| `DB_URL` | JDBC URL de PostgreSQL. Local: `jdbc:postgresql://localhost:5433/futbol` | local: `.env` (copia de `.env.example`) · prod: Neon → Connection details → formato JDBC + `?sslmode=require` | 1 | prod sí |
| `DB_USERNAME` | usuario de la base. Local: `futbol` | `.env` · Neon | 1 | no |
| `DB_PASSWORD` | contraseña. Local: `futbol_local_dev` (solo desarrollo) | `.env` · Neon | 1 | prod sí |
| `DB_NAME` | nombre de la base local para compose. Local: `futbol` | `.env` | 1 (lo lee `docker-compose.yml`) | no |
| `PORT` | puerto HTTP; default 8080 | Render lo inyecta | — (opcional) | no |
| `RENDER_GIT_COMMIT` | versión en `/api/health`; default `dev` | Render lo inyecta | — (opcional) | no |
| `SPRING_PROFILES_ACTIVE` | `prod` en Render; `demo` para sembrar | Render env / línea de comando | 3 (`docker-smoke.sh` lo pasa) | no |
| `DEMO_PASSWORD` | contraseña del usuario `demo`, solo con perfil `demo`. Local: `demo_local_dev` | `.env` · Render (si se siembra en prod) | 18 | prod sí |
| `SMOKE_PORT` | puerto de `scripts/smoke.sh`; default 8080 | línea de comando | — (opcional) | no |
| `CI` | solo activa `forbidOnly` en `frontend/playwright.config.ts`; no afecta ningún gate local | GitHub Actions la define sola | — (opcional) | no |

`.env.example` está commiteado con valores **solo de desarrollo local** (nunca se usan en prod); `.env` está en `.gitignore`. `application.yml` lee `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` **sin defaults**, y el listener de `FutbolManagerApplication.main` (paso 1) corta el arranque con `Falta la variable de entorno obligatoria <NOMBRE>` si alguna falta o está en blanco (gate del paso 3). No hay validación por pasos porque las tres se necesitan desde el paso 1 y Bootstrap las provee; `DEMO_PASSWORD` solo la lee un bean `@Profile("demo")`, así que su ausencia no rompe ningún gate anterior al 18.

**Quién carga cada variable** (detalle en §19.6): la app Spring la lee de `.env` vía `spring.config.import: optional:file:.env[.properties]` (relativo al directorio de trabajo = raíz del proyecto; `scripts/smoke.sh` hace `cd` a la raíz y Playwright arranca el jar con `cwd: '..'`); `docker compose` lee `.env` nativamente; `scripts/docker-smoke.sh` hace `set -a; . ./.env; set +a` y pasa `--env-file .env`; los tests de Maven **no** usan `.env`: Surefire define valores placeholder (`pom.xml`) y `@ServiceConnection` los reemplaza por el contenedor de Testcontainers; los `psql` del paso 28 usan `-U futbol -d futbol` literales dentro del contenedor (sin variables).

### Files that must be committed

| File | Why it is committed | Ignore-file exception line |
|---|---|---|
| `.env.example` | documenta las variables con valores locales | `!.env.example` después de `.env` (el patrón `.env` no lo matchea, la negación queda como defensa) |
| `.claude/settings.json`, `.claude/rules/*.md`, `.claude/skills/**` | configuración compartida del agente | — no matcheado (solo `.claude/settings.local.json` está ignorado) |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | el wrapper fija Maven 3.9.16; CI y Docker lo usan | — no matcheado |
| `frontend/package-lock.json` | `npm ci` en CI y Docker | — no matcheado |
| `pom.xml`, `frontend/package.json`, configs de `frontend/` | manifiestos y configs de los gates | — no matcheado |
| `src/main/resources/db/migration/*.sql` | esquema | — no matcheado |
| `docker-compose.yml`, `Dockerfile`, `.dockerignore`, `render.yaml` | infraestructura | — no matcheado |
| `.github/workflows/ci.yml` | CI | — no matcheado |
| `scripts/smoke.sh`, `scripts/docker-smoke.sh` | gates | — no matcheado |
| `.gitattributes`, `.gitignore`, `.nvmrc`, `CLAUDE.md`, `AGENTS.md`, `README.md` | repo | — no matcheado |
| `blueprints/futbol-manager/**` | este bundle (documentación del proyecto) | — no matcheado |

Patrones del `.gitignore` emitido: `target/`, `src/main/resources/static/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/coverage/`, `frontend/test-results/`, `frontend/playwright-report/`, `.env`, `!.env.example`, `*.log`, `.idea/`, `.vscode/`, `.claude/settings.local.json`. Ninguno matchea los archivos de la tabla (comprobado patrón por patrón). El `.gitignore` llega con la copia de `workspace/`, **antes** del primer commit.

### Bootstrap

Corre desde la raíz del proyecto (`D:/Proyectos Java VSC/futbol_manager`, donde está `blueprints/futbol-manager/`), en Git Bash, no interactivo. Es seguro correrlo dos veces: la segunda vez cada línea es un no-op que sale con 0.

```bash
# order matters: repo init → workspace copy (.gitignore/.gitattributes/configs) → .env → Maven wrapper
#                → npm install → first commit (everything above is in place, ignore file first) → local DB
git rev-parse --git-dir >/dev/null 2>&1 || git init -b main   # idempotent: the target folder is already a repo on main

# Copy workspace/ into the project root exactly once. Marker = pom.xml (emitted by workspace/).
# Not `cp -n`: GNU and BSD cp disagree on its exit status; this guard exits 0 on both when it skips.
# Never re-copy over a bootstrapped tree: it would revert pom.xml / package.json edits.
[ -f pom.xml ] || cp -r blueprints/futbol-manager/workspace/. .

[ -f .env ] || cp .env.example .env   # local dev values only; .env is gitignored

# Maven wrapper (mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties) from the globally installed Maven.
[ -f mvnw ] || mvn -N -q org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.16

npm --prefix frontend install   # creates frontend/package-lock.json on the first run; no-op afterwards

# First commit only if the repo has none. update-index marks mvnw executable for Linux (CI, Docker).
git rev-parse --verify HEAD >/dev/null 2>&1 || { git add -A && git update-index --chmod=+x mvnw && git commit -q -m "chore: bootstrap"; }

docker compose up -d --wait db   # postgres:17 on host port 5433, healthy before returning
```

Archivos que la copia **nunca** vuelve a pisar (porque el marcador `pom.xml` ya existe): `pom.xml`, `frontend/package.json`, `frontend/package-lock.json` y cualquier archivo emitido que un paso haya editado (p. ej. `application.yml`). La descarga de Chromium para Playwright no está acá: la hace el paso 2 (`npm --prefix frontend run e2e:install`). No hay migraciones ni seed en Bootstrap: Flyway migra al arrancar la app o cada test.

Comandos de servicios locales: up `docker compose up -d --wait db` · down `docker compose down` · reset (borra datos) `docker compose down -v`.

---

## 11. Dependencies

Fuente única: el informe de `stack-researcher` de esta sesión, verificado el **2026-10-21**. No hay runtime track de respaldo (desvío Java). Los starters de Spring Boot no llevan versión en `pom.xml`: las fija el BOM `https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom`. "Instalado por" — Maven: el `pom.xml` emitido en §19.6, resuelto por el primer `./mvnw -q test …`/`./mvnw -q verify` del paso 1; npm: `npm --prefix frontend install` de §10 Bootstrap sobre el `frontend/package.json` emitido (versiones exactas, sin `^`).

### Runtime

| Package | Version | Source (registry URL or track file) | Checked | Installed by | Purpose |
|---|---|---|---|---|---|
| org.springframework.boot:spring-boot-starter-parent | 4.1.1 | https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml | 2026-10-21 | `pom.xml` (§19.6) → paso 1 `./mvnw` | parent + BOM; soporte OSS hasta 2027-07-31 |
| spring-boot-starter-webmvc | 4.1.1 (BOM) | BOM 4.1.1 (URL arriba) | 2026-10-21 | `pom.xml` → paso 1 | Spring MVC (en Boot 4 reemplaza al nombre `-web`) |
| spring-boot-starter-security | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | Spring Security 7 |
| spring-boot-starter-data-jpa | 4.1.1 (BOM; Hibernate 7.4.5.Final) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | JPA/Hibernate |
| spring-boot-starter-validation | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | Bean Validation de requests |
| spring-boot-starter-session-jdbc | 4.1.1 (BOM) | BOM 4.1.1; propiedades verificadas en spring-boot-session-jdbc-4.1.1.jar | 2026-10-21 | `pom.xml` → paso 1 | sesiones en Postgres |
| spring-boot-starter-flyway | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | autoconfiguración de Flyway (obligatoria en Boot 4) |
| org.flywaydb:flyway-database-postgresql | 12.4.0 (BOM) | BOM 4.1.1 — no fijar 13.x a mano | 2026-10-21 | `pom.xml` → paso 1 | soporte PostgreSQL de Flyway |
| org.postgresql:postgresql | 42.7.13 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | driver JDBC (scope runtime) |
| org.springdoc:springdoc-openapi-starter-webmvc-ui | 3.1.1 | https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml | 2026-10-21 | `pom.xml` → paso 1 | OpenAPI + Swagger UI en dev (apagado en prod) |
| react / react-dom | 19.3.1 | https://registry.npmjs.org/react · https://registry.npmjs.org/react-dom | 2026-10-21 | §10 Bootstrap `npm --prefix frontend install` | UI |
| react-router | 7.18.4 | https://registry.npmjs.org/react-router | 2026-10-21 | §10 Bootstrap | ruteo (línea 7 elegida sobre 8.x por madurez de docs; sin `react-router-dom`) |
| @tanstack/react-query | 5.104.5 | https://registry.npmjs.org/@tanstack/react-query | 2026-10-21 | §10 Bootstrap | estado de servidor + polling |
| radix-ui | 1.8.1 | https://registry.npmjs.org/radix-ui | 2026-10-21 | §10 Bootstrap | primitivas accesibles (Dialog, Select) |
| class-variance-authority | 0.7.1 | https://registry.npmjs.org/class-variance-authority | 2026-10-21 | §10 Bootstrap | variantes de `Button` |
| clsx | 2.1.1 | https://registry.npmjs.org/clsx | 2026-10-21 | §10 Bootstrap | composición de clases |
| tailwind-merge | 3.7.0 | https://registry.npmjs.org/tailwind-merge | 2026-10-21 | §10 Bootstrap | merge de clases Tailwind |
| lucide-react | 1.61.0 | https://registry.npmjs.org/lucide-react | 2026-10-21 | §10 Bootstrap | íconos |
| @fontsource-variable/inter | 5.3.0 | https://registry.npmjs.org/@fontsource-variable/inter | 2026-10-21 | §10 Bootstrap | fuente UI self-hosted |
| @fontsource-variable/jetbrains-mono | 5.3.0 | https://registry.npmjs.org/@fontsource-variable/jetbrains-mono | 2026-10-21 | §10 Bootstrap | fuente numérica self-hosted |
| Imagen `postgres:17` (17.11) | 17 | Docker Hub `library/postgres` (informe de pins) | 2026-10-21 | `docker-compose.yml` y `TestcontainersConfiguration.java` (§19.6) | base local y de tests |
| Imagen `eclipse-temurin:21-jre` | 21-jre (actualizada 2026-10-17) | Docker Hub `library/eclipse-temurin` (informe de pins) | 2026-10-21 | `Dockerfile` (paso 3) | runtime de la imagen |

### Development

| Package | Version | Source (registry URL or track file) | Checked | Installed by | Purpose |
|---|---|---|---|---|---|
| spring-boot-starter-test | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | JUnit 5, AssertJ, JsonPath, Mockito |
| spring-boot-starter-webmvc-test | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | MockMvc / `@AutoConfigureMockMvc` |
| spring-boot-starter-security-test | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | `csrf()` post-processor |
| spring-boot-testcontainers | 4.1.1 (BOM) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | `@ServiceConnection` |
| org.testcontainers:testcontainers-postgresql | 2.0.5 (testcontainers-bom importado por el BOM de Boot) | BOM 4.1.1 | 2026-10-21 | `pom.xml` → paso 1 | `org.testcontainers.postgresql.PostgreSQLContainer` |
| Maven (distribución del wrapper) | 3.9.16 | informe de pins (Maven Central) | 2026-10-21 | §10 Bootstrap (`-Dmaven=3.9.16`) | build |
| org.apache.maven.plugins:maven-wrapper-plugin | 3.3.4 | informe de pins (Maven Central) | 2026-10-21 | §10 Bootstrap (`mvn -N … maven-wrapper-plugin:3.3.4:wrapper`) | genera `mvnw` |
| vite | 8.3.5 | https://registry.npmjs.org/vite | 2026-10-21 | §10 Bootstrap | dev server + build (engines node `^20.19.0 \|\| >=22.12.0`) |
| @vitejs/plugin-react | 6.1.2 | https://registry.npmjs.org/@vitejs/plugin-react | 2026-10-21 | §10 Bootstrap | React en Vite (peer vite ^8) |
| typescript | 6.0.3 | https://registry.npmjs.org/typescript | 2026-10-21 | §10 Bootstrap | tipos (no 7.x: typescript-eslint exige < 6.1.0) |
| @types/react / @types/react-dom | 19.3.0 | https://registry.npmjs.org/@types/react | 2026-10-21 | §10 Bootstrap | tipos |
| @types/node | 24.19.2 | https://registry.npmjs.org/@types/node | 2026-10-21 | §10 Bootstrap | tipos de configs (vite/playwright) |
| tailwindcss / @tailwindcss/vite | 4.3.3 | https://registry.npmjs.org/tailwindcss | 2026-10-21 | §10 Bootstrap | estilos |
| vitest | 5.0.5 | https://registry.npmjs.org/vitest | 2026-10-21 | §10 Bootstrap | tests unitarios (engines `^22.12.0 \|\| ^24.0.0 \|\| >=26.0.0`) |
| jsdom | 30.1.3 | https://registry.npmjs.org/jsdom | 2026-10-21 | §10 Bootstrap | DOM de Vitest |
| @testing-library/react | 16.3.3 | https://registry.npmjs.org/@testing-library/react | 2026-10-21 | §10 Bootstrap | tests de componentes |
| @testing-library/jest-dom | 7.0.1 | https://registry.npmjs.org/@testing-library/jest-dom | 2026-10-21 | §10 Bootstrap | matchers (`/vitest` entry) |
| @testing-library/user-event | 14.6.8 | https://registry.npmjs.org/@testing-library/user-event | 2026-10-21 | §10 Bootstrap | interacción en tests |
| @playwright/test | 1.64.1 | https://registry.npmjs.org/@playwright/test | 2026-10-21 | §10 Bootstrap (paquete) · paso 2 `e2e:install` (Chromium; también en el paso 28 y §20.1) | E2E |
| @axe-core/playwright | 4.14.0 | https://registry.npmjs.org/@axe-core/playwright | 2026-10-21 | §10 Bootstrap | a11y en E2E |
| eslint / @eslint/js | 9.39.5 | https://registry.npmjs.org/eslint | 2026-10-21 | §10 Bootstrap | lint (no 10: jsx-a11y 6.10.2 declara peer eslint ≤ ^9) |
| typescript-eslint | 8.72.0 | https://registry.npmjs.org/typescript-eslint | 2026-10-21 | §10 Bootstrap | lint TS |
| eslint-plugin-react-hooks | 7.1.1 | https://registry.npmjs.org/eslint-plugin-react-hooks | 2026-10-21 | §10 Bootstrap | reglas de hooks |
| eslint-plugin-react-refresh | 0.5.7 | https://registry.npmjs.org/eslint-plugin-react-refresh | 2026-10-21 | §10 Bootstrap | HMR seguro |
| eslint-plugin-jsx-a11y | 6.10.2 | https://registry.npmjs.org/eslint-plugin-jsx-a11y | 2026-10-21 | §10 Bootstrap | a11y estático |
| globals | 17.13.0 | https://registry.npmjs.org/globals | 2026-10-21 | §10 Bootstrap | globals de ESLint |
| prettier | 3.9.10 | https://registry.npmjs.org/prettier | 2026-10-21 | §10 Bootstrap | formato |
| prettier-plugin-tailwindcss | 0.8.1 | https://registry.npmjs.org/prettier-plugin-tailwindcss | 2026-10-21 | §10 Bootstrap | orden de clases |
| Imagen `node:24-alpine` | 24 (24.22.1 "Krypton" LTS) | Docker Hub `library/node` (informe de pins) | 2026-10-21 | `Dockerfile` (paso 3) | build del frontend en la imagen |
| Imagen `eclipse-temurin:21-jdk` | 21-jdk (actualizada 2026-10-17) | Docker Hub `library/eclipse-temurin` (informe de pins) | 2026-10-21 | `Dockerfile` (paso 3) | build del jar en la imagen |
| GitHub Actions `actions/checkout`, `actions/setup-java`, `actions/setup-node` | `@v4` | UNVERIFIED — verify before install (no estaban en el informe de pins) | — | `.github/workflows/ci.yml` (paso 3) | CI; no es gate local |
| Node.js | 24 LTS | informe de pins | 2026-10-21 | lo instala el desarrollador (§10 Prerequisites); `.nvmrc` | runtime del frontend |
| JDK | 21 | informe de pins (Boot 4.1 soporta 17–26) | 2026-10-21 | lo instala el desarrollador (§10 Prerequisites) | runtime del backend |

### Deliberately not used

| Rejected | Instead | Why |
|---|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | renombrado en Boot 4 |
| `flyway-core` solo | `spring-boot-starter-flyway` | en Boot 4 sin el starter Flyway no se autoconfigura |
| `spring-boot-starter-data-jpa-test` | `spring-boot-starter-test` + Testcontainers | no verificado en el informe |
| `testcontainers-junit-jupiter` | `@TestConfiguration` + `@Bean @ServiceConnection` | no hace falta el módulo JUnit |
| `@MockBean` | `@MockitoBean` | removido en Boot 4 |
| `com.fasterxml.jackson.databind` | Jackson 3 (`tools.jackson`) | Boot 4 usa Jackson 3 |
| JWT / `jjwt` / refresh tokens | Spring Session JDBC | mismo origen; sesión revocable y persistente |
| `react-router-dom` | `react-router` 7 | paquete unificado |
| eslint 10, typescript 7 | eslint 9.39.5, typescript 6.0.3 | peers de jsx-a11y y typescript-eslint |
| recharts | tabla | Fase B (gráfico de ELO) |
| Thumbnailator | — | fotos en Fase B |
| Redux / Zustand | TanStack Query + estado local | no hay estado de cliente entre rutas |
| WebSockets / SSE | polling 5 s | Non-Goal |
| Actuator | `/api/health` propio | health sin DB y sin superficie extra en Render Free |
| Cloudinary | Postgres `bytea` (Fase B) | decisión tomada |

---

## 12. Deployment Strategy

### Hosting

- **App:** Render Web Service, plan **Free**, runtime **Docker** (`Dockerfile` en la raíz, contexto `.`), región **Virginia (US East)**, una instancia, `healthCheckPath: /api/health`. Definido en `render.yaml` (paso 3). Render inyecta `PORT` y `RENDER_GIT_COMMIT`. El servicio duerme tras ~15 min sin tráfico; el arranque en frío tarda ~50 s.
- **Base:** Neon Free, proyecto con **Postgres 17 elegido explícitamente** (el default de Neon no está verificado), región **AWS us-east-1** — la misma zona que Render Virginia: cada request hace varias queries y una base en otro continente sumaría ~120 ms por query. Neon escala a cero cuando Hikari no retiene conexiones (`minimum-idle: 0`).
- Build: la imagen compila el frontend (`npm ci && npm run build`) y luego el jar (`./mvnw -q -DskipTests package`); runtime `java -XX:MaxRAMPercentage=75 -jar /app/app.jar` como usuario `app`.

### Environments

| Environment | Branch | URL | Database | Third-party mode |
|---|---|---|---|---|
| Local | cualquiera | http://localhost:8080 (jar) · http://localhost:5173 (Vite dev) | `postgres:17` de compose en `localhost:5433` | — |
| Test | — | MockMvc | `postgres:17` efímero de Testcontainers | — |
| Preview | NOT APPLICABLE — Render Free + Neon Free: sin previews por PR en v1; CI corre los gates en cada PR | — | — | — |
| Production | `main` | `https://<servicio>.onrender.com` | Neon (branch `main`, Postgres 17) | — |

### CI/CD

Tier 0 de `deployment.md`. `.github/workflows/ci.yml` en cada push a `main` y en cada PR:
1. `backend`: `chmod +x mvnw && ./mvnw -B verify` (Testcontainers usa el Docker del runner).
2. `frontend` (en `frontend/`): `npm ci`, `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build`.

Deploy: Render con `autoDeploy: true` construye la imagen al pushear a `main`. Flyway migra al arrancar la nueva revisión; si la migración o el arranque fallan, el health check no pasa y Render mantiene la revisión anterior. El E2E (Playwright) es gate local del paso 28 y de §20.1, no de CI (necesita la base de compose y el jar).

### Release and rollback

- Release = merge a `main` → build y deploy automáticos (≈ 5–8 min en Free).
- Rollback = en Render → *Events* → *Rollback* a un deploy anterior (≈ 2–3 min, sin rebuild), o `git revert` + push. Las migraciones son forward-only: una revisión vieja tiene que tolerar el esquema nuevo, por eso los cambios de esquema siguen expand → migrate → contract en deploys separados.

### Domain, DNS, TLS

Se usa el subdominio `*.onrender.com` con TLS automático de Render. Sin dominio propio en v1 (no hay registros DNS que crear).

---

## 13. Testing Strategy

| Layer | Framework | What it covers | Where | Runs |
|---|---|---|---|---|
| Unit (puro) | JUnit 5 | `EloCalculator`, `TeamBalancer`, `AuthRateLimiter` (reloj falso) | `src/test/java/**/{elo,match/balance,auth}/*Test.java` | `./mvnw -q verify`, cada paso de backend, CI |
| Integration | JUnit 5 + Spring Boot Test + MockMvc + Testcontainers (`postgres:17`) | Cada endpoint contra una base real: auth, CSRF, tenancy (404), roles (403), reglas 409/422, libro ELO, replay, estadísticas, perfil prod | `src/test/java/**/*IntegrationTest.java`, `EloReplayPropertyTest.java` | `./mvnw -q verify`, CI |
| Component | Vitest + Testing Library + jsdom | Formularios accesibles, CSRF del cliente, redirecciones, estados vacío/error, armado de equipos sin arrastre, polling 5 s, deshacer | `frontend/src/**/*.test.tsx` | `npm --prefix frontend run test`, CI |
| E2E | Playwright (Chromium, 375×812) + `@axe-core/playwright` | Los 3 recorridos críticos contra el jar empaquetado + a11y + scroll horizontal + tamaño de botones | `frontend/e2e/flows.spec.ts` (+ `e2e/health.spec.ts`, emitido: contrato `webServer` ↔ jar desde el paso 2) | pasos 2 y 28, §20.1 (local) |

### Critical flows to cover E2E

1. Registro → crear grupo → agregar jugadores invitados.
2. Crear partido → convocar → armar y guardar equipos → empezar → cargar goles → cerrar → el ranking refleja el resultado.
3. Throttling de login: el 6.º intento tras 5 fallos muestra "Demasiados intentos fallidos".

### Test data

- Integración: Testcontainers levanta un `postgres:17` por contexto de Spring cacheado (`TestcontainersConfiguration`, §19.6); Flyway aplica V1+V2. Los tests no comparten datos: cada uno registra usuarios con `ApiTestClient.uniqueUsername(...)` y crea sus grupos, así no dependen del orden. `fm.auth.register-per-ip-per-hour` vale 100000 en la suite (Surefire) y 10 solo en `AuthThrottleIntegrationTest`.
- Vitest: sin red; `fetch` simulado por test; `src/test/setup.ts` limpia DOM, mocks, `localStorage` y cookies después de cada test.
- E2E: la base de compose (`docker compose up -d --wait db`, `.env`); cada corrida crea usuarios `e2e_<aleatorio>`; no se resetea la base entre corridas porque nada depende de su estado previo.

### What is deliberately not tested

Estilos y colores en tests funcionales (los cubre axe + la regla ESLint anti-hex); librerías (Spring Security, Hibernate, TanStack Query); el sleep/wake de Render y Neon (launch checklist); lectores de pantalla (pasada manual en §20.1); carga/concurrencia más allá del bloqueo `FOR UPDATE` del replay (decenas de grupos).

---

## 14. Security & Secrets

| Concern | Control | Implemented in |
|---|---|---|
| Secret storage | `.env` local (gitignored); variables de entorno de Render en prod; nada en el repo ni en la imagen (`.dockerignore` excluye `.env`) | `.gitignore`, `.dockerignore`, Render |
| Secret rotation | Neon: *Reset password* → actualizar `DB_PASSWORD` en Render → redeploy. Al rotar, todas las sesiones siguen válidas (no dependen del secreto de la base) | README (paso 28) |
| Input validation | Bean Validation en todos los requests + CHECK constraints en la base | records de los controllers, `V2__core_schema.sql` |
| Output encoding / XSS | React escapa todo; nunca `dangerouslySetInnerHTML`; CSP `script-src 'self'` en prod | frontend, `SecurityConfig` |
| SQL injection | JPA con parámetros y `JdbcClient` con parámetros nombrados; prohibido concatenar SQL | todos los repositorios |
| AuthN / AuthZ | §8: sesión server-side + `GroupAccess` en cada handler | `SecurityConfig`, `GroupAccess` |
| CSRF | `csrf.spa()`: cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` en todo no-GET | `SecurityConfig`, `lib/api.ts` |
| Rate limiting / abuse | login: 5 fallos por (usuario, IP) → bloqueo exponencial 30 s…900 s; 50 fallos por IP en 15 min; registro: 10/h por IP; 429 + `Retry-After`; en memoria | `AuthRateLimiter` |
| Webhook verification | NOT APPLICABLE — no hay webhooks entrantes | — |
| Dependency audit | `npm audit --omit=dev` en el frontend y revisión de Dependabot de GitHub, mensual, la hace el desarrollador | GitHub |
| Security headers | prod: `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'` · `Referrer-Policy: strict-origin-when-cross-origin` · `X-Content-Type-Options: nosniff` · `X-Frame-Options: DENY`; HSTS lo agrega Spring Security en respuestas HTTPS | `SecurityConfig`, `application-prod.yml` |
| PII handling | Se guarda username, nombre visible y hash BCrypt; nada más. Sin mails. Borrado de cuenta: Non-Goal v1 (se hace a mano en Neon si alguien lo pide) | `users` |
| Logging hygiene | nunca se loguean contraseñas, hashes, ids de sesión ni tokens CSRF; logs ECS JSON en prod; `server.error.include-stacktrace: never` | `application*.yml`, regla `backend-java.md` |
| Cookies | `FM_SESSION` HttpOnly + SameSite=Lax + Secure (prod) | `application*.yml` |
| Proxy headers | `server.forward-headers-strategy: none` por defecto (local no confía en ningún proxy) y `native` en `prod` (RemoteIpValve de Tomcat: recorre `X-Forwarded-For` desde la derecha y solo salta proxies internos) para IP real y esquema https detrás de Render | `application.yml`, `application-prod.yml` |

**Hard rules**
- No secret is ever committed, printed in a log, sent to an error tracker, or embedded in a client bundle. Anything reaching the browser is public — treat it that way.
- All server-side authorization checks run before the work, not after.
- Third-party webhooks are verified by signature before their body is parsed as trusted (no hay ninguno en v1).

No se manejan datos regulados (salud, finanzas, menores): solo usernames y nombres visibles elegidos por los usuarios.

---

## 15. Accessibility

**Target: WCAG 2.2 Level AA.** Accessibility criteria belong in Section 9 "Done when" lists, not in a someday backlog.

### Baseline requirements

| Requirement | Rule |
|---|---|
| Semantic HTML | `header`/`nav`/`main` en `AppShell`, un `h1` por página, headings en orden, listas para listas, tablas con `<th scope>` en ranking e historial |
| Keyboard | Todo operable con teclado; skip link "Saltar al contenido"; diálogos de `radix-ui` con foco atrapado, Escape y retorno del foco |
| Focus visible | `outline: 2px solid var(--fm-focus)` + offset 2px, ≥ 3:1 (§7) |
| Contrast | §7 (pares medidos; bordes de controles con `--fm-border-strong`) |
| Forms | `<label>` en todo input; errores en texto con `aria-invalid` + `aria-describedby`; resumen en `role="alert"`; foco al primer inválido |
| Images | Solo íconos `lucide-react` decorativos (`aria-hidden`) o con texto visible al lado |
| Motion | `prefers-reduced-motion: reduce` anula animaciones (`index.css`) |
| Zoom / reflow | Usable a 320 px y 200 % de zoom; E2E verifica sin scroll horizontal a 375 px |
| Live regions | Marcador `role="status"`; resultados de acciones en `Alert` |

### WCAG 2.2 additions — the ones most often missed

| SC | Requirement |
|---|---|
| 2.4.11 Focus Not Obscured (Min) | El marcador sticky del modo en vivo deja `scroll-padding-top` igual a su alto, así el foco nunca queda debajo |
| 2.5.7 Dragging Movements | No hay arrastre: los equipos se arman con botones "Pasar al equipo A/B" |
| 2.5.8 Target Size (Min) | ≥ 24×24 px en todo; botones del modo en vivo ≥ 64 px (medido en E2E) |
| 3.3.7 Redundant Entry | El link de invitación prellena el código; `next` vuelve a la ruta original tras login |
| 3.3.8 Accessible Authentication (Min) | Sin captchas; pegar y autocompletar permitidos en todos los campos de auth |

### Verification

```bash
npm --prefix frontend run lint      # eslint-plugin-jsx-a11y en nivel error — expect: exit 0
npm --prefix frontend run test:e2e  # axe en /ingresar, /registro, /grupos, detalle y vivo — expect: 0 serious/critical
```

Automated checks catch roughly a third of real issues. Add these manual passes before launch: keyboard-only traversal of every critical flow, one screen-reader pass over the primary flow (NVDA + Firefox), and a 200% zoom pass on the narrowest breakpoint — en §20.1.

---

## 16. Observability & Cost

### Instrumentation

| Signal | Tool | What it captures | Who looks at it |
|---|---|---|---|
| Errors | Logs de Render (sin error tracker en v1) | excepciones no manejadas: `ApiExceptionHandler` loguea `ERROR` con stacktrace y la ruta | el desarrollador, ante un reporte |
| Logs | Logging estructurado de Spring Boot, formato ECS JSON en prod (`logging.structured.format.console: ecs`) | requests fallidos, arranque, migraciones Flyway | el desarrollador |
| Metrics | NOT APPLICABLE — sin Actuator ni Prometheus en v1 (observabilidad mínima) | — | — |
| Uptime | UptimeRobot (opcional, launch checklist) | `GET /api/health` cada 10 min desde la nube de UptimeRobot | el desarrollador (mail de UptimeRobot) |

### The metrics that matter for this project

| Metric | Target | Alert at |
|---|---|---|
| Disponibilidad de `/api/health` | ≥ 99 % mensual | UptimeRobot: 2 chequeos fallidos seguidos |
| Primera respuesta en frío | < 60 s | > 90 s en la medición manual de §20.1 |
| Almacenamiento de Neon | < 0.25 GB | 0.4 GB (límite Free 0.5 GB) — consola de Neon |
| Horas de cómputo de Render | ≤ 744 h/mes (un servicio) | > 700 h a mitad de mes — dashboard de Render |

### Health check

`GET /api/health` devuelve `{"status":"UP","version":"<sha>"}` desde un controller sin dependencias inyectadas salvo la versión, sin tocar la base ni crear sesión. A propósito **no** verifica la base: así un pinger lo puede llamar sin despertar Neon, y Render no reinicia la app cuando Neon está escalado a cero (la primera query lo despierta). La salud de la base la prueban Flyway al arrancar y cualquier request real.

### Cost model

| Service | Free tier | Cost at v1 scale (decenas de grupos) | Cost at 10× | Cliff to watch |
|---|---|---|---|---|
| Render Web Service | 750 h/mes de instancia Free, sleep tras inactividad | USD 0 | USD 0 (mismo servicio) | necesitar sin cold start → plan Starter pago |
| Neon | 0.5 GB de almacenamiento, horas de cómputo limitadas, escala a cero | USD 0 | USD 0 (texto chico; sesiones limpiadas cada 6 h) | fotos (Fase B, ~25 KB c/u) o pinger que impida escalar a cero |
| GitHub Actions | minutos gratis para repos públicos | USD 0 | USD 0 | repo privado con muchos pushes |
| UptimeRobot | 50 monitores gratis | USD 0 | USD 0 | — |

**Estimated monthly cost at launch: USD 0.** El mayor riesgo de costo es el cómputo de Neon si algo mantiene conexiones abiertas: `minimum-idle: 0` y un health sin base son la palanca. Nada escala superlinealmente.

---

## 17. Model Routing

NOT APPLICABLE — this project does not call an LLM at runtime.

---

## 18. Skills to Use During Build

Nombres y comandos copiados de `knowledge/skills-registry.md`. Sin barra = se activa sola; se nombra en prosa. Si una skill no está instalada, el builder sigue con la guía de este blueprint y lo anota en una línea. Las skills `ecc:springboot-*` / `ecc:jpa-patterns` no figuran en el registro y por eso no se recomiendan.

| Skill | Build steps | Why | Install |
|---|---|---|---|
| frontend-design | 2, 6, 9, 13, 15, 17 | pantallas de producción con los tokens de §7 | `/plugin marketplace add anthropics/skills` then `/plugin install example-skills@anthropic-agent-skills` |
| ui-ux-pro-max | 2, 3 | revisar el sistema visual y las primitivas contra §7 | `/plugin marketplace add nextlevelbuilder/ui-ux-pro-max-skill` then `/plugin install ui-ux-pro-max@ui-ux-pro-max-skill` |
| playwright-cli | 18 | escribir y depurar `flows.spec.ts` y los chequeos axe | `npm install -g @playwright/cli@latest` then `playwright-cli install --skills` |

---

## 19. Agent Workspace

Modo bundle: cada artefacto de esta sección es un archivo real en `blueprints/futbol-manager/workspace/`, que replica el layout del proyecto. El builder lo copia **una sola vez** a la raíz con la línea guardada de §10 Bootstrap:

```bash
[ -f pom.xml ] || cp -r blueprints/futbol-manager/workspace/. .   # marker guard: skips (exit 0) once pom.xml exists, on GNU and BSD alike; never clobbers edited files
```

Elegimos el guard por marcador (y no `cp -Rn`) porque `cp -n` sale con 1 en BSD/macOS cuando omite un archivo y con 0 en GNU; `[ -f … ] ||` sale con 0 en ambos al saltear. Archivos que nunca se vuelven a pisar: `pom.xml`, `frontend/package.json`, `frontend/package-lock.json` y todo archivo emitido que un paso haya editado. Ningún paso de §9 edita un archivo emitido salvo que su **Do** lo diga.

`.claude/commands/` no se emite. Los procedimientos repetibles están en `.claude/skills/` (§19.4).

Todo archivo emitido pasa los gates del propio blueprint: Prettier y ESLint corren solo dentro de `frontend/` (config emitida: `frontend/.prettierrc.json` — 2 espacios, comillas simples, `trailingComma: all` salvo JSON, ancho 100, LF), y los archivos de `workspace/frontend/` están escritos en ese estilo; los archivos fuera de `frontend/` (Java, YAML, SQL, Markdown, `settings.json`) no tienen formatter en ningún gate. El primer `format:check` (paso 2) los verifica; la ejecución real del formatter sobre la copia la hace el hilo principal al correr Bootstrap.

### 19.1 `CLAUDE.md`

Contenido literal de `workspace/CLAUDE.md` (128 líneas):

````markdown
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
6. No implementar nada de la tabla de Non-Goals (blueprint §1): fotos, votación de MVP, reclamo de perfil, atributos entre compañeros o radar (el «nivel» único 1,0–10,0 —con un decimal— de cada jugador sí está en v1), gráficos, amigos, mail, OAuth, WebSockets.
7. Nunca marcar una tarea como hecha con un gate en rojo, ni editar un comando `verify`.
````

### 19.2 `AGENTS.md`

Contenido literal de `workspace/AGENTS.md`:

````markdown
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
````

### 19.3 `.claude/settings.json`

Cubre cada subcomando de cada `Verify` de §9, de §10 Bootstrap y de §20.1 (partidos en `&&`, `||`, `;`, `|` y dentro de `$( )`). `grep`, `tail` y `git` de solo lectura son de lectura; `test`/`[` tienen entrada propia. Sin intérpretes desnudos, sin `curl:*`, sin comodines de `npx` ni `sh -c` (los `curl` viven dentro de los scripts emitidos, que se aprueban por su ruta exacta).

```json
{
  "permissions": {
    "allow": [
      "Bash(./mvnw -q verify)",
      "Bash(./mvnw -q test:*)",
      "Bash(./mvnw -q -DskipTests package)",
      "Bash(npm --prefix frontend install)",
      "Bash(npm --prefix frontend ci)",
      "Bash(npm --prefix frontend run format:check)",
      "Bash(npm --prefix frontend run format)",
      "Bash(npm --prefix frontend run lint)",
      "Bash(npm --prefix frontend run typecheck)",
      "Bash(npm --prefix frontend run test)",
      "Bash(npm --prefix frontend run build)",
      "Bash(npm --prefix frontend run e2e:install)",
      "Bash(npm --prefix frontend run test:e2e)",
      "Bash(npm --prefix frontend run dev)",
      "Bash(bash scripts/smoke.sh:*)",
      "Bash(bash scripts/docker-smoke.sh)",
      "Bash(SPRING_PROFILES_ACTIVE=demo bash scripts/smoke.sh)",
      "Bash(docker compose up -d --wait db)",
      "Bash(docker compose down)",
      "Bash(docker compose ps:*)",
      "Bash(docker compose exec -T db psql -U futbol -d futbol -tAc:*)",
      "Bash(docker build -t futbol-manager:local .)",
      "Bash(cd target)",
      "Bash(env -u DB_URL DB_USERNAME=x DB_PASSWORD=x java -jar futbol-manager.jar:*)",
      "Bash(test:*)",
      "Bash([ -f pom.xml ])",
      "Bash([ -f .env ])",
      "Bash([ -f mvnw ])",
      "Bash(grep:*)",
      "Bash(tail -n 1)",
      "Bash(cp -r blueprints/futbol-manager/workspace/. .)",
      "Bash(cp .env.example .env)",
      "Bash(mvn -N -q org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.16)",
      "Bash(git status:*)",
      "Bash(git diff:*)",
      "Bash(git log:*)",
      "Bash(git tag:*)",
      "Bash(git add -A)",
      "Bash(git commit -m:*)",
      "Bash(git commit -q -m:*)",
      "Bash(git rev-parse:*)",
      "Bash(git rev-list:*)",
      "Bash(git ls-files:*)",
      "Bash(git check-ignore:*)",
      "Bash(git init -b main)",
      "Bash(git update-index --chmod=+x mvnw)"
    ],
    "deny": [
      "Read(./.env)",
      "Bash(git push:*)",
      "Bash(docker compose down -v)"
    ]
  }
}
```

Las reglas `deny` desalientan, no aíslan: un script que abre `.env` por su cuenta (como `docker-smoke.sh`, a propósito) no queda bloqueado. Los secretos de producción nunca están en la máquina de desarrollo.

### 19.4 Project skills — `.claude/skills/<name>/SKILL.md`

| Skill | Triggers on | What it automates |
|---|---|---|
| `add-migration` | "add a column", "new table", "change the schema", "migration" | migración Flyway nueva `V{n}__…` + entidad + test, sin tocar V1/V2 |
| `add-group-endpoint` | "new endpoint", "add an API route", "expose X for a group" | endpoint bajo `/api/groups/{groupId}/**` con `GroupAccess`, DTOs anidados y su test 404/403/400 |

`workspace/.claude/skills/add-migration/SKILL.md`:

````markdown
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
````

`workspace/.claude/skills/add-group-endpoint/SKILL.md`:

````markdown
---
name: add-group-endpoint
description: Add a REST endpoint that reads or writes data owned by a group (players, matches, events, stats). Use for "new endpoint", "add an API route", "expose X for a group".
---

# Add a group-scoped endpoint

## When to use
Any route under `/api/groups/{groupId}/**`.

## Steps
1. Put the handler in the feature's `*Controller` with request/response DTOs as nested `record`s and
   Bean Validation annotations on the request record (`@Valid @RequestBody`).
2. First line of the handler: `groupAccess.requireMember(groupId, user)` (or `requireAdmin`), where
   `user` is `@AuthenticationPrincipal AuthUser user`. A non-member gets 404, never 403.
3. Load nested resources by `(id, groupId)` — e.g. `matchRepo.findByIdAndGroupId(matchId, groupId)` —
   and throw `ApiException.notFound("Partido no encontrado.")` when empty.
4. Errors via `ApiException` with a Spanish `detail`; validation is handled by `ApiExceptionHandler`.
5. Add the route to blueprint §5 conventions if it introduces a new status code.
6. Integration test with `ApiTestClient`: happy path, a non-member (404), a member without the role
   (403 when the route is admin-only), and one validation failure (400 with `errors[].field`).

## Verify
```bash
./mvnw -q test -Dtest=<YourFeature>IntegrationTest   # expect: exit 0
./mvnw -q verify                                       # expect: exit 0
```

## Do not
- Return 403 for another group's resource.
- Query group data without `groupId` in the `WHERE` clause.
- Return JPA entities directly from a controller.
````

### 19.5 `.claude/rules/*.md`

| File | `paths` globs | Covers |
|---|---|---|
| `.claude/rules/backend-java.md` | `src/main/java/**`, `src/test/java/**` | paquetes, repos anidados, controllers con lógica, tenancy, errores, Jackson 3 |
| `.claude/rules/database.md` | `src/main/resources/db/migration/**`, `src/main/java/com/futbolmanager/elo/**`, `src/main/java/com/futbolmanager/**/*.java` | Flyway, convenciones de esquema, libro ELO |
| `.claude/rules/frontend.md` | `frontend/src/**`, `frontend/e2e/**` | imports, TanStack Query, `apiFetch`, tokens, a11y, copy en español |
| `.claude/rules/testing.md` | `src/test/**`, `frontend/src/**/*.test.tsx`, `frontend/src/**/*.test.ts`, `frontend/e2e/**` | Testcontainers, aislamiento de datos, Vitest sin globals, axe |

Los cuatro archivos se emiten completos en `workspace/.claude/rules/` (son reglas diferidas: solo se cargan al tocar archivos de sus globs); su contenido es el de esos archivos y no se repite acá para no duplicar ~120 líneas que el builder nunca lee desde el blueprint.

### 19.6 Verify-critical config and local infrastructure

Todos los archivos de esta tabla existen como archivos reales y completos bajo `workspace/`, en la ruta que ocupan en el proyecto; Bootstrap los copia antes del paso 1. Ningún paso los crea; los que un paso edita lo dicen en su **Do** (ninguno de esta tabla se edita).

| File | Path in the project | Which `Verify` commands need it | Resolution/env handling it carries | Bundle-path exclusion |
|---|---|---|---|---|
| Maven POM | `pom.xml` | todos los `./mvnw …` (pasos 1–5, 7, 8, 10–12, 14, 16–28) | Surefire `argLine` `-Duser.timezone=UTC` (ver paso 1: zona legacy de Windows que postgres:17 rechaza) y `systemPropertyVariables`: `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` placeholder (los reemplaza `@ServiceConnection`) y `fm.auth.register-per-ip-per-hour=100000`; `finalName` `futbol-manager` | n/a — Maven compila solo `src/` del módulo raíz y no tiene `<modules>`; `blueprints/` nunca entra al build |
| App config | `src/main/resources/application.yml` | jar en `scripts/smoke.sh` (pasos 1, 2, 28), Playwright (2, 28), todos los tests | `spring.config.import: optional:file:.env[.properties]` (loader de env de la app, relativo al cwd) | n/a — no recorre el árbol |
| Migración sesiones | `src/main/resources/db/migration/V1__spring_session.sql` | paso 1 (`to_regclass`), toda app/test | — | n/a |
| Migración esquema | `src/main/resources/db/migration/V2__core_schema.sql` | paso 1 y todo test de integración | — | n/a |
| Testcontainers | `src/test/java/com/futbolmanager/TestcontainersConfiguration.java` | todo `*IntegrationTest`, `EloReplayPropertyTest` | `@ServiceConnection` sobre `postgres:17` (Docker requerido) | n/a |
| Cliente de test | `src/test/java/com/futbolmanager/support/ApiTestClient.java` | todos los tests de integración | CSRF vía `SecurityMockMvcRequestPostProcessors.csrf()`, cookie `FM_SESSION`, IP configurable | n/a |
| Compose | `docker-compose.yml` | `scripts/smoke.sh`, `scripts/docker-smoke.sh`, paso 28 (psql), Bootstrap | lee `.env` nativamente (`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`); imagen `postgres:17`, volumen nombrado, healthcheck `pg_isready` | n/a — no recorre el árbol |
| Env local | `.env.example` (→ `.env` en Bootstrap) | todo lo que arranca el jar o compose | valores locales literales | n/a |
| Ignore de git | `.gitignore` | Checkpoints y §20.1 | — | n/a — el bundle se commitea a propósito |
| Atributos de git | `.gitattributes` | `mvnw` en Docker/CI (LF) | — | n/a |
| Ignore de Docker | `.dockerignore` | paso 3 `docker build`, §20.1 | — | `blueprints/` (línea literal) |
| Node | `.nvmrc` | CI (`node-version-file`) | — | n/a |
| Smoke del jar | `scripts/smoke.sh` | pasos 1, 2, 28 y §20.1 | ejecuta el jar desde la raíz (carga `.env` por la app) | n/a |
| Smoke de la imagen | `scripts/docker-smoke.sh` | paso 3 y §20.1 | `set -a; . ./.env; set +a` + `--env-file .env` + override de `DB_URL` a `host.docker.internal:5433` | n/a |
| Manifiesto npm | `frontend/package.json` | todos los `npm --prefix frontend run …` | versiones exactas; `"type": "module"` | n/a (sin workspaces npm) |
| TS raíz | `frontend/tsconfig.json` | `typecheck` (`tsc -b`) | referencias a app/node | n/a (`files: []`) |
| TS app | `frontend/tsconfig.app.json` | `typecheck` | `moduleResolution: bundler`, `types: ["vite/client"]` (declara `*.css`, requerido por `noUncheckedSideEffectImports`) | `"exclude": ["**/blueprints/**"]` |
| TS node | `frontend/tsconfig.node.json` | `typecheck`, `test:e2e` (pasos 2, 28) | `lib: ["ES2023", "DOM"]` (los specs usan `document` dentro de `page.evaluate`); `types: ["node"]`; incluye `vite.config.ts`, `playwright.config.ts`, `e2e` | `"exclude": ["**/blueprints/**"]` |
| Vite + Vitest | `frontend/vite.config.ts` | `build`, `test`, `dev` | `build.outDir: '../src/main/resources/static'`, `emptyOutDir: true`; `test.environment: 'jsdom'`, `setupFiles`; proxy `/api` → `http://localhost:8080`; Vitest reutiliza el resolver de Vite | `test.exclude` incluye `'**/blueprints/**'` |
| Setup de tests | `frontend/src/test/setup.ts` | `test` | jest-dom (`@testing-library/jest-dom/vitest`), cleanup, stub de `matchMedia`; no lee env | n/a |
| ESLint | `frontend/eslint.config.js` | `lint` | flat config ESLint 9; regla anti-hex | `ignores` incluye `'**/blueprints/**'` |
| Prettier | `frontend/.prettierrc.json` + `frontend/.prettierignore` | `format:check` | plugin Tailwind con `tailwindStylesheet: ./src/styles/index.css` | `.prettierignore` incluye `**/blueprints/**` |
| HTML de entrada | `frontend/index.html` | `build` | carga `/theme-init.js` síncrono y `/src/main.tsx` | n/a |
| Tema antes del paint | `frontend/public/theme-init.js` | `build` (paso 2: `test -f …/static/theme-init.js`) | — | n/a |
| Tokens | `frontend/src/styles/tokens.css` | `build`, `format:check` | — | n/a |
| Estilos + `@theme` | `frontend/src/styles/index.css` | `build`, `format:check` (stylesheet del plugin de Prettier) | importa `tailwindcss`, las fuentes Fontsource y `tokens.css` | n/a |
| Spec de health E2E | `frontend/e2e/health.spec.ts` | `test:e2e` (pasos 2 y 28, §20.1) | solo el fixture `request` (sin navegador); `baseURL` del config | n/a |
| Playwright | `frontend/playwright.config.ts` | `test:e2e` (pasos 2 y 28, §20.1) | `webServer.command: 'java -jar target/futbol-manager.jar'`, `cwd: '..'` (raíz → el jar carga `.env`), `url: http://localhost:8080/api/health` | `testIgnore: ['**/blueprints/**']` |
| Workspace del agente | `CLAUDE.md`, `AGENTS.md`, `.claude/**` | permisos de todos los gates | — | n/a |

**Byte-exact / golden files:** ninguno (ver tabla al final).

#### Resolution convention matrix

**The convention, stated once:**
- **Java:** repositorios Spring Data declarados como interfaces anidadas dentro de su entidad (`User.Repo`), descubiertos por `@EnableJpaRepositories(considerNestedRepositories = true)` en `com.futbolmanager.FutbolManagerApplication`; escaneo de componentes y entidades desde el paquete raíz `com.futbolmanager`.
- **TypeScript:** specifiers relativos **sin extensión** (`../lib/api`), sin path aliases, sin barrels; imports de CSS como side-effect (`import './styles/index.css'`).

| Context | Command that exercises it | Convention as it appears there | Config + literal setting that makes it work |
|---|---|---|---|
| Application source (Java) | `./mvnw -q -DskipTests package` + `bash scripts/smoke.sh` | `interface Repo extends JpaRepository<…>` anidada | `FutbolManagerApplication.java` (paso 1) — `@EnableJpaRepositories(considerNestedRepositories = true)`; `pom.xml` — parent Boot 4.1.1, `java.version` 21 |
| Test files (Java) | `./mvnw -q verify` | mismos repos anidados; `@Import(TestcontainersConfiguration.class)` | el contexto de test arranca la misma `@SpringBootApplication` (misma anotación); Surefire del parent compila `src/test/java` |
| Application source (TS) | `npm --prefix frontend run build`, `npm --prefix frontend run typecheck` | `import { apiFetch } from '../lib/api'` | `tsconfig.app.json` — `"moduleResolution": "bundler"` (acepta extensionless), `"types": ["vite/client"]` (resuelve `*.css`); Vite resuelve extensionless por defecto |
| Test files (TS) | `npm --prefix frontend run test` | idéntica | `vite.config.ts` — Vitest usa el mismo resolver de Vite; `test.setupFiles: ['./src/test/setup.ts']` |
| Standalone scripts | `bash scripts/smoke.sh`, `bash scripts/docker-smoke.sh` | no importan módulos del proyecto (bash puro) | n/a por diseño: el proyecto no tiene scripts TS/Node que importen `src/` |
| E2E specs | `npm --prefix frontend run test:e2e` | `frontend/e2e/*.spec.ts` importan solo paquetes (`@playwright/test`, `@axe-core/playwright`), nunca `src/` | Playwright transpila TS con su propio loader; `tsconfig.node.json` los incluye para `typecheck` |
| Build / bundle (imagen) | `docker build -t futbol-manager:local .` | igual a las dos filas "Application source" | `Dockerfile` (paso 3) corre los mismos `npm run build` y `./mvnw -q -DskipTests package` |
| Lint | `npm --prefix frontend run lint` | ESLint no resuelve imports (sin `eslint-plugin-import`) | `eslint.config.js` — sin resolver |

#### Cross-artifact value reconciliation

| Shared value | Single source — the file that decides it | Literal value | Every other place it appears | Compared |
|---|---|---|---|---|
| Jar construido | `pom.xml` — `<finalName>futbol-manager</finalName>` (+ packaging jar) | `target/futbol-manager.jar` | `scripts/smoke.sh` (`test -f` y `java -jar`) · `frontend/playwright.config.ts` `webServer.command` (`target/futbol-manager.jar`, cwd raíz) · `Dockerfile` (`/app/target/futbol-manager.jar`) · paso 3 Verify (`futbol-manager.jar` con cwd `target`) · §3 · §19.3 | yes |
| Salida del frontend | `frontend/vite.config.ts` — `build.outDir` | `../src/main/resources/static` (= `src/main/resources/static/` desde la raíz) | `.gitignore` · `.dockerignore` · `Dockerfile` (copia `/app/src/main/resources/static`) · `SpaWebConfig` (`classpath:/static/`) · paso 2 Verify (`src/main/resources/static/theme-init.js`) · §3 | yes |
| Puerto de la app | `application.yml` — `server.port: ${PORT:8080}` | `8080` | `scripts/smoke.sh` (default) · `playwright.config.ts` (`baseURL`, `url`) · `vite.config.ts` proxy `http://localhost:8080` · `Dockerfile` `EXPOSE 8080` · `docker-smoke.sh` `-p 8081:8080` | yes |
| Puerto de host de la base | `docker-compose.yml` — `"5433:5432"` | `5433` | `.env.example` `DB_URL=jdbc:postgresql://localhost:5433/futbol` · `docker-smoke.sh` (`host.docker.internal:5433`) · §10 | yes |
| Nombre/usuario de la base local | `.env.example` — `DB_NAME=futbol`, `DB_USERNAME=futbol` | `futbol` | `DB_URL` (`/futbol`) · paso 28 Verify (`psql -U futbol -d futbol`) · §19.3 (`docker compose exec -T db psql -U futbol -d futbol -tAc:*`) | yes |
| Servicio de compose | `docker-compose.yml` — `services.db` | `db` | `docker compose up -d --wait db` (Bootstrap, scripts, §19.1) · `docker compose exec -T db` (paso 28) | yes |
| Imagen local | paso 3 Verify — `docker build -t futbol-manager:local .` | `futbol-manager:local` | `scripts/docker-smoke.sh` · §20.1 · §19.3 · §19.1 | yes |
| Imagen Postgres | `docker-compose.yml` — `image: postgres:17` | `postgres:17` | `TestcontainersConfiguration.java` (`DockerImageName.parse("postgres:17")`) · §11 | yes |
| Ruta de health | `HealthController` (paso 1) — `GET /api/health` | `/api/health` | `smoke.sh` · `docker-smoke.sh` · `playwright.config.ts` `url` · `render.yaml` `healthCheckPath` · `SecurityConfig` `permitAll` | yes |
| Cookie de sesión | `application.yml` — `server.servlet.session.cookie.name` | `FM_SESSION` | `ApiTestClient.SESSION_COOKIE` · criterios de pasos 1 y 4 · §8 | yes |
| Cookie/header CSRF | Spring Security `csrf.spa()` (defaults de `CookieCsrfTokenRepository`) | `XSRF-TOKEN` / `X-XSRF-TOKEN` | `frontend/src/lib/api.ts` (paso 6) · criterios de CSRF de los pasos 4 y 6 · §8 | yes |
| Clave de tema | `frontend/public/theme-init.js` — `'fm-theme'` | `fm-theme` | `AppShell.tsx` (paso 2) · criterio del paso 2 · §19.1 | yes |
| Stylesheet de Tailwind | `frontend/.prettierrc.json` — `tailwindStylesheet` | `./src/styles/index.css` | `frontend/src/main.tsx` (`import './styles/index.css'`, paso 2) | yes |
| Major de Node | `.nvmrc` | `24` | `Dockerfile` `node:24-alpine` · CI `node-version-file: .nvmrc` · `package.json` `engines.node` `>=24 <25` | yes |
| Límite de registro | `application.yml` — `fm.auth.register-per-ip-per-hour: 10` | `10` (prod/dev) · `100000` (suite) · `10` (`AuthThrottleIntegrationTest`) | `pom.xml` Surefire · paso 5 | yes |
| Ruta del bundle | ubicación del bundle | `blueprints/` | `.dockerignore` · `**/blueprints/**` en `tsconfig.app.json`, `tsconfig.node.json`, `vite.config.ts` (`test.exclude`), `eslint.config.js`, `.prettierignore`, `playwright.config.ts` | yes |

Contrato más temprano en que cada par existe junto, y su gate: jar ↔ `smoke.sh` — paso 1 (lo corre); salida de Vite ↔ jar ↔ `SpaWebConfig` — paso 2 (smoke de `/` y `/grupos`); imagen ↔ `docker-smoke.sh` ↔ puerto 5433 — paso 3; health ↔ `render.yaml` — paso 3 (`grep` + docker-smoke); cookie de sesión ↔ `ApiTestClient` — paso 4; Playwright `webServer` ↔ jar ↔ `.env` — paso 2 (`test:e2e` con `e2e/health.spec.ts`, emitido).

#### Byte-exact artifact reconciliation

NOT APPLICABLE — this blueprint authors no byte-exact expected output. Ningún `Verify` hace `diff` contra un literal; los mensajes en español que verifican los tests (`Usuario o contraseña incorrectos.`, `Ese nombre de usuario ya está en uso.`, `Demasiados intentos fallidos…`) los define este blueprint (§5) y los produce el propio código del proyecto, no el runtime.

---

## 20. Acceptance Gate, Risks & Decision Log

### 20.1 Global acceptance gate

The project is **done** when every command below exits 0 on a clean checkout (después de correr §10 Bootstrap, con Docker corriendo), and not before. CI corre el subconjunto sin servicios locales (`./mvnw -B verify` y los cinco gates de frontend).

```bash
npm --prefix frontend ci                  # expect: exit 0 — instala exactamente el lockfile commiteado
npm --prefix frontend run format:check    # expect: exit 0
npm --prefix frontend run lint            # expect: exit 0, 0 warnings
npm --prefix frontend run typecheck       # expect: exit 0
npm --prefix frontend run test            # expect: exit 0, 0 failed
npm --prefix frontend run build           # expect: exit 0 — escribe src/main/resources/static/
./mvnw -q verify                          # expect: exit 0, 0 failed — y empaqueta target/futbol-manager.jar con el frontend
bash scripts/smoke.sh / '<div id="root"></div>' /grupos '<div id="root"></div>'
                                          # expect: exit 0 — corre el jar que declara pom.xml (§9 rule 13)
docker build -t futbol-manager:local .    # expect: exit 0
bash scripts/docker-smoke.sh              # expect: exit 0 — imagen prod: health 200, no-root, Swagger 404
npm --prefix frontend run e2e:install     # expect: exit 0
npm --prefix frontend run test:e2e        # expect: exit 0 — 4 tests (health + 3 recorridos), axe 0 serious/critical
```

**Every expectation above is a property, not a tally.** **Every line above exits 0 on a correct build.** **And every line that fails, fails for the reason it claims** — ninguna condición es "cualquier código distinto de 0".

Plus these manual gates — the post-build launch checklist — each checked once before launch:

- [ ] Cada paso de §9 tiene su tag: `git tag -l 'step-*'` lista `step-01-backend-skeleton` … `step-28-hardening-e2e` (uno por paso). El repositorio lo crea §10 Bootstrap.
- [ ] Cada archivo de §10 *Files that must be committed* está trackeado, una ruta por invocación: `git ls-files --error-unmatch .env.example`, `git ls-files --error-unmatch .claude/settings.json`, `git ls-files --error-unmatch mvnw`, `git ls-files --error-unmatch mvnw.cmd`, `git ls-files --error-unmatch .mvn/wrapper/maven-wrapper.properties`, `git ls-files --error-unmatch frontend/package-lock.json`, `git ls-files --error-unmatch src/main/resources/db/migration/V2__core_schema.sql`, `git ls-files --error-unmatch scripts/smoke.sh` — cada uno exit 0. Y ninguno ignorado, uno por ruta: `status=0; git check-ignore -q .env.example || status=$?; test "$status" -eq 1` (repetir para `.claude/settings.json` y `frontend/package-lock.json`; 1 = no ignorado · 128 = error de uso, y eso falla).
- [ ] `.env` no está trackeado: `status=0; git ls-files --error-unmatch .env || status=$?; test "$status" -eq 1` (1 = no trackeado).
- [ ] El ignore file estuvo antes del primer commit: `test "$(git log --diff-filter=A --format=%H -- .gitignore | tail -n 1)" = "$(git rev-list --max-parents=0 HEAD)"` exit 0.
- [ ] `mvnw` es ejecutable en git: `git ls-files -s mvnw` empieza con `100755`.
- [ ] §19.6 *Byte-exact artifact reconciliation* es NOT APPLICABLE (no hay golden files).
- [ ] §10 Bootstrap re-ejecutado una vez sobre el árbol ya construido **salió con 0**, no pisó `pom.xml` ni `frontend/package.json` (`git status --porcelain` vacío después) y `./mvnw -q verify` sigue en verde.
- [ ] Todas las filas de §19.6 *Cross-artifact value reconciliation* dicen `Compared: yes`, y los gates de arriba corrieron desde la raíz **con el bundle presente**.
- [ ] Ningún Non-Goal de §1 fue construido.
- [ ] **Neon:** proyecto creado con **Postgres 17** (elegido explícitamente), región **AWS us-east-1**; copiar host, base, usuario y contraseña.
- [ ] **Render:** *New → Blueprint* desde el repo (lee `render.yaml`) o Web Service Docker Free en Virginia; cargar `DB_URL=jdbc:postgresql://<neon-host>/<db>?sslmode=require`, `DB_USERNAME`, `DB_PASSWORD` (`SPRING_PROFILES_ACTIVE=prod` ya viene de `render.yaml`); ninguna de esas variables está en el repo.
- [ ] Primer deploy verde y smoke: `curl -s https://<app>.onrender.com/api/health` devuelve `"status":"UP"` y el `version` es el SHA del commit; `curl -s -o /dev/null -w '%{http_code}' https://<app>.onrender.com/v3/api-docs` devuelve `404`.
- [ ] Respuesta en frío tras 20 min sin tráfico: `curl -s -o /dev/null -w '%{time_total}' https://<app>.onrender.com/api/health` < 60 s.
- [ ] Los 3 flujos de §13 completados a mano en la URL de producción desde un celular (375 px).
- [ ] Opcional: UptimeRobot HTTP monitor a `https://<app>.onrender.com/api/health` cada 10 min (Render Free: 750 h/mes ≥ 744 h de un mes de 31 días; el health no toca la base, así Neon igual puede escalar a cero).
- [ ] Opcional: sembrar la demo en prod con `DEMO_PASSWORD` y `SPRING_PROFILES_ACTIVE=prod,demo` en un deploy, y volver a `prod`.
- [ ] Pasada solo-teclado por los 3 flujos y una pasada con lector de pantalla (NVDA + Firefox) del flujo de partido completo, con notas en el README (§15).
- [ ] Un error forzado (p. ej. `PUT` con JSON inválido a un endpoint) aparece en los logs de Render como una línea JSON ECS (§16; no hay error tracker en v1).
- [ ] Un rollback ejecutado a propósito una vez desde *Events → Rollback* de Render, y el health vuelve a `UP` (§12).

**No warnings are ignored.**

### 20.2 Risk register

| Risk | Likelihood | Impact | Early signal | Mitigation |
|---|---|---|---|---|
| Cold start de Render Free (~50 s) + wake de Neon hacen lenta la primera carga de la demo | H | M | la medición de §20.1 > 60 s; reclutador ve pantalla en blanco | health sin base; pinger opcional cada 10 min; nota en el README; `RequireAuth` muestra esqueleto, no pantalla vacía (paso 28 / launch checklist) |
| Error en el recálculo ELO (orden, doble aplicación, INITIAL tocadas) | M | H | `EloReplayPropertyTest` o `EloReplayIntegrationTest` en rojo; `current_elo` ≠ libro | un único camino (cerrar = replay), `SELECT … FOR UPDATE` del grupo, test de propiedad con semilla fija (paso 26) |
| Novedad de Spring Boot 4 / Testcontainers 2 / springdoc 3 (cambios de paquetes, APIs removidas) | M | M | errores de compilación de imports en el paso 1 o 4 (`AutoConfigureMockMvc`, `PostgreSQLContainer`, `csrf.spa()`) | pins verificados; los pasos 1 y 4 los ejercitan primero; si una clase cambió de paquete, el builder usa la del artefacto pineado sin cambiar la versión |
| Throttling en memoria: se resetea al reiniciar y no escala a 2 instancias | H (reinicios en Free) | L | — | aceptado y documentado; multi-instancia es Non-Goal; mover a tabla/Redis si cambia |
| Spoofing de `X-Forwarded-For` debilita el límite por IP (con `framework` el valor de la izquierda lo controla el cliente) | B | M | muchos 401 desde IPs variadas en logs | resuelto en E1-T5: `none` por defecto y `native` en `prod`, con `LocalClientIpIntegrationTest` y `ProdClientIpIntegrationTest`; si Render usara proxies fuera de rangos privados, ajustar `server.tomcat.remoteip.internal-proxies` |
| Límites de Neon Free (0.5 GB, horas de cómputo) | L | M | consola de Neon > 0.4 GB o cómputo cerca del tope | `minimum-idle: 0`, limpieza de sesiones cada 6 h, health sin base; fotos (Fase B) acotadas a ~25 KB |
| Node 25 local vs Node 24 fijado | M | L | warning `EBADENGINE` en `npm install`; diferencias de comportamiento raras | `.nvmrc`; Docker y CI usan 24; recomendar nvm-windows/fnm (§10) |
| `kill` de un `java.exe` lanzado desde Git Bash no lo detiene en alguna máquina Windows | L | M | segundo `smoke.sh` falla porque el puerto 8080 sigue ocupado | `scripts/smoke.sh` usa `kill` + `wait` en el `trap`; si persiste, `SMOKE_PORT` evita el choque y se cierra el proceso a mano; verificado por la re-ejecución de §20.1 |

### 20.3 Decision log

| # | Decision | Rejected alternative | Why | Would reverse if |
|---|---|---|---|---|
| 1 | Java 21 + Spring Boot 4.1 (desvío sin runtime track) | Spring Boot 3.5 / TypeScript track | restricción del usuario; 3.5 sin soporte OSS desde 2026-06-30; 4.1 soportado hasta 2027-07-31 | una dependencia crítica no soporta Boot 4 |
| 2 | Sesión server-side (Spring Session JDBC) + CSRF SPA | JWT + refresh rotativo | mismo origen; revocable; sobrevive reinicios de Render | aparece una app nativa o un cliente de terceros |
| 3 | PostgreSQL 17 en Neon Free (us-east-1) | Render Postgres Free / Supabase | escala a cero, free sin vencimiento de 30 días; misma zona que Render Virginia | Neon cambia su free tier o la latencia app↔DB supera 20 ms |
| 4 | Render Free con Docker | Fly.io / Railway | gratis con `render.yaml`; Docker reproduce local | se necesita evitar el cold start (plan pago) |
| 5 | JPA para CRUD + `JdbcClient` para libro ELO y stats | todo JPA | orden explícito y SQL agregado sin N+1 | el replay se vuelve un cuello de botella medido |
| 6 | Esquema completo en `V2__core_schema.sql` emitido (no migraciones por paso) | una migración por feature en cada paso | `ddl-auto: validate` desde el paso 1 y pasos ≤ 5 archivos | — (cambios futuros van en V3+) |
| 7 | Repositorios anidados en la entidad (`considerNestedRepositories`) | un archivo por repositorio | mantiene cada paso ≤ 5 archivos sin perder cohesión | el equipo crece y prefiere la convención estándar |
| 8 | SPA Vite servida por el jar (mismo origen) | SSR / dos servicios | sin SEO; un solo deploy; sin CORS | se agrega una superficie pública indexable |
| 9 | Tailwind 4 + tokens + primitivas copy-in sobre `radix-ui` | kit de componentes con estilos | accesibilidad de Radix con diseño propio | — |
| 10 | TanStack Query + polling 5 s, sin store global | WebSockets / Redux | modo en vivo con 1–3 anotadores; polling barato | se mide latencia de polling inaceptable |
| 11 | ELO con libro contable y replay desde el partido afectado | delta acumulado sin libro | correcciones y anulaciones exactas y auditables | el replay de un grupo supera 2 s |
| 12 | Throttling en memoria sin bloqueo de cuenta | lockout de cuenta / Redis | nadie puede bloquear la cuenta de otro; una instancia | multi-instancia |
| 13 | Region de Neon us-east-1 en vez de São Paulo | sa-east-1 (pedido original) | Render no tiene región en Sudamérica; la app está en Virginia y cada request hace varias queries | Render agrega una región en Sudamérica |
| 14 | E2E local contra el jar (no en CI) | Playwright en CI | necesita base + jar; CI corre todos los demás gates | se agrega un job con servicio Postgres |
| 15 | Arqueros rotativos: el puesto es informativo; atajada de cualquier jugador (+1, tope +3), jugadón (+1, tope +2) y valla invicta +2 para todo el equipo | puesto fijo con arquero único y valla solo para GK/DEF | en grupos amateur el arquero rota y el puesto por partido no reflejaba quién defendió | el grupo vuelve a tener arqueros fijos |
| 16 | Nivel de jugador 1,0–10,0 (un decimal, guardado en décimas enteras) asignado por el admin, visible solo para admins; el balanceo minimiza la diferencia de nivel y el ELO solo desempata | balancear por ELO | el ELO se mueve con los resultados y no representa el nivel percibido; ocultarlo a los miembros evita fricción entre amigos | el nivel por admin resulta arbitrario o se pide puntuación entre compañeros (Non-Goal §1) |

### 20.4 What to build next

1. **Votación de MVP 48 h** (fila `MVP_BONUS`) — cuando haya ≥ 3 grupos activos y quejas del MVP a dedo.
2. **Reclamo de perfil invitado con código** — cuando un grupo real pida fusionar un invitado con su cuenta.
3. **Gráfico de historial de ELO (Recharts)** — cuando el endpoint `elo-history` esté estable en producción.
4. **Fotos de perfil/grupo en Postgres** (≤ 2 MB, 256×256 JPEG ~25 KB) — tras medir el almacenamiento de Neon.
5. **Atributos entre compañeros + radar** — cuando el balanceo por nivel + ELO solo resulte insuficiente.

Todo vuelve vía `/architect-brownfield` en un segundo blueprint, con este v1 en producción.

---

*End of blueprint. Build order is §9. Stop when §20.1 is green.*

