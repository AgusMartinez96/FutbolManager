# Epic 06: Dirección visual monocroma

> Al terminar, la app usa la dirección visual monocroma de §7: paleta negro/blanco/grises en claro y oscuro (con el color reservado a error y éxito), botones en pastilla de 30px, tarjetas planas con borde fino, pesos 400/500, Inter para la interfaz y Barlow Condensed (self-hosted) solo para el marcador en vivo, el nombre del grupo y el ranking; los equipos A y B se distinguen por relleno/contorno y letra, los links por subrayado, y el foco, el contraste y los tamaños (44px en controles, 64px en vivo) quedan medidos por tests.

| | |
|---|---|
| **Epic id** | `06-direccion-visual-monocroma` |
| **Tasks** | `E6-T1` … `E6-T8` |
| **Depends on** | `05-equipos-por-suma-y-alternativas` (E5-T3) |
| **Unlocks** | nada |
| **Parallel with** | ninguno |

Cambio de producto aprobado con el proyecto completo (31 pasos hechos): solo toca el frontend. En `tasks.json` estas tareas van **al final** del array y sus pasos son el 32 a 39: no se renumera nada porque los tags `step-01` … `step-31` ya existen. La inspiración es `blueprints/futbol-manager/referencia-estilo.md` (anonimizada): se toma como referencia, no como copia. You do not need any other file to complete this epic. Everything below is repeated here on purpose.

---

## Stack

React 19 + TypeScript 6 + Vite 8 · Tailwind CSS 4 · TanStack Query 5 · React Router 7 · `radix-ui` · `lucide-react`. Package manager: npm (`frontend/package-lock.json`). Versiones en el lockfile — leelas, nunca adivines una.

Todos los comandos corren en **Git Bash** desde la raíz del proyecto (en CMD/PowerShell los `npm --prefix frontend run …` son iguales y `./mvnw` es `.\mvnw.cmd`).

| Task | Command |
|---|---|
| Frontend | `npm --prefix frontend run format:check` · `lint` · `typecheck` · `test` · `build` |
| Jar + E2E (T3 y T8) | `./mvnw -q -DskipTests package` · `npm --prefix frontend run test:e2e` (requiere la base local arriba: `docker compose up -d --wait db`) |

**Gate:** los cinco comandos de frontend en verde antes de marcar una tarea como hecha; T3 y T8 suman el jar y `test:e2e`.

## Directory subtree

```
frontend/src/styles/
  tokens.css                    # exists — edited (E6-T1, E6-T7)
  index.css                     # exists — edited (E6-T1, E6-T7, E6-T8)
  tokens.test.ts                # NEW (E6-T1)
  designGuards.test.ts          # NEW (E6-T8)
frontend/src/components/
  ui.tsx                        # exists — edited (E6-T2)
  ui.test.tsx                   # NEW (E6-T2)
frontend/src/app/               # AppShell.tsx, Breadcrumbs.tsx, router.tsx — edited (E6-T2)
frontend/src/features/live/     # Scoreboard.tsx NEW; LivePage(.test).tsx, EventDialog.tsx (E6-T3); CloseControls(.test).tsx (E6-T5)
frontend/src/features/groups/   # GroupsPages(.test).tsx, GroupDetailPage.tsx, PlayersTab.tsx, PlayerForm.tsx (E6-T4)
frontend/src/features/auth/     # AuthPages(.test).tsx (E6-T5)
frontend/src/features/matches/  # MatchesPages.tsx (E6-T5); MatchSetupPage(.test).tsx, TeamColumn.tsx, LineupBuilder(.test).tsx (E6-T6)
frontend/src/features/stats/    # StatsPages(.test).tsx (E6-T7)
frontend/e2e/flows.spec.ts      # exists — edited (E6-T3, E6-T8)
frontend/package.json, package-lock.json   # edited (E6-T1: + Barlow Condensed, E6-T8: - JetBrains Mono)
```

Everything outside this subtree is out of scope. If a task seems to require editing a file not listed here, stop and report — it means the epic boundary is wrong. No hay cambios de backend, de esquema ni de API.

## Data model touched here

Ninguno.

## Contracts

**Produced** — later work depends on exactly these signatures:

| Export | Signature | Used by |
|---|---|---|
| `TextLink` | `({ to, block?, ...LinkProps }) => JSX` — `Link` con subrayado permanente, `font-medium`, color del texto; `block` suma `inline-flex min-h-11 items-center` | todas las pantallas |
| `TeamTag` | `({ team: 'A' \| 'B' }) => JSX` — A pastilla rellena, B pastilla de contorno, letra visible (`aria-hidden`) y texto `sr-only` «Equipo A/B» | `Scoreboard` |
| `Alert` | `variant: 'error' \| 'success' \| 'info'` — ícono `aria-hidden` + texto; `error` = `role="alert"`, `success`/`info` = `role="status"` | todas las pantallas |
| `Button` | `md` = `min-h-11`, `live` = `min-h-16`, `rounded-pill`, sin clases de foco | todas las pantallas |
| `Scoreboard` | `({ live }: { live: LiveState }) => JSX` — `role="status"` `aria-label="Marcador"` `aria-atomic` | `LivePage` |
| Tokens | `--fm-success`, `--fm-scrim`, `--fm-shadow-dialog`, `--fm-radius-pill`, `--fm-radius-card`, `--fm-text-score`, `--font-display` | componentes |

## Conventions that bite in this area

- **Paleta:** solo negro, blanco y grises; el color (rojo/verde) únicamente para error y éxito, siempre con ícono y texto. Ni un hex ni `rgb()` fuera de `tokens.css`.
- **Pesos:** solo 400 y 500 (`font-normal`, `font-medium`). Nada de `font-bold`, `font-semibold`, `font-extrabold` ni `font-black`.
- **Formas:** botones, pestañas y badges `rounded-pill` (30px); inputs y selects `rounded-lg` (8px); tarjetas y alertas `rounded-card` (0) con borde de 1px; ninguna sombra salvo `DialogShell` (borde de 1px `border-strong` + `shadow-dialog`).
- **Tamaños:** ≥ 24px siempre; `Button` md, `ThemeToggle`, `TextLink block`, inputs y selects ≥ 44px (`min-h-11`); botones de registro en vivo ≥ 64px (`min-h-16`). Los breadcrumbs inline quedan en `min-h-6` (24px).
- **Links:** siempre `TextLink` (subrayado permanente, color del texto): así cumplen `link-in-text-block` de axe por `text-decoration` y no por color.
- **Foco:** una única regla global `:focus-visible` en `index.css`. Ningún componente usa `outline-none`, `ring` ni `focus:` para anularla (el skip link solo usa `focus:` para mostrarse).
- **Equipos:** A relleno, B contorno y la letra siempre visible; el color nunca los distingue. Los encabezados `Equipo A`/`Equipo B` conservan su nombre accesible exacto.
- **Fuente de display:** Barlow Condensed 500 (`font-display`) solo en el marcador en vivo, el nombre del grupo y el ranking. Self-hosted: la CSP de prod es `'self'`, así que no se usa el CDN de Google Fonts. Los números del resto de la app van en Inter con `tabular-nums`.
- **Marcador:** no depende de cifras tabulares de la fuente: cada número vive en un slot de ancho fijo (`min-w-score`, 2 dígitos) y el E2E verifica que el ancho no cambia de 9 a 10 goles.
- **Scrim:** claro `rgb(17 17 17 / 0.6)` (el diálogo blanco contrasta ≥ 3:1 con el peor fondo compuesto); oscuro `rgb(0 0 0 / 0.9)` (el borde fuerte contrasta ≥ 3:1 con el peor fondo compuesto). En oscuro el relleno del diálogo no se separa del fondo: el borde es obligatorio.
- **Tests:** nada de probar foco ni subrayado por clase (no hay layout en jsdom): lo miden los E2E de Playwright con estilos calculados. `testing.md` no cambia.
- **Skill de diseño:** en tareas visuales, `ui-ux-pro-max` con una sola consulta y solo si hay una duda real de diseño, sin `--persist`. Si hace falta y no está disponible, frenar y avisar. Si una recomendación choca con el blueprint, no se aplica: se le muestra al dueño para que decida.
- **Cada tarea trae su barrido:** el último `Verify` de cada tarea busca con `grep` (exit 1) los patrones viejos en los archivos de esa tarea; `designGuards.test.ts` (E6-T8) lo hace sobre todo `src`.
- **Tamaño de archivos:** un componente ≤ 300 líneas y un archivo ≤ 400 (`GroupDetailPage.tsx` ya ronda las 250).

Full project rules: `CLAUDE.md`. Area rules: `.claude/rules/frontend.md`, `.claude/rules/testing.md`.

---

## Tasks

Listed in the same order as `tasks.json`. That order is the build order — work top to bottom and do not re-rank by priority or by what looks quick.

### `E6-T1` — Add monochrome tokens, global focus rule and display font

**Depends on:** `E5-T3` · **Priority:** p1

Los tokens pasan a la paleta monocroma de §7, el foco queda en una única regla global y la fuente de display (Barlow Condensed) se sirve desde el propio jar.

- `frontend/src/styles/tokens.css` (editar) — valores de §7 en ambos temas (`--fm-background`, `--fm-surface`, `--fm-surface-raised`, `--fm-border`, `--fm-border-strong`, `--fm-text`, `--fm-text-muted`, `--fm-primary`, `--fm-on-primary`, `--fm-focus`, `--fm-danger*`) y tokens nuevos `--fm-success`, `--fm-scrim` (`rgb(17 17 17 / 0.6)` en claro, `rgb(0 0 0 / 0.9)` en oscuro), `--fm-shadow-dialog`, `--fm-radius-pill` (30px), `--fm-radius-card` (0) y `--fm-text-score` (4rem). `--fm-accent`, `--fm-mvp`, `--fm-mvp-badge` y `--fm-on-mvp` quedan como alias transitorios (`--fm-accent: var(--fm-text)`, `--fm-mvp: var(--fm-text)`, `--fm-mvp-badge: var(--fm-primary)`, `--fm-on-mvp: var(--fm-on-primary)`) para que el código todavía no migrado compile; el paso 38 los elimina. Sigue siendo el único archivo con hex y con `rgb()`.
- `frontend/src/styles/index.css` (editar) — mapea los tokens nuevos en `@theme inline` (`--color-success`, `--color-scrim`, `--shadow-dialog`, `--radius-pill`, `--radius-card`, `--text-score`, `--spacing-score` de `2ch`, `--font-display`); importa `@fontsource/barlow-condensed/latin-500.css` y `@fontsource/barlow-condensed/latin-ext-500.css`; `--font-display: 'Barlow Condensed', 'Inter Variable', ui-sans-serif, system-ui, sans-serif` (lo que quede fuera del subset latin cae en Inter al mismo peso). `--font-mono` se mantiene hasta el paso 38 y el `@import` de JetBrains hasta el 39. La regla global `:focus-visible` (`outline: 2px solid var(--fm-focus)` y `outline-offset: 2px`) queda como la única regla de foco del producto, dentro de `@layer base`.
- `frontend/package.json` (editar) — agrega `@fontsource/barlow-condensed` con el pin exacto `5.3.0` (sin `^`), resuelto contra el registro por `stack-researcher` el 2026-11-06 (publicada el 2026-07-19; trae `latin-500.css` y `latin-ext-500.css`; sin peers ni engines). El motivo va en el mensaje del commit (fuente de display self-hosted: el CDN de Google Fonts queda bloqueado por la CSP de prod) y la fila está en §11.
- `frontend/package-lock.json` (editar) — actualizado por `npm --prefix frontend install`.
- `frontend/src/styles/tokens.test.ts` (nuevo) — Vitest sin DOM: lee `tokens.css`, parsea ambos temas y calcula el contraste (luminancia relativa) de todos los pares de la tabla de §7: texto, muted, danger y success sobre `background`, `surface` y `surface-raised`; `on-primary` sobre `primary`; `on-danger` sobre `danger-bg`; `border-strong` y `focus` sobre las tres superficies; el texto del diálogo (`text` y `text-muted` sobre `surface`); y el `--fm-scrim` compuesto sobre el token de color de mayor luminancia de cada tema (`background`, `surface`, `surface-raised`, `text` o `primary`; en oscuro es el relleno primario `#ffffff`) (claro: el fondo del diálogo contra ese peor fondo; oscuro: `border-strong` contra ese peor fondo). Falla si hay hex en otro `.css`.

**Files**
- `frontend/src/styles/tokens.css` — edit
- `frontend/src/styles/index.css` — edit
- `frontend/package.json` — edit
- `frontend/package-lock.json` — edit
- `frontend/src/styles/tokens.test.ts` — new

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `frontend/src/styles/tokens.test.ts` **THE SYSTEM SHALL** leer `tokens.css` y comprobar en claro y oscuro texto ≥ 4,5:1 (`text` y `text-muted` sobre `background`, `surface` y `surface-raised`; `danger` y `success` sobre las mismas tres superficies; `on-primary` sobre `primary`; `on-danger` sobre `danger-bg`) y límites de control ≥ 3:1 (`border-strong` y `focus` sobre `background`, `surface` y `surface-raised`).
2. **WHEN** `tokens.test.ts` compone `--fm-scrim` sobre el token de color de mayor luminancia de cada tema (`background`, `surface`, `surface-raised`, `text` o `primary`; en oscuro es el relleno primario `#ffffff`) **THE SYSTEM SHALL** comprobar en claro que el fondo del diálogo contrasta ≥ 3:1 con ese peor fondo compuesto y en oscuro que `border-strong` contrasta ≥ 3:1 con él, y que `text` y `text-muted` sobre `surface` cumplen 4,5:1 (texto del diálogo).
3. **WHEN** se busca en `frontend/src` (sin contar los tests) **THE SYSTEM SHALL** encontrar en `index.css` la única regla global `:focus-visible` con `outline: 2px solid var(--fm-focus)` y `outline-offset: 2px`, y ninguna aparición de `outline-none`, `outline-hidden`, `outline: none` ni `ring-offset`.
4. **WHEN** corre `build` **THE SYSTEM SHALL** empaquetar Barlow Condensed `latin-500` y `latin-ext-500` desde `node_modules` (self-hosted), sin referencias a `fonts.googleapis.com` ni `fonts.gstatic.com` en `frontend/src` ni en `frontend/index.html`.
5. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend con los alias transitorios `--fm-accent` y `--fm-mvp*` **THE SYSTEM SHALL** salir con 0, porque el código aún no migrado sigue compilando.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -rnE --exclude='*.test.*' 'outline-none|outline-hidden|outline: *none|ring-offset' frontend/src || status=$?; test "$status" -eq 1
status=0; grep -rnE --exclude='*.test.*' 'fonts\.googleapis|fonts\.gstatic' frontend/src frontend/index.html || status=$?; test "$status" -eq 1
ls src/main/resources/static/assets | grep -q 'barlow-condensed-latin-500'
ls src/main/resources/static/assets | grep -q 'barlow-condensed-latin-ext-500'
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T1: add monochrome tokens, global focus rule and display font"
git tag step-32-visual-tokens
```

### `E6-T2` — Restyle ui primitives, add TextLink and TeamTag, restyle shell

**Depends on:** `E6-T1` · **Priority:** p1

Las primitivas pasan a pastilla/plano, aparecen `TextLink` y `TeamTag`, `Alert` suma éxito con ícono y `DialogShell` lleva borde y scrim; el header de la app se reestiliza.

- `frontend/src/components/ui.tsx` (editar) — `Button`: pastilla (`rounded-pill`), `font-medium`, `md` con `min-h-11` (44px) y `live` con `min-h-16`; `primary` relleno, `secondary` contorno `border-border-strong`, `danger` relleno rojo, `ghost`; `disabled:opacity-50 disabled:cursor-not-allowed`, `active:opacity-80`; sin ninguna clase de foco (lo resuelve la regla global). `Field`: label `font-medium`, input `min-h-11 rounded-lg`, error con ícono `CircleAlert` `aria-hidden`. `Card`: `rounded-card border border-border`, plana. `Alert`: variantes `error` (`role="alert"`), `success` (`role="status"`) e `info`, con ícono `lucide-react` `aria-hidden` y el texto, `rounded-card`. `DialogShell`: `rounded-card border border-border-strong shadow-dialog`, overlay `bg-scrim`, título `font-medium`, conserva `z-40`/`z-50`. Nuevos `TextLink` (envuelve `Link` de `react-router`: `underline underline-offset-4 font-medium text-text`; con `block` suma `inline-flex min-h-11 items-center`) y `TeamTag` (equipo A: pastilla rellena `bg-primary text-on-primary`; equipo B: pastilla de contorno; la letra «A»/«B» siempre visible con `aria-hidden` y un texto `sr-only` «Equipo A»/«Equipo B»; no un `aria-label` sobre un `span`, que axe marca como `aria-prohibited-attr`).
- `frontend/src/components/ui.test.tsx` (nuevo) — `Button` en sus cuatro variantes con nombre accesible y `disabled`; `size="live"` con `min-h-16`; `Field` con label, `aria-invalid` y `aria-describedby`; `Alert` error (`role="alert"`) y success (`role="status"`), cada uno con ícono `aria-hidden` y el texto; `TeamTag` A y B con nombre accesible; `TextLink` con rol `link` y `href`; `DialogShell` con título y descripción accesibles. No se prueba foco ni subrayado por clase: eso lo mide Playwright en el paso 39.
- `frontend/src/app/AppShell.tsx` (editar) — título de la app `font-medium`; `ThemeToggle` `size-11 rounded-pill` conservando `aria-label`, `fm-theme` y `data-theme`; skip link `rounded-pill`. Header plano con borde inferior.
- `frontend/src/app/Breadcrumbs.tsx` (editar) — los tramos con link usan `TextLink` (inline, `min-h-6`); se conservan `aria-label="Migas de pan"` y `aria-current="page"`.
- `frontend/src/app/router.tsx` (editar) — el `h1` de la ruta `*` pasa a `font-medium`.
- `AppShell.test.tsx` y `Breadcrumbs.test.tsx` consultan por rol, nombre y `aria-current`: no deberían cambiar y corren en el gate; si alguno necesita un ajuste, se frena y se reporta (la tarea superaría los 5 archivos).

**Files**
- `frontend/src/components/ui.tsx` — edit
- `frontend/src/components/ui.test.tsx` — new
- `frontend/src/app/AppShell.tsx` — edit
- `frontend/src/app/Breadcrumbs.tsx` — edit
- `frontend/src/app/router.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `frontend/src/components/ui.test.tsx` **THE SYSTEM SHALL** renderizar `Button` en `primary`, `secondary`, `danger` y `ghost` con su nombre accesible, `disabled` real al deshabilitarlo y la clase `min-h-16` en `size="live"`.
2. **WHEN** se renderiza `Alert` con `variant="error"` y con `variant="success"` **THE SYSTEM SHALL** exponer `role="alert"` y `role="status"` respectivamente, con un ícono `aria-hidden` y el mensaje como texto.
3. **WHEN** se renderiza `TeamTag` con `A` y con `B` **THE SYSTEM SHALL** mostrar la letra y exponer el nombre accesible `Equipo A` y `Equipo B`, y **WHEN** se renderiza `TextLink` **THE SYSTEM SHALL** exponer un `link` con su `href`.
4. **WHEN** corren `AppShell.test.tsx` y `Breadcrumbs.test.tsx` sin cambios **THE SYSTEM SHALL** pasar: skip link a `#main`, un `h1`, botón de tema que alterna `data-theme` y guarda `fm-theme`, y migas con `aria-current="page"`.
5. **WHEN** se buscan en `ui.tsx`, `AppShell.tsx`, `Breadcrumbs.tsx` y `router.tsx` pesos `font-bold`/`font-semibold`, `rounded-xl`, `font-mono`, clases de los tokens `accent`/`mvp`, `outline-none` y `rgb(` (en `ui.tsx` también `shadow-[`) **THE SYSTEM SHALL** no encontrar coincidencias.
6. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow-\[|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/components/ui.tsx || status=$?; test "$status" -eq 1
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/app/AppShell.tsx frontend/src/app/Breadcrumbs.tsx frontend/src/app/router.tsx || status=$?; test "$status" -eq 1
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T2: restyle ui primitives, add TextLink and TeamTag, restyle shell"
git tag step-33-ui-primitives
```

### `E6-T3` — Restyle live mode with condensed scoreboard and filled/outlined team buttons

**Depends on:** `E6-T2` · **Priority:** p1

El marcador pasa a Barlow Condensed en slots de ancho fijo con `TeamTag` A (relleno) y B (contorno); los botones de registro distinguen los equipos por forma y texto, siempre a 64px.

- `frontend/src/features/live/Scoreboard.tsx` (nuevo) — `Scoreboard` extraído de `LivePage.tsx`: sticky arriba con borde inferior y sin sombra; `role="status"`, `aria-label="Marcador"` y `aria-atomic="true"`; `TeamTag` A (relleno) y B (contorno) a los lados; cada número en `font-display text-score` dentro de un slot de ancho fijo de 2 dígitos (`min-w-score`, centrado, `tabular-nums`) para que el separador y las etiquetas no se muevan de 9 a 10; texto accesible oculto `Equipo A {n}, equipo B {m}` y los números visibles `aria-hidden`. No depende de que Barlow Condensed traiga cifras tabulares.
- `frontend/src/features/live/LivePage.tsx` (editar) — usa `Scoreboard`; la grilla de registro de 2 columnas pone en la izquierda los botones del equipo A con `variant="primary"` (relleno) y en la derecha los del equipo B con `variant="secondary"` (contorno), todos `size="live"` (64px) y con la etiqueta de texto de siempre (`Gol A`, `Gol B`, …); `AfterMatchLinks` con `TextLink block`; títulos `font-medium`; el banner `Sin conexión, reintentando…` se muestra debajo del marcador para no empujarlo; `EventList` con `Card` plana.
- `frontend/src/features/live/LivePage.test.tsx` (editar) — el marcador (`status` de nombre `Marcador`, `aria-atomic`, texto `Equipo A 2, equipo B 1`), los ocho botones por nombre con `min-h-16`, el banner y los casos existentes (diálogo `z-50`, anular, deshacer).
- `frontend/src/features/live/EventDialog.tsx` (editar) — labels `font-medium` y selects `min-h-11 rounded-lg`; usa `DialogShell` sin cambios de contrato.
- `frontend/e2e/flows.spec.ts` (editar) — nuevo recorrido «marcador sin saltos»: carga goles del equipo A hasta 10 y comprueba que el ancho del contenedor del número del equipo A es idéntico con 9 y con 10; con estilos calculados comprueba que `Gol A` tiene fondo opaco y `Gol B` fondo transparente con borde; axe y sin scroll horizontal a 375px; botones de registro ≥ 64px.

**Revisión del dueño (no es un gate de Verify):** antes de cerrar esta tarea con `--done`, el dueño prueba en el celular la distinción equipo A relleno / equipo B contorno (decisión P2 del 2026-11-06: se mantiene). No forma parte de Done when ni de Verify y figura en la checklist manual de §20.1. Si no convence, se frena y se decide antes del commit de cierre.

**Files**
- `frontend/src/features/live/Scoreboard.tsx` — new
- `frontend/src/features/live/LivePage.tsx` — edit
- `frontend/src/features/live/LivePage.test.tsx` — edit
- `frontend/src/features/live/EventDialog.tsx` — edit
- `frontend/e2e/flows.spec.ts` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** `LivePage.test.tsx` renderiza el modo en vivo con un 2 a 1 **THE SYSTEM SHALL** exponer un `role="status"` de nombre `Marcador` con `aria-atomic="true"` cuyo texto incluye `Equipo A 2, equipo B 1`, y los ocho botones de registro (`Gol A` … `Jugadón B`) con la clase `min-h-16`.
2. **WHEN** corre el E2E del modo en vivo **THE SYSTEM SHALL** comprobar con estilos calculados que `Gol A` tiene fondo opaco y `Gol B` fondo transparente con borde, de modo que los equipos se distinguen por forma además de por la letra.
3. **WHEN** el E2E carga goles del equipo A hasta llegar a 10 **THE SYSTEM SHALL** medir el ancho del contenedor del número del equipo A con 9 goles y con 10 y comprobar que es idéntico, sin scroll horizontal a 375px.
4. **WHEN** el E2E analiza con axe el modo en vivo a 375px **THE SYSTEM SHALL** reportar 0 violaciones `serious` o `critical` y botones de registro de al menos 64px de alto.
5. **WHEN** corren los tests existentes de `LivePage.test.tsx` **THE SYSTEM SHALL** conservar el diálogo de evento por encima del marcador (`z-50`), anular con reglas de autoría, deshacer y el banner `Sin conexión, reintentando…`.
6. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend, el empaquetado del jar y `test:e2e` **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/features/live/LivePage.tsx frontend/src/features/live/Scoreboard.tsx frontend/src/features/live/EventDialog.tsx || status=$?; test "$status" -eq 1
./mvnw -q -DskipTests package
npm --prefix frontend run test:e2e
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T3: restyle live mode with condensed scoreboard and filled/outlined team buttons"
git tag step-34-live-scoreboard
```

### `E6-T4` — Restyle groups and players screens

**Depends on:** `E6-T3` · **Priority:** p1

Mis grupos, el detalle del grupo (con el nombre en Barlow Condensed), las pestañas en pastilla y la lista y el formulario de jugadores pasan a la dirección monocroma.

- `frontend/src/features/groups/GroupsPages.tsx` (editar) — filas de la lista de grupos planas (`rounded-card border border-border`, sin `rounded-xl`), `font-medium`, links con `TextLink`; títulos `font-medium`.
- `frontend/src/features/groups/GroupDetailPage.tsx` (editar) — el `h1` con el nombre del grupo en `font-display` (30px, `break-words` para nombres largos); links con `TextLink`; pestañas en pastilla (`rounded-pill min-h-11`), la activa con `bg-primary text-on-primary` y las demás con contorno, conservando `role="tab"`, `aria-selected` y `?tab=`; código de invitación en Inter `tabular-nums`; el checkbox de `membersCanScore` con su label en `min-h-11`.
- `frontend/src/features/groups/PlayersTab.tsx` (editar) — ELO en Inter `tabular-nums`; link del jugador con `TextLink`; el nivel sigue visible solo para admin.
- `frontend/src/features/groups/PlayerForm.tsx` (editar) — label `font-medium` y select de puesto `min-h-11 rounded-lg`.
- `frontend/src/features/groups/GroupsPages.test.tsx` (editar) — único test de `GroupsPages`, `GroupDetailPage`, `PlayersTab` y `PlayerForm`: se agregan los casos del `h1` con el nombre del grupo y de las pestañas (`role="tab"`, `aria-selected`) y se conservan los de nivel solo para admin.

**Files**
- `frontend/src/features/groups/GroupsPages.tsx` — edit
- `frontend/src/features/groups/GroupDetailPage.tsx` — edit
- `frontend/src/features/groups/PlayersTab.tsx` — edit
- `frontend/src/features/groups/PlayerForm.tsx` — edit
- `frontend/src/features/groups/GroupsPages.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `GroupsPages.test.tsx` y se abre el detalle de un grupo **THE SYSTEM SHALL** mostrar el nombre del grupo en un único `h1` y las pestañas con `role="tab"` y `aria-selected` correcto, y **WHEN** se cambia de pestaña **THE SYSTEM SHALL** reflejarla en `?tab=`.
2. **WHEN** un `MEMBER` abre la pestaña de jugadores **THE SYSTEM SHALL** no mostrar ningún valor ni campo `Nivel`, y **WHEN** es `ADMIN` **THE SYSTEM SHALL** mostrar `Nivel 5,0` y el campo `Nivel (1,0 a 10,0)`.
3. **WHEN** se buscan en `GroupsPages.tsx`, `GroupDetailPage.tsx`, `PlayersTab.tsx` y `PlayerForm.tsx` pesos `font-bold`/`font-semibold`, `rounded-xl`, `font-mono`, clases de los tokens `accent`/`mvp`, `shadow`, `outline-none` y `rgb(` **THE SYSTEM SHALL** no encontrar coincidencias.
4. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/features/groups/GroupsPages.tsx frontend/src/features/groups/GroupDetailPage.tsx frontend/src/features/groups/PlayersTab.tsx frontend/src/features/groups/PlayerForm.tsx || status=$?; test "$status" -eq 1
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T4: restyle groups and players screens"
git tag step-35-groups
```

### `E6-T5` — Restyle auth, matches list and match close controls

**Depends on:** `E6-T4` · **Priority:** p1

Ingreso y registro, la lista y el alta de partidos y el cierre con MVP pasan a la dirección monocroma, con links subrayados y errores con ícono.

- `frontend/src/features/auth/AuthPages.tsx` (editar) — título `font-medium`; los links entre `/ingresar` y `/registro` (dentro de la frase «¿No tenés cuenta?») con `TextLink` inline; errores con `Alert` con ícono.
- `frontend/src/features/auth/AuthPages.test.tsx` (editar) — se conservan los casos de errores (`aria-invalid`, `aria-describedby`, resumen `role="alert"`) y se agrega el caso de los links entre ambas pantallas por rol `link`.
- `frontend/src/features/matches/MatchesPages.tsx` (editar) — lista plana (`rounded-card border border-border`, sin `rounded-xl`), `font-medium`, número de jugadores en Inter `tabular-nums`, select de formato `min-h-11 rounded-lg`, link con `TextLink`.
- `frontend/src/features/live/CloseControls.tsx` (editar) — label del MVP `font-medium`, select `min-h-11 rounded-lg`, resumen de ELO en Inter `tabular-nums`; los botones siguen `size="live"`.
- `frontend/src/features/live/CloseControls.test.tsx` (editar) — se conservan cierre con MVP, cambio de MVP, anulación y resumen de ELO (texto); si no hace falta tocarlo corre sin cambios.

**Files**
- `frontend/src/features/auth/AuthPages.tsx` — edit
- `frontend/src/features/auth/AuthPages.test.tsx` — edit
- `frontend/src/features/matches/MatchesPages.tsx` — edit
- `frontend/src/features/live/CloseControls.tsx` — edit
- `frontend/src/features/live/CloseControls.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `AuthPages.test.tsx` **THE SYSTEM SHALL** mantener los errores por campo con `aria-invalid` y `aria-describedby`, el resumen `role="alert"` `Revisá los campos marcados.` y los links entre `/ingresar` y `/registro` accesibles por rol `link`.
2. **WHEN** corre `CloseControls.test.tsx` **THE SYSTEM SHALL** cerrar con el MVP elegido y mandarlo como `mvpPlayerId`, cambiar el MVP de un partido cerrado con `PUT …/mvp` y mostrar el resumen de ELO con su texto (`Agus1000 → 1012 · +12 (sube)`).
3. **WHEN** se buscan en `AuthPages.tsx`, `MatchesPages.tsx` y `CloseControls.tsx` pesos `font-bold`/`font-semibold`, `rounded-xl`, `font-mono`, clases de los tokens `accent`/`mvp`, `shadow`, `outline-none` y `rgb(` **THE SYSTEM SHALL** no encontrar coincidencias.
4. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/features/auth/AuthPages.tsx frontend/src/features/matches/MatchesPages.tsx frontend/src/features/live/CloseControls.tsx || status=$?; test "$status" -eq 1
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T5: restyle auth, matches list and match close controls"
git tag step-36-auth-matches-close
```

### `E6-T6` — Restyle match setup and lineup builder

**Depends on:** `E6-T5` · **Priority:** p1

La convocatoria, los equipos guardados y el armado pasan a la dirección monocroma: encabezados «Equipo A» relleno y «Equipo B» contorno sin cambiar su nombre accesible.

- `frontend/src/features/matches/MatchSetupPage.tsx` (editar) — títulos `font-medium`, links con `TextLink`, equipos guardados con el mismo contraste relleno (A) / contorno (B), número de confirmados en Inter `tabular-nums`; el select de asistencia usa `selectClass` de `TeamColumn` (`min-h-11`).
- `frontend/src/features/matches/MatchSetupPage.test.tsx` (editar) — depende de la estructura (`heading {name: 'Equipo A'}` con `closest('div')`, `Agus\s*Nivel 6,5\s*ELO 1000`, `Nivel total: N`): se conservan todos los casos; no se anidan nodos nuevos dentro de esos textos.
- `frontend/src/features/matches/TeamColumn.tsx` (editar) — el `h3` «Equipo A» con pastilla rellena y «Equipo B» con pastilla de contorno por clases (sin agregar nodos de texto: el nombre accesible sigue siendo exactamente `Equipo A`/`Equipo B`); números en Inter `tabular-nums`; `selectClass` con `min-h-11 rounded-lg`; nombres `font-medium`.
- `frontend/src/features/matches/LineupBuilder.tsx` (editar) — título `font-medium`; «Opción k de N» y los totales en Inter `tabular-nums` conservando la estructura de spans; botones en pastilla; el aviso de cantidad sigue como `Alert variant="error"` con ícono.
- `frontend/src/features/matches/LineupBuilder.test.tsx` (editar) — se conservan los casos de «Otra opción» (`getByText(/^Opción/)`), el aviso y el recorrido de alternativas.

**Files**
- `frontend/src/features/matches/MatchSetupPage.tsx` — edit
- `frontend/src/features/matches/MatchSetupPage.test.tsx` — edit
- `frontend/src/features/matches/TeamColumn.tsx` — edit
- `frontend/src/features/matches/LineupBuilder.tsx` — edit
- `frontend/src/features/matches/LineupBuilder.test.tsx` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `MatchSetupPage.test.tsx` **THE SYSTEM SHALL** conservar los encabezados con nombre exacto `Equipo A` y `Equipo B`, `Nivel total: N` por equipo, `Diferencia de nivel: N`, el aviso `role="alert"` de cantidad y los puestos `Puesto de {nombre}`.
2. **WHEN** corre `LineupBuilder.test.tsx` **THE SYSTEM SHALL** mostrar `Opción k de N` en el `status`, recorrer las alternativas con `Otra opción` y volver a la primera sin nuevas peticiones a `balance`.
3. **WHEN** un `MEMBER` abre el detalle del partido **THE SYSTEM SHALL** mostrar la convocatoria y los equipos guardados sin controles de armado y sin ningún valor de nivel.
4. **WHEN** se activa con el teclado `Pasar al equipo B` **THE SYSTEM SHALL** mover al jugador sin arrastrar.
5. **WHEN** se buscan en `MatchSetupPage.tsx`, `TeamColumn.tsx` y `LineupBuilder.tsx` pesos `font-bold`/`font-semibold`, `rounded-xl`, `font-mono`, clases de los tokens `accent`/`mvp`, `shadow`, `outline-none` y `rgb(` **THE SYSTEM SHALL** no encontrar coincidencias.
6. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/features/matches/MatchSetupPage.tsx frontend/src/features/matches/TeamColumn.tsx frontend/src/features/matches/LineupBuilder.tsx || status=$?; test "$status" -eq 1
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T6: restyle match setup and lineup builder"
git tag step-37-lineup-screens
```

### `E6-T7` — Restyle ranking and profile, remove legacy tokens

**Depends on:** `E6-T6` · **Priority:** p1

El ranking y el perfil pasan a la dirección monocroma (ranking en Barlow Condensed, MVP como badge invertido) y, sin usos pendientes, se eliminan los tokens `accent`, `mvp` y `font-mono`.

- `frontend/src/features/stats/StatsPages.tsx` (editar) — `h1` «Ranking» y posición, nombre y ELO del ranking en `font-display`; el resto de los números (perfil, historial) en Inter `tabular-nums`; links con `TextLink`; badges en contorno `rounded-pill`; el badge de MVP invertido (`bg-primary text-on-primary`) con ícono `Trophy` `aria-hidden` y el número; pesos solo 400/500; los nombres largos se ajustan en varias líneas.
- `frontend/src/features/stats/StatsPages.test.tsx` (editar) — se conservan tabla de ranking (posición, link del jugador, ELO, `Provisorio`), perfil (`dt`/`dd` de estadísticas, `MVPs`), historial y errores con `role="alert"`; se agrega que el badge de MVP muestra el número.
- `frontend/src/styles/tokens.css` (editar) — elimina `--fm-accent`, `--fm-mvp`, `--fm-mvp-badge` y `--fm-on-mvp` (alias transitorios del paso 32), después de comprobar que ningún archivo los usa.
- `frontend/src/styles/index.css` (editar) — elimina `--color-accent`, `--color-mvp`, `--color-mvp-badge`, `--color-on-mvp` y `--font-mono`; el `@import` de JetBrains queda hasta el paso 39. Los identificadores de dominio `mvp`, `mvps`, `mvpPlayerId`, `MvpSelect` y `close-mvp` no son tokens y no se tocan.

**Files**
- `frontend/src/features/stats/StatsPages.tsx` — edit
- `frontend/src/features/stats/StatsPages.test.tsx` — edit
- `frontend/src/styles/tokens.css` — edit
- `frontend/src/styles/index.css` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `StatsPages.test.tsx` **THE SYSTEM SHALL** mostrar el ranking con posición, link al perfil, ELO, partidos y `Provisorio`, y el perfil con las estadísticas por `dt`/`dd`, `MVPs` y el historial.
2. **WHEN** se buscan en `frontend/src` (sin contar los tests) las clases `(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)`, las variables `--fm-accent`, `--fm-mvp`, `--fm-on-mvp`, `--color-accent`, `--color-mvp`, `--color-on-mvp` y `--font-mono` y la clase `font-mono` **THE SYSTEM SHALL** no encontrar coincidencias.
3. **WHEN** se buscan en `StatsPages.tsx` pesos `font-bold`/`font-semibold`, `rounded-xl`, `shadow`, `outline-none` y `rgb(` **THE SYSTEM SHALL** no encontrar coincidencias.
4. **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -nE 'font-(bold|semibold|extrabold|black)|shadow|rounded-(xl|2xl|3xl)|font-mono|(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|outline-none|rgb\(' frontend/src/features/stats/StatsPages.tsx || status=$?; test "$status" -eq 1
status=0; grep -rnE --exclude='*.test.*' '(text|bg|border|ring)-(accent|mvp|mvp-badge|on-mvp)|--fm-accent|--fm-mvp|--fm-on-mvp|--color-accent|--color-mvp|--color-on-mvp|--font-mono|font-mono' frontend/src || status=$?; test "$status" -eq 1
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T7: restyle ranking and profile, remove legacy tokens"
git tag step-38-ranking-token-cleanup
```

### `E6-T8` — Add visual guards, theme E2E and drop JetBrains Mono

**Depends on:** `E6-T7` · **Priority:** p1

Un test de Vitest prohíbe los patrones visuales viejos, Playwright mide foco, subrayado y tamaños en claro y oscuro, y se retira la fuente JetBrains Mono.

- `frontend/src/styles/designGuards.test.ts` (nuevo) — escanea los `*.ts` y `*.tsx` de `frontend/src` (sin `*.test.*`; el test contiene los patrones como datos) y falla si encuentra: pesos `font-bold`, `font-semibold`, `font-extrabold`, `font-black`, `font-light`, `font-thin`; `shadow` y `shadow-*` (excepción explícita: `shadow-dialog` solo en `components/ui.tsx`); `rounded-xl`, `rounded-2xl`, `rounded-3xl`, `rounded-md`, `rounded-sm` y `rounded-none`; `rounded-lg` (excepción explícita solo en `components/ui.tsx`, `groups/PlayerForm.tsx`, `matches/TeamColumn.tsx`, `matches/MatchesPages.tsx`, `live/CloseControls.tsx` y `live/EventDialog.tsx`, que son inputs y selects); `font-mono`; `font-display` (excepción explícita solo en `live/Scoreboard.tsx`, `groups/GroupDetailPage.tsx` y `stats/StatsPages.tsx`); clases y variables de los tokens `accent`/`mvp`; hex, `rgb(`, `rgba(`, `hsl(` y `oklch(` fuera de `tokens.css`; clases de paleta de Tailwind (`bg-white`, `text-black`, `border-red-500`, …) y valores arbitrarios `[#…]`; `outline-none`, `outline-hidden`, `ring-offset` y `focus:` salvo el skip link; `fonts.googleapis.com`. Las excepciones viven en constantes con nombre al principio del test. Para los `*.css` solo comprueba que no haya hex, `rgb(`, `rgba(`, `hsl(` ni `oklch(` fuera de `tokens.css`, variables o clases de los tokens `accent`/`mvp` ni `fonts.googleapis.com` (`index.css` define legítimamente `--shadow-dialog`, `--font-display` y `--color-focus`). Incluye casos de tabla que **deben** marcarse (`font-bold`, `shadow-lg`, `rounded-xl`, `outline-none`, `bg-white`, `font-display` en una ruta no permitida) y casos que no (`font-medium`, `shadow-dialog` en `components/ui.tsx`, `rounded-lg` en un archivo de la lista), para que una regex rota no pase en vacío; esos casos no llevan literales hex (los rechaza ESLint).
- `frontend/e2e/flows.spec.ts` (editar) — corre en tema claro y oscuro (`localStorage` `fm-theme` antes de cargar) sobre `/ingresar`, `/registro`, `/grupos`, el detalle de grupo y el modo en vivo a 375px: axe con etiquetas `wcag2a`, `wcag2aa`, `wcag21aa`, `wcag22aa` y 0 violaciones `serious`/`critical`, sin scroll horizontal; estilos calculados: los links dentro de la frase de `/ingresar` y `/registro` tienen `text-decoration-line` con `underline`; con Tab sobre un botón relleno el `:focus-visible` tiene `outline-style: solid`, `outline-width: 2px`, `outline-offset: 2px` y el `outline-color` del token `--fm-focus`; alturas ≥ 44px para `Button` md, `ThemeToggle`, `TextLink block`, un input y un select, y ≥ 64px en los botones de registro; un recorrido «foco no tapado por el marcador sticky»: con el modo en vivo cargado y la página desplazada, Tab por los botones de registro (`Gol A` … `Jugadón B`) y `Deshacer`, y tras cada Tab el borde superior del elemento enfocado queda por debajo del borde inferior del marcador sticky (SC 2.4.11).
- `frontend/src/styles/index.css` (editar) — elimina `@import '@fontsource-variable/jetbrains-mono'`.
- `frontend/package.json` (editar) — quita `@fontsource-variable/jetbrains-mono` (motivo en el commit: la fuente mono ya no se usa).
- `frontend/package-lock.json` (editar) — actualizado por `npm --prefix frontend install`.

**Files**
- `frontend/src/styles/designGuards.test.ts` — new
- `frontend/e2e/flows.spec.ts` — edit
- `frontend/src/styles/index.css` — edit
- `frontend/package.json` — edit
- `frontend/package-lock.json` — edit

**Acceptance**

Copied from this task's `acceptance` array in `tasks.json` — identical after the emphasis normalization defined in `agents/blueprint-writer.md` *Acceptance strings*. Each one is decidable by a command below, on this machine, during the build.

1. **WHEN** corre `designGuards.test.ts` **THE SYSTEM SHALL** fallar ante cualquier peso `bold`/`semibold`/`extrabold`/`black`, sombra fuera de `shadow-dialog` en `ui.tsx`, radio `xl`/`2xl`/`3xl`, `rounded-lg` fuera de los archivos de inputs y selects de la lista explícita, `font-mono`, `font-display` fuera de `Scoreboard.tsx`, `GroupDetailPage.tsx` y `StatsPages.tsx`, tokens `accent`/`mvp`, color literal fuera de `tokens.css` y anulaciones de foco (comprobado con casos de tabla que deben marcarse y casos que no), y pasar sobre el árbol actual.
2. **WHEN** el E2E analiza con axe `/ingresar`, `/registro`, `/grupos`, el detalle de grupo y el modo en vivo a 375px en tema claro y en tema oscuro **THE SYSTEM SHALL** reportar 0 violaciones `serious` o `critical` y ningún scroll horizontal.
3. **WHEN** el E2E lee los estilos calculados de los links dentro de una frase en `/ingresar` y `/registro` en ambos temas **THE SYSTEM SHALL** comprobar que `text-decoration-line` incluye `underline`.
4. **WHEN** el E2E navega con Tab hasta un botón relleno en ambos temas **THE SYSTEM SHALL** comprobar que el estilo calculado de `:focus-visible` tiene `outline-style: solid`, `outline-width: 2px`, `outline-offset: 2px` y el color de `--fm-focus`, y **WHEN** recorre con Tab los botones de registro del modo en vivo con la página desplazada **THE SYSTEM SHALL** comprobar que el borde superior de cada elemento enfocado queda por debajo del borde inferior del marcador sticky.
5. **WHEN** el E2E mide `Button` md, `ThemeToggle`, `TextLink block`, un input y un select **THE SYSTEM SHALL** comprobar altura ≥ 44px, y **WHEN** mide los botones de registro en vivo **THE SYSTEM SHALL** comprobar altura ≥ 64px.
6. **WHEN** se busca `jetbrains` en `frontend/package.json` y `frontend/src` **THE SYSTEM SHALL** no encontrar coincidencias, y **WHEN** corren `format:check`, `lint`, `typecheck`, `test` y `build` del frontend, el empaquetado del jar y `test:e2e` **THE SYSTEM SHALL** salir con 0.

**Verify** — every command, in order, run from the project root. Each one exits 0 when this task is correct; the last one exiting 0 is what makes the task done.

```bash
npm --prefix frontend run format:check
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
status=0; grep -rniE --exclude='*.test.*' 'jetbrains' frontend/package.json frontend/src || status=$?; test "$status" -eq 1
./mvnw -q -DskipTests package
npm --prefix frontend run test:e2e -- -g "foco no tapado por el marcador"
npm --prefix frontend run test:e2e
```

**Checkpoint**

```bash
git add -A && git commit -m "E6-T8: add visual guards, theme E2E and drop JetBrains Mono"
git tag step-39-visual-guards
```

## Epic acceptance

- [ ] Los cinco comandos de frontend, `./mvnw -q -DskipTests package` y `npm --prefix frontend run test:e2e` salen con 0.
- [ ] `git tag -l 'step-3[2-9]-*'` lista los ocho tags de este epic (`step-32-visual-tokens` … `step-39-visual-guards`).
- [ ] La única dependencia nueva es `@fontsource/barlow-condensed` 5.3.0 (con motivo en el commit y fila en §11) y se retira `@fontsource-variable/jetbrains-mono`; no hay cambios de backend ni migraciones.
- [ ] Axe sin violaciones `serious`/`critical` en tema claro y oscuro a 375px; sin scroll horizontal.

## Pitfalls

- `--fm-accent` y `--fm-mvp*` siguen existiendo como alias hasta E6-T7: no los uses en código nuevo (para un link, `TextLink`).
- No agregues nodos de texto dentro de los encabezados `Equipo A`/`Equipo B` ni dentro de los textos que los tests leen (`Nivel 6,5`, `Opción k de N`): rompen `getByRole`/`getByText`.
- No uses `ring-offset` de Tailwind para el foco (es un `box-shadow` y desaparece en modo de alto contraste): la regla global usa `outline`.
- Los identificadores de dominio `mvp`, `mvps`, `mvpPlayerId`, `MvpSelect` y `close-mvp` no son tokens: no se renombran.
- Los `woff2` de Barlow Condensed (≈ 21 KB y ≈ 14 KB) superan el `assetsInlineLimit` de Vite (4 KB): se emiten como archivos del mismo origen y no hace falta `font-src data:` en la CSP. Si algún día se inlinan, la CSP los bloquearía.
- Pendiente fuera de este epic: mover el foco a `main` al cambiar de ruta (`focus-on-route-change`, recomendación de `ui-ux-pro-max`), registrado en §20.4.

## Before moving on

Con las ocho tareas hechas el proyecto vuelve a estar completo: nada más depende de este epic.
