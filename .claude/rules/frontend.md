---
description: React + Vite frontend conventions (routing, data, styling, accessibility)
paths:
  - "frontend/src/**"
  - "frontend/e2e/**"
---

# Frontend

- Imports are relative and extensionless (`../lib/api`). No path aliases, no barrel `index.ts` files.
- Server data only through TanStack Query hooks in `features/<feature>/api.ts`; query keys start with
  the tenant: `['groups', groupId, 'players']`. No global store. Filters/tabs that should survive a
  reload go in the URL search params.
- HTTP only through `apiFetch` in `src/lib/api.ts` (adds `X-XSRF-TOKEN`, parses ProblemDetail into
  `ApiError`). Never call `fetch` directly from a component.
- Routing: `react-router` (package name `react-router`, never `react-router-dom`); routes declared in
  `src/app/router.tsx` only.
- Styling: Tailwind utilities mapped to tokens (`bg-surface`, `text-text-muted`, `border-border-strong`,
  `bg-primary text-on-primary`). No hex, `rgb()`/`hsl()` or Tailwind palette colors in components — the
  ESLint rule rejects hex literals and `designGuards.test.ts` the rest. Inputs and controls use
  `border-border-strong` (3:1 contrast).
- Look: monochrome; color only for error/success, always with a `lucide-react` icon and text. Only weights
  400/500 (`font-normal`, `font-medium`). Buttons, tabs and badges are pills (`rounded-pill`); inputs and
  selects `rounded-lg`; cards and alerts `rounded-card` with a 1px `border-border`. No shadows except
  `DialogShell` (`shadow-dialog` + 1px `border-border-strong`).
- Links are always `TextLink` (permanent underline; color is never the only cue). Team A is a filled
  shape and team B an outlined one, always with the letter visible.
- Focus: the single global `:focus-visible` rule in `index.css`. Never `outline-none`, `ring`,
  `ring-offset` or `focus:` overrides (the skip link may use `focus:` only to reveal itself).
- Icons: `lucide-react` only. Primitives: copy-in components over `radix-ui` + `cva` + `tailwind-merge`.
- Accessibility: every input has a `<label>`; errors use `aria-invalid` + `aria-describedby`; async
  results go to a `role="status"`/`role="alert"` region; targets ≥ 24px (`min-h-6 min-w-6`); `Button` md, `ThemeToggle`, `TextLink` block, inputs and selects
  ≥ 44px (`min-h-11`); live-mode buttons ≥ 64px (`min-h-16`); every drag interaction has buttons instead (we ship no drag at all).
- UI copy is Spanish (rioplatense, "vos"). Identifiers are English.
- One component ≤ 300 lines; a file ≤ 400 lines.
- Numbers use Inter with `tabular-nums`. `font-display` (Barlow Condensed, self-hosted) is only for the live
  scoreboard, the group name and the ranking; there is no `font-mono`.
- En tareas visuales, usá `ui-ux-pro-max` con una sola consulta y solo si hay una duda real de diseño. Si hace falta y no está disponible, frená y avisá. Si una recomendación choca con el blueprint, no se aplica: se le muestra al dueño para que decida. Se usa sin `--persist`.
