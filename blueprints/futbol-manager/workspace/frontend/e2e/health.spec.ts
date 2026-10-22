import { expect, test } from '@playwright/test';

// Exercises the playwright.config.ts webServer contract (jar path, cwd '..', .env, readiness URL)
// from step 2 on. Uses only the `request` fixture: no browser is launched.
test('health del jar empaquetado', async ({ request }) => {
  const response = await request.get('/api/health');
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({ status: 'UP' });
});
