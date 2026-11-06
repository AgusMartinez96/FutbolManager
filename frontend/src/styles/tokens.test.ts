/// <reference types="node" />
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

// vite.config sets `css: false`, so `?raw` imports come back empty: read the files from disk.
// Vitest runs with the frontend folder as cwd.
const stylesRoot = join(process.cwd(), 'src');
const css = readFileSync(join(stylesRoot, 'styles', 'tokens.css'), 'utf8');

/** Every stylesheet under src/, as [relative path, contents]. */
function stylesheets(): [string, string][] {
  return readdirSync(stylesRoot, { recursive: true, encoding: 'utf8' })
    .map((path) => path.replaceAll('\\', '/'))
    .filter((path) => path.endsWith('.css'))
    .map((path): [string, string] => [path, readFileSync(join(stylesRoot, path), 'utf8')]);
}

type Rgb = [number, number, number];
type Theme = 'dark' | 'light';

/** Declarations of a rule whose selector list contains `selector`. */
function declarations(selector: string): Record<string, string> {
  const out: Record<string, string> = {};
  const source = css.replace(/\/\*[\s\S]*?\*\//g, '');
  for (const [, selectors, body] of source.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (!selectors.split(',').some((s) => s.trim() === selector)) continue;
    for (const [, name, value] of body.matchAll(/(--fm-[\w-]+)\s*:\s*([^;]+);/g)) {
      out[name] = value.trim();
    }
  }
  return out;
}

const themes: Record<Theme, Record<string, string>> = {
  dark: declarations("[data-theme='dark']"),
  light: declarations("[data-theme='light']"),
};

function parseHex(value: string): Rgb {
  const m = /^#([0-9a-f]{6})$/i.exec(value);
  if (!m) throw new Error(`Not a 6-digit hex color: ${value}`);
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function parseScrim(value: string): { rgb: Rgb; alpha: number } {
  const m = /^rgb\((\d+) (\d+) (\d+) \/ ([\d.]+)\)$/.exec(value);
  if (!m) throw new Error(`Not an rgb(r g b / a) color: ${value}`);
  return { rgb: [Number(m[1]), Number(m[2]), Number(m[3])], alpha: Number(m[4]) };
}

function luminance([r, g, b]: Rgb): number {
  const [lr, lg, lb] = [r, g, b].map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
}

function contrast(a: Rgb, b: Rgb): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

function color(theme: Theme, token: string): Rgb {
  const value = themes[theme][`--fm-${token}`];
  if (!value) throw new Error(`Missing --fm-${token} in ${theme}`);
  return parseHex(value);
}

function compose(top: { rgb: Rgb; alpha: number }, under: Rgb): Rgb {
  return top.rgb.map((c, i) => Math.round(c * top.alpha + under[i] * (1 - top.alpha))) as Rgb;
}

const surfaces = ['background', 'surface', 'surface-raised'];

describe.each<Theme>(['dark', 'light'])('tokens (%s)', (theme) => {
  it('text tokens reach 4.5:1 on every surface', () => {
    for (const surface of surfaces) {
      for (const token of ['text', 'text-muted', 'danger', 'success']) {
        expect(
          contrast(color(theme, token), color(theme, surface)),
          `${token} on ${surface}`,
        ).toBeGreaterThanOrEqual(4.5);
      }
    }
    expect(
      contrast(color(theme, 'on-primary'), color(theme, 'primary')),
      'on-primary on primary',
    ).toBeGreaterThanOrEqual(4.5);
    expect(
      contrast(color(theme, 'on-danger'), color(theme, 'danger-bg')),
      'on-danger on danger-bg',
    ).toBeGreaterThanOrEqual(4.5);
  });

  it('control boundaries reach 3:1 on every surface', () => {
    for (const surface of surfaces) {
      for (const token of ['border-strong', 'focus']) {
        expect(
          contrast(color(theme, token), color(theme, surface)),
          `${token} on ${surface}`,
        ).toBeGreaterThanOrEqual(3);
      }
    }
  });

  it('dialog scrim keeps the dialog distinguishable over the brightest composed background', () => {
    const scrim = parseScrim(themes[theme]['--fm-scrim']);
    const brightest = ['background', 'surface', 'surface-raised', 'text', 'primary']
      .map((token) => color(theme, token))
      .sort((a, b) => luminance(b) - luminance(a))[0];
    const worst = compose(scrim, brightest);

    if (theme === 'light') {
      expect(
        contrast(color(theme, 'surface'), worst),
        'dialog fill vs worst backdrop',
      ).toBeGreaterThanOrEqual(3);
    } else {
      expect(
        contrast(color(theme, 'border-strong'), worst),
        'dialog border vs worst backdrop',
      ).toBeGreaterThanOrEqual(3);
    }
    for (const token of ['text', 'text-muted']) {
      expect(
        contrast(color(theme, token), color(theme, 'surface')),
        `${token} on dialog`,
      ).toBeGreaterThanOrEqual(4.5);
    }
  });
});

describe('token inventory', () => {
  it('defines the new tokens in both themes or at the root', () => {
    for (const theme of ['dark', 'light'] as const) {
      for (const name of ['--fm-success', '--fm-scrim', '--fm-shadow-dialog']) {
        expect(themes[theme][name], `${name} in ${theme}`).toBeTruthy();
      }
    }
    const root = declarations(':root');
    expect(root['--fm-radius-pill']).toBe('30px');
    expect(root['--fm-radius-card']).toBe('0');
    expect(root['--fm-text-score']).toBe('4rem');
  });

  it('keeps hex colors and rgb() out of every other stylesheet', () => {
    const others = stylesheets().filter(([path]) => !path.endsWith('tokens.css'));
    expect(others.length).toBeGreaterThan(0);
    const offenders = others
      .filter(([, text]) => /#[0-9a-f]{3,8}\b|\brgba?\(/i.test(text))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });
});
