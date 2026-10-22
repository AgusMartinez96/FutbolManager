import { defineConfig, devices } from '@playwright/test';

// E2E runs against the packaged jar (frontend built into it), not the Vite dev server.
// Preconditions (step 2 and step 18 Verify): `npm --prefix frontend run build`, `./mvnw -q -DskipTests package`,
// `docker compose up -d --wait db`, `npm --prefix frontend run e2e:install`.
export default defineConfig({
  testDir: './e2e',
  testIgnore: ['**/blueprints/**'],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'mobile-chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } },
    },
  ],
  webServer: {
    // cwd is the project root so the jar finds target/ and loads ./.env (spring.config.import).
    command: 'java -jar target/futbol-manager.jar',
    cwd: '..',
    url: 'http://localhost:8080/api/health',
    reuseExistingServer: false,
    timeout: 120_000,
    stdout: 'pipe',
  },
});
