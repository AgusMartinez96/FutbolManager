---
description: Test conventions for JUnit/Testcontainers, Vitest and Playwright
paths:
  - "src/test/**"
  - "frontend/src/**/*.test.tsx"
  - "frontend/src/**/*.test.ts"
  - "frontend/e2e/**"
---

# Testing

- Integration tests hit a real PostgreSQL through Testcontainers (Docker must be running). Never mock
  repositories or the database.
- Name test methods after behavior: `returns404WhenUserIsNotAMember`, not `test3`.
- Each test creates its own users (`ApiTestClient.uniqueUsername`) and groups; no test depends on
  another test's data or on execution order.
- Time-dependent logic (throttling) takes an injected `java.time.Clock`; unit tests use a mutable fake.
- Pure algorithms (`EloCalculatorTest`, `TeamBalancerTest`) are plain JUnit with no Spring context.
- Vitest: import `describe/it/expect/vi` from `vitest` (no globals). Mock HTTP with
  `vi.stubGlobal('fetch', ...)`; query by role and label, never by CSS class — except the explicit
  `min-h-16` size check, because jsdom has no layout.
- Playwright specs live in `frontend/e2e/`, run against the packaged jar on :8080, create unique
  usernames per run, and check axe with tags `wcag2a`, `wcag2aa`, `wcag21aa`, `wcag22aa`, failing on
  `serious` or `critical` impact.
- A gate that expects a non-zero exit asserts the exact code: `status=0; cmd || status=$?; test "$status" -eq N`.
- Never add `@Disabled`, `.skip` or `.only` to make a gate pass.
