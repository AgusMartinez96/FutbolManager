import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Locator, type Page } from '@playwright/test';

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

const MEMBERS = ['Admin E2E', 'Ana Invitada', 'Beto Invitado', 'Caro Invitada'];

async function addPlayers(page: Page) {
  for (const [name, level] of [
    ['Ana Invitada', '4,5'],
    ['Beto Invitado', '6,0'],
    ['Caro Invitada', '7,5'],
  ]) {
    await addPlayer(page, name, level);
  }
}

async function createMatch(page: Page, place: string) {
  await page.getByRole('link', { name: 'Partidos' }).click();
  await page.getByRole('button', { name: 'Crear partido' }).click();
  await page.getByLabel('Fecha y hora').fill('2030-01-10T21:00');
  await page.getByLabel('Lugar').fill(place);
  await page.getByRole('button', { name: 'Crear', exact: true }).click();
  await expect(page.getByRole('heading', { name: new RegExp(place) })).toBeVisible();
}

async function confirmEveryone(page: Page) {
  for (const name of MEMBERS) {
    await page.getByRole('combobox', { name: `Asistencia de ${name}` }).selectOption('CONFIRMED');
  }
}

async function startMatch(page: Page) {
  await page.getByRole('button', { name: 'Armar equipos' }).click();
  await page.getByRole('button', { name: 'Guardar equipos' }).click();
  await expect(page.getByText('Hay cambios sin guardar.')).toBeHidden();
  await page.getByRole('button', { name: 'Empezar partido' }).click();
  await expect(page.getByRole('heading', { name: 'En vivo' })).toBeVisible();
}

async function expectMinHeight(target: Locator, min: number, label: string) {
  const box = await target.boundingBox();
  expect(box?.height ?? 0, label).toBeGreaterThanOrEqual(min);
}

/** Tab until `target` has focus (a real keyboard focus, so :focus-visible applies). */
async function tabTo(page: Page, target: Locator) {
  for (let presses = 0; presses < 20; presses++) {
    await page.keyboard.press('Tab');
    if (await target.evaluate((el) => el === document.activeElement)) return;
  }
  throw new Error('El elemento nunca recibió el foco con Tab.');
}

async function expectUnderlinedLink(link: Locator) {
  const line = await link.evaluate((el) => getComputedStyle(el).textDecorationLine);
  expect(line).toContain('underline');
}

async function expectFocusRing(button: Locator) {
  const ring = await button.evaluate((el) => {
    const probe = document.createElement('span');
    probe.style.color = 'var(--fm-focus)';
    document.body.append(probe);
    const token = getComputedStyle(probe).color;
    probe.remove();
    const style = getComputedStyle(el);
    return {
      style: style.outlineStyle,
      width: style.outlineWidth,
      offset: style.outlineOffset,
      color: style.outlineColor,
      token,
    };
  });
  expect(ring).toMatchObject({ style: 'solid', width: '2px', offset: '2px' });
  expect(ring.color).toBe(ring.token);
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

test('marcador sin saltos', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await register(page, uniqueUsername(), 'Admin E2E');
  await createGroup(page, 'Marcador de prueba');
  await addPlayers(page);
  await createMatch(page, 'Cancha de marcador');
  await confirmEveryone(page);
  await startMatch(page);

  await expectAccessibleScreen(page);
  for (const label of [
    'Gol A',
    'Gol B',
    'Atajada A',
    'Atajada B',
    'Corte A',
    'Corte B',
    'Jugadón A',
    'Jugadón B',
  ]) {
    const box = await page.getByRole('button', { name: label, exact: true }).boundingBox();
    expect(box?.height ?? 0, label).toBeGreaterThanOrEqual(64);
  }

  const styleOf = (label: string) =>
    page.getByRole('button', { name: label, exact: true }).evaluate((el) => {
      const style = getComputedStyle(el);
      return { background: style.backgroundColor, borderWidth: style.borderTopWidth };
    });
  const goalA = await styleOf('Gol A');
  const goalB = await styleOf('Gol B');
  expect(goalA.background).not.toBe('rgba(0, 0, 0, 0)');
  expect(goalB.background).toBe('rgba(0, 0, 0, 0)');
  expect(parseFloat(goalB.borderWidth)).toBeGreaterThanOrEqual(1);

  const scoreA = page.getByTestId('score-A');
  const widthWith = async (goals: number) => {
    await expect(scoreA).toHaveText(String(goals));
    return (await scoreA.boundingBox())?.width ?? 0;
  };
  let widthAtNine = 0;
  for (let goal = 1; goal <= 10; goal++) {
    await page.getByRole('button', { name: 'Gol A', exact: true }).click();
    await page.getByRole('button', { name: 'Registrar' }).click();
    await expect(page.getByRole('dialog')).toBeHidden();
    if (goal === 9) widthAtNine = await widthWith(9);
  }
  const widthAtTen = await widthWith(10);
  expect(widthAtNine).toBeGreaterThan(0);
  expect(widthAtTen).toBe(widthAtNine);
  await expectNoHorizontalScroll(page);
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

for (const theme of ['light', 'dark'] as const) {
  test(`tema ${theme}: contraste, subrayado, foco y tamaños`, async ({ page }) => {
    await page.addInitScript((value) => window.localStorage.setItem('fm-theme', value), theme);

    await page.goto('/ingresar');
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
    await expectAccessibleScreen(page);
    await expectUnderlinedLink(page.getByRole('link', { name: 'Creá una' }));
    await expectMinHeight(page.getByLabel('Usuario'), 44, 'input Usuario');
    const submit = page.getByRole('button', { name: 'Ingresar' });
    await tabTo(page, submit);
    await expectFocusRing(submit);

    await page.goto('/registro');
    await expectAccessibleScreen(page);
    await expectUnderlinedLink(page.getByRole('link', { name: 'Ingresá' }));

    await register(page, uniqueUsername(), 'Admin E2E');
    await expectAccessibleScreen(page);
    await expectMinHeight(page.getByRole('button', { name: /Cambiar a tema/ }), 44, 'ThemeToggle');
    await expectMinHeight(page.getByRole('button', { name: 'Crear grupo' }), 44, 'Button md');

    await createGroup(page, `Grupo ${theme}`);
    await addPlayers(page);
    await expectAccessibleScreen(page);
    await expectMinHeight(page.getByRole('link', { name: 'Partidos' }), 44, 'TextLink block');

    await createMatch(page, `Cancha ${theme}`);
    await confirmEveryone(page);
    await expectMinHeight(
      page.getByRole('combobox', { name: 'Asistencia de Ana Invitada' }),
      44,
      'select',
    );
    await startMatch(page);
    await expectAccessibleScreen(page);
    for (const label of ['Gol A', 'Gol B', 'Atajada A', 'Corte B', 'Jugadón A', 'Jugadón B']) {
      await expectMinHeight(page.getByRole('button', { name: label, exact: true }), 64, label);
    }
  });
}

test('foco no tapado por el marcador sticky', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 480 });
  await register(page, uniqueUsername(), 'Admin E2E');
  await createGroup(page, 'Foco de prueba');
  await addPlayers(page);
  await createMatch(page, 'Cancha de foco');
  await confirmEveryone(page);
  await startMatch(page);
  await page.getByRole('button', { name: 'Gol A', exact: true }).click();
  await page.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();

  const buttons = page.getByRole('region', { name: 'Registro' }).getByRole('button');
  const count = await buttons.count();
  expect(count).toBeGreaterThanOrEqual(9);

  const gapBelowScoreboard = async () => {
    await page.evaluate(() => new Promise((done) => requestAnimationFrame(() => done(null))));
    return page.evaluate(() => {
      const bar = document.querySelector('[role="status"][aria-label="Marcador"]');
      const focused = document.activeElement;
      if (!bar || !focused) return Number.NEGATIVE_INFINITY;
      return focused.getBoundingClientRect().top - bar.getBoundingClientRect().bottom;
    });
  };

  await page.evaluate(() => window.scrollTo(0, 0));
  await tabTo(page, buttons.nth(0));
  expect(await gapBelowScoreboard()).toBeGreaterThanOrEqual(0);
  for (let index = 1; index < count; index++) {
    await page.keyboard.press('Tab');
    await expect(buttons.nth(index)).toBeFocused();
    expect(await gapBelowScoreboard(), `Tab hacia el botón ${index}`).toBeGreaterThanOrEqual(0);
  }
  expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
  for (let index = count - 2; index >= 0; index--) {
    await page.keyboard.press('Shift+Tab');
    await expect(buttons.nth(index)).toBeFocused();
    expect(await gapBelowScoreboard(), `Shift+Tab hacia el botón ${index}`).toBeGreaterThanOrEqual(
      0,
    );
  }
});
