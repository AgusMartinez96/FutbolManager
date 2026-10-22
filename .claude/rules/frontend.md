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
  `bg-primary text-on-primary`). No hex colors and no arbitrary color values in components — the
  ESLint rule rejects hex literals. Inputs and controls use `border-border-strong` (3:1 contrast).
- Icons: `lucide-react` only. Primitives: copy-in components over `radix-ui` + `cva` + `tailwind-merge`.
- Accessibility: every input has a `<label>`; errors use `aria-invalid` + `aria-describedby`; async
  results go to a `role="status"`/`role="alert"` region; targets ≥ 24px (`min-h-6 min-w-6`), live-mode
  buttons ≥ 64px (`min-h-16`); every drag interaction has buttons instead (we ship no drag at all).
- UI copy is Spanish (rioplatense, "vos"). Identifiers are English.
- One component ≤ 300 lines; a file ≤ 400 lines.
- Numbers on scoreboards and ELO use `font-mono tabular-nums`.
