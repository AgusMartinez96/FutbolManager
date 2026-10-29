# Fútbol Manager

App web para organizar partidos de fútbol amateur entre amigos: grupos con código de invitación, jugadores con ranking ELO, estadísticas, armado automático de equipos balanceados por nivel y marcador en vivo pensado para el celular.

## Stack

Java 21 · Spring Boot 4.1 (WebMVC, Security, Data JPA, Session JDBC, Flyway) · PostgreSQL 17 · React 19 + TypeScript + Vite · Tailwind CSS 4 · TanStack Query · React Router 7 · Testcontainers · Vitest · Playwright + axe · Docker → Render Free + Neon.

Las versiones exactas están en `pom.xml` y `frontend/package-lock.json`.

## Requisitos

- JDK 21
- Docker Desktop (base local y tests con Testcontainers)
- Node 24 LTS (`.nvmrc`). Node 25 instala con un warning `EBADENGINE`: usá Node 24 (nvm-windows o fnm).

## Bootstrap

Desde la raíz del proyecto:

1. Copiá `.env.example` a `.env` (nunca se commitea).
2. Levantá la base: `docker compose up -d --wait db` (PostgreSQL 17 en el puerto de host 5433).
3. Instalá el frontend: `npm --prefix frontend install`.

## Comandos

| Tarea | Git Bash | CMD / PowerShell |
|---|---|---|
| Backend dev (http://localhost:8080) | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
| Frontend dev (http://localhost:5173, proxy `/api`) | `npm --prefix frontend run dev` | igual |
| Tests de backend | `./mvnw -q verify` | `.\mvnw.cmd -q verify` |
| Una clase de test | `./mvnw -q test -Dtest=AuthIntegrationTest` | `.\mvnw.cmd -q test "-Dtest=AuthIntegrationTest"` |
| Frontend: format / lint / tipos / tests / build | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` | igual |
| Empaquetar el jar | `./mvnw -q -DskipTests package` | `.\mvnw.cmd -q -DskipTests package` |
| Smoke del jar | `bash scripts/smoke.sh` | solo Git Bash |
| Imagen Docker | `docker build -t futbol-manager:local .` y `bash scripts/docker-smoke.sh` | el smoke, solo Git Bash |
| E2E (una vez `e2e:install`) | `npm --prefix frontend run e2e:install` · `npm --prefix frontend run test:e2e` | igual |

`npm --prefix frontend run build` deja el SPA en `src/main/resources/static/`, que el jar sirve. Los E2E corren contra el jar empaquetado: antes ejecutá `build` y `package`.

## Demo

Con el perfil `demo` el arranque siembra el grupo **Los del jueves** (14 jugadores, 6 partidos cerrados con ELO recalculado). Es idempotente: arrancar de nuevo no duplica nada.

```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Ingresá con el usuario `demo` y la contraseña de `DEMO_PASSWORD` en tu `.env`.

## Deploy (Neon + Render)

1. **Neon.** Creá un proyecto con PostgreSQL 17 en `us-east-1`. Copiá host, base, usuario y contraseña.
2. **Render.** Creá un Web Service desde este repositorio; `render.yaml` define el servicio Docker con `healthCheckPath: /api/health`. Cargá las variables:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`
   - `DB_USERNAME` y `DB_PASSWORD`
   
   Render inyecta `PORT` y `RENDER_GIT_COMMIT`.
3. **Smoke.** Cuando termine el deploy, `GET https://<servicio>.onrender.com/api/health` debe responder 200 con `"status":"UP"` y `version` igual al commit desplegado.
4. **Pinger opcional.** El plan Free duerme tras inactividad; un monitor externo que pida `/api/health` cada 10 minutos evita el arranque en frío.
5. **Rollback.** En Render: *Events → Rollback* al deploy anterior. Las migraciones de Flyway son solo hacia adelante: un rollback de código no deshace el esquema.
6. **Rotar la contraseña de Neon.** Cambiala en Neon (*Roles → Reset password*), actualizá `DB_PASSWORD` en Render y redeployá. Nunca commitees secretos.

En producción las respuestas llevan `Content-Security-Policy`, `Referrer-Policy`, `X-Content-Type-Options` y `X-Frame-Options`, y la documentación OpenAPI (`/v3/api-docs`, `/swagger-ui.html`) está apagada.
