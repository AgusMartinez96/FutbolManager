import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const PASSWORD = 'e2e-password-1';

function uniqueUsername(): string {
  return `e2e_${Math.random().toString(36).slice(2, 10)}`;
}

async function expectNoSeriousA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze();
  const serious = results.violations.filter(
    (violation) => violation.impact === 'serious' || violation.impact === 'critical',
  );
  expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
}

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

async function expectAccessibleScreen(page: Page) {
  await expectNoSeriousA11yViolations(page);
  await expectNoHorizontalScroll(page);
}

async function register(page: Page, username: string, displayName: string) {
  await page.goto('/registro');
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel('Nombre para mostrar').fill(displayName);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('heading', { name: 'Mis grupos' })).toBeVisible();
}

async function createGroup(page: Page, name: string) {
  await page.getByRole('button', { name: 'Crear grupo' }).click();
  await page.getByLabel('Nombre del grupo').fill(name);
  await page.getByRole('button', { name: 'Crear', exact: true }).click();
  await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible();
}

async function addPlayer(page: Page, name: string, level: string) {
  await page.getByRole('button', { name: 'Agregar jugador' }).click();
  await page.getByLabel('Nombre', { exact: true }).fill(name);
  await page.getByLabel('Nivel (1,0 a 10,0)').fill(level);
  await page.getByRole('button', { name: 'Agregar', exact: true }).click();
  await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
}

test('registro y grupo', async ({ page }) => {
  await page.goto('/ingresar');
  await expect(page.getByRole('heading', { name: 'Ingresar' })).toBeVisible();
  await expectAccessibleScreen(page);
  await page.goto('/registro');
  await expect(page.getByRole('heading', { name: 'Crear cuenta' })).toBeVisible();
  await expectAccessibleScreen(page);

  await register(page, uniqueUsername(), 'Admin E2E');
  await expectAccessibleScreen(page);

  await createGroup(page, 'Los de prueba');
  for (const [name, level] of [
    ['Ana Invitada', '4,5'],
    ['Beto Invitado', '6,0'],
    ['Caro Invitada', '7,5'],
  ]) {
    await addPlayer(page, name, level);
  }
  await expect(page.getByRole('link', { name: /Invitad[ao]$/ })).toHaveCount(3);
  await expectAccessibleScreen(page);
});

test('partido completo', async ({ page }) => {
  await register(page, uniqueUsername(), 'Admin E2E');
  await createGroup(page, 'Partido de prueba');
  for (const [name, level] of [
    ['Ana Invitada', '4,5'],
    ['Beto Invitado', '6,0'],
    ['Caro Invitada', '7,5'],
  ]) {
    await addPlayer(page, name, level);
  }
  const everyone = ['Admin E2E', 'Ana Invitada', 'Beto Invitado', 'Caro Invitada'];

  await page.getByRole('link', { name: 'Partidos' }).click();
  await page.getByRole('button', { name: 'Crear partido' }).click();
  await page.getByLabel('Fecha y hora').fill('2030-01-10T21:00');
  await page.getByLabel('Lugar').fill('Cancha de prueba');
  await page.getByRole('button', { name: 'Crear', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Cancha de prueba/ })).toBeVisible();

  for (const name of everyone) {
    await page.getByRole('combobox', { name: `Asistencia de ${name}` }).selectOption('CONFIRMED');
  }
  await expect(page.getByText('confirmados')).toContainText('4');

  await page.getByRole('button', { name: 'Armar equipos' }).click();
  const teamACard = page.getByRole('heading', { name: 'Equipo A' }).locator('xpath=..');
  await expect(teamACard.getByRole('combobox').first()).toBeVisible();
  const teamA = (
    await teamACard
      .getByRole('combobox')
      .evaluateAll((selects) => selects.map((select) => select.getAttribute('aria-label') ?? ''))
  ).map((label) => label.replace('Puesto de ', ''));
  expect(teamA.length).toBeGreaterThan(0);

  await page.getByRole('button', { name: 'Guardar equipos' }).click();
  await expect(page.getByText('Hay cambios sin guardar.')).toBeHidden();
  await page.getByRole('button', { name: 'Empezar partido' }).click();
  await expect(page.getByRole('heading', { name: 'En vivo' })).toBeVisible();
  const groupId = /\/grupos\/([^/]+)/.exec(page.url())?.[1] ?? '';

  await expectAccessibleScreen(page);
  for (const label of ['Gol A', 'Gol B', 'Atajada A', 'Jugadón A', 'Deshacer']) {
    const box = await page.getByRole('button', { name: label, exact: true }).boundingBox();
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(64);
  }

  for (let goal = 0; goal < 2; goal++) {
    await page.getByRole('button', { name: 'Gol A', exact: true }).click();
    await page.getByRole('dialog').getByLabel('Autor').selectOption({ index: 1 });
    await page.getByRole('button', { name: 'Registrar' }).click();
    await expect(page.getByRole('dialog')).toBeHidden();
  }
  await page.getByRole('button', { name: 'Jugadón A', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Autor').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.getByRole('status', { name: 'Marcador' })).toContainText('2');

  await page.getByRole('button', { name: 'Cerrar partido' }).click();
  await page.getByRole('button', { name: 'Confirmar cierre' }).click();
  await expect(page.getByRole('heading', { name: 'Resumen del ELO' })).toBeVisible();
  await page.getByRole('button', { name: 'Listo' }).click();

  await page.goto(`/grupos/${groupId}/ranking`);
  await expect(page.getByRole('heading', { name: 'Ranking' })).toBeVisible();
  for (const name of teamA) {
    const row = page
      .getByRole('row')
      .filter({ has: page.getByRole('link', { name, exact: true }) });
    const elo = Number(await row.getByRole('cell').nth(2).innerText());
    expect(elo, `ELO de ${name}`).toBeGreaterThan(1000);
  }
});

test('throttling de login', async ({ page, context }) => {
  const username = uniqueUsername();
  await register(page, username, 'Throttle E2E');
  await context.clearCookies();

  await page.goto('/ingresar');
  for (let attempt = 0; attempt < 6; attempt++) {
    await page.getByLabel('Usuario').fill(username);
    await page.getByLabel('Contraseña').fill('contraseña-incorrecta');
    await page.getByRole('button', { name: 'Ingresar' }).click();
    if (attempt < 5) {
      await expect(page.getByText('Usuario o contraseña incorrectos.').first()).toBeVisible();
    }
  }
  await expect(page.getByText(/Demasiados intentos fallidos/).first()).toBeVisible();
});
