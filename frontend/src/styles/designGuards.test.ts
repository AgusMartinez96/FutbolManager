/// <reference types="node" />
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

// Guards for the monochrome visual direction (blueprint §7). Vitest runs with the frontend folder as cwd.
const SRC = join(process.cwd(), 'src');

/** Paths are relative to `src/`, with forward slashes. */
const SHADOW_DIALOG_FILES = ['components/ui.tsx'];
/** Inputs and selects (8px radius). */
const ROUNDED_LG_FILES = [
  'components/ui.tsx',
  'features/groups/PlayerForm.tsx',
  'features/matches/TeamColumn.tsx',
  'features/matches/MatchesPages.tsx',
  'features/live/CloseControls.tsx',
  'features/live/EventDialog.tsx',
];
/** Barlow Condensed: live scoreboard, group name and ranking. */
const DISPLAY_FONT_FILES = [
  'features/live/Scoreboard.tsx',
  'features/groups/GroupDetailPage.tsx',
  'features/stats/StatsPages.tsx',
];
/** The skip link reveals itself with `focus:`. */
const SKIP_LINK_FILES = ['app/AppShell.tsx'];
const TOKENS_CSS = 'styles/tokens.css';

interface Rule {
  id: string;
  pattern: RegExp;
  /** Returns true when this match is an explicit exception. */
  allowed?: (path: string, match: string) => boolean;
}

const PALETTE =
  '(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\\d{2,3}|white|black';
const COLOR_UTILITY =
  'bg|text|border|ring|outline|fill|stroke|from|via|to|divide|decoration|caret|accent|placeholder';

const COLOR_LITERAL_RULES: Rule[] = [
  { id: 'hex color', pattern: /#[0-9a-fA-F]{3,8}\b/g },
  { id: 'color function', pattern: /\b(?:rgba?|hsla?|oklch)\(/g },
];
const LEGACY_TOKEN_RULES: Rule[] = [
  {
    id: 'accent/mvp token',
    pattern:
      /(?<![\w-])(?:bg|text|border|outline|fill|stroke|from|via|to|divide|decoration|ring)-(?:accent|mvp)(?:-[\w-]+)?(?![\w-])|--(?:fm|color)-(?:accent|mvp)/g,
  },
];
const GOOGLE_FONTS_RULE: Rule = { id: 'google fonts', pattern: /fonts\.googleapis\.com/g };

const SOURCE_RULES: Rule[] = [
  {
    id: 'heavy or light weight',
    pattern: /(?<![\w-])font-(?:bold|semibold|extrabold|black|light|extralight|thin)(?![\w-])/g,
  },
  {
    id: 'shadow',
    pattern: /(?<![\w-])shadow(?:-[\w./[\]-]+)?(?![\w-])/g,
    allowed: (path, match) => match === 'shadow-dialog' && SHADOW_DIALOG_FILES.includes(path),
  },
  {
    id: 'rounded size',
    pattern: /(?<![\w-])rounded-(?:xs|sm|md|xl|2xl|3xl|none)(?![\w-])/g,
  },
  {
    id: 'rounded-lg outside inputs',
    pattern: /(?<![\w-])rounded-lg(?![\w-])/g,
    allowed: (path) => ROUNDED_LG_FILES.includes(path),
  },
  { id: 'font-mono', pattern: /(?<![\w-])font-mono(?![\w-])/g },
  {
    id: 'font-display outside allowed files',
    pattern: /(?<![\w-])font-display(?![\w-])/g,
    allowed: (path) => DISPLAY_FONT_FILES.includes(path),
  },
  ...LEGACY_TOKEN_RULES,
  ...COLOR_LITERAL_RULES,
  {
    id: 'tailwind palette color',
    pattern: new RegExp(`(?<![\\w-])(?:${COLOR_UTILITY})-(?:${PALETTE})(?![\\w-])`, 'g'),
  },
  { id: 'focus override', pattern: /(?<![\w-])(?:outline-none|outline-hidden)(?![\w-])/g },
  { id: 'ring-offset', pattern: /(?<![\w-])ring-offset(?:-[\w-]+)?(?![\w-])/g },
  { id: 'ring', pattern: /(?<![\w-])ring(?:-[\w-]+)?(?![\w-])/g },
  {
    id: 'focus: variant',
    pattern: /(?<![\w-])focus:[\w![-]/g,
    allowed: (path) => SKIP_LINK_FILES.includes(path),
  },
  GOOGLE_FONTS_RULE,
];

const STYLE_RULES: Rule[] = [
  ...COLOR_LITERAL_RULES.map((rule) => ({
    ...rule,
    allowed: (path: string) => path === TOKENS_CSS,
  })),
  ...LEGACY_TOKEN_RULES,
  GOOGLE_FONTS_RULE,
];

function scan(path: string, text: string, rules: Rule[]): string[] {
  const found: string[] = [];
  for (const rule of rules) {
    for (const [match] of text.matchAll(rule.pattern)) {
      if (!rule.allowed?.(path, match)) found.push(`${rule.id}: ${match}`);
    }
  }
  return found;
}

function filesUnderSrc(accept: (path: string) => boolean): string[] {
  return readdirSync(SRC, { recursive: true, encoding: 'utf8' })
    .map((path) => path.replaceAll('\\', '/'))
    .filter(accept);
}

const isSource = (path: string) => /\.tsx?$/.test(path) && !/\.test\.[^/]+$/.test(path);
const isStyle = (path: string) => path.endsWith('.css');

describe('design guards: rules catch what they should', () => {
  // Hex literals are built from parts: ESLint rejects hex strings in src/.
  const hash = '#';
  const mustFlag: [string, string, string][] = [
    ['font-bold', 'features/x/Page.tsx', 'font-bold'],
    ['font-semibold', 'features/x/Page.tsx', 'font-semibold'],
    ['shadow-lg', 'features/x/Page.tsx', 'shadow-lg'],
    ['bare shadow', 'features/x/Page.tsx', 'shadow'],
    ['shadow-dialog outside ui.tsx', 'features/x/Page.tsx', 'shadow-dialog'],
    ['rounded-xl', 'features/x/Page.tsx', 'rounded-xl'],
    ['rounded-md', 'features/x/Page.tsx', 'rounded-md'],
    ['rounded-lg in a card file', 'features/x/Page.tsx', 'rounded-lg'],
    ['font-mono', 'features/x/Page.tsx', 'font-mono'],
    ['outline-none', 'features/x/Page.tsx', 'outline-none'],
    ['ring-offset-2', 'features/x/Page.tsx', 'ring-offset-2'],
    ['focus: variant', 'features/x/Page.tsx', 'focus:underline'],
    ['bg-white', 'features/x/Page.tsx', 'bg-white'],
    ['border-red-500', 'features/x/Page.tsx', 'border-red-500'],
    ['accent token', 'features/x/Page.tsx', 'text-accent'],
    ['hex color', 'features/x/Page.tsx', `${hash}abc`],
    ['rgb() color', 'features/x/Page.tsx', 'rgb(0 0 0)'],
    ['font-display in a disallowed path', 'features/x/Page.tsx', 'font-display'],
    ['google fonts', 'features/x/Page.tsx', 'https://fonts.googleapis.com/css'],
  ];
  it.each(mustFlag)('flags %s', (_name, path, text) => {
    expect(scan(path, `className="${text}"`, SOURCE_RULES)).not.toEqual([]);
  });

  const mustPass: [string, string, string][] = [
    ['font-medium', 'features/x/Page.tsx', 'font-medium'],
    ['shadow-dialog in components/ui.tsx', 'components/ui.tsx', 'shadow-dialog'],
    ['rounded-lg in an allowed file', 'features/groups/PlayerForm.tsx', 'rounded-lg'],
    ['rounded-pill and rounded-card', 'features/x/Page.tsx', 'rounded-pill rounded-card'],
    ['font-display in the scoreboard', 'features/live/Scoreboard.tsx', 'font-display'],
    ['focus: in the skip link', 'app/AppShell.tsx', 'focus:not-sr-only'],
    ['token classes', 'features/x/Page.tsx', 'bg-primary text-on-primary border-border-strong'],
    ['prose "focus: true" object key', 'features/x/Page.tsx', 'focus: true'],
    ['an in-page anchor', 'features/x/Page.tsx', 'href="#contenido"'],
  ];
  it.each(mustPass)('accepts %s', (_name, path, text) => {
    expect(scan(path, `className="${text}"`, SOURCE_RULES)).toEqual([]);
  });

  it('flags color literals in a stylesheet other than tokens.css', () => {
    expect(scan('styles/index.css', `a { color: ${hash}fff; }`, STYLE_RULES)).not.toEqual([]);
    expect(scan('styles/index.css', 'a { color: rgb(0 0 0); }', STYLE_RULES)).not.toEqual([]);
    expect(scan('styles/index.css', 'a { color: var(--fm-accent); }', STYLE_RULES)).not.toEqual([]);
    expect(scan(TOKENS_CSS, `:root { --x: ${hash}fff; }`, STYLE_RULES)).toEqual([]);
  });
});

describe('design guards: the current tree', () => {
  it('has sources and stylesheets to scan', () => {
    expect(filesUnderSrc(isSource).length).toBeGreaterThan(20);
    expect(filesUnderSrc(isStyle)).toContain(TOKENS_CSS);
  });

  it('has no forbidden visual patterns in TypeScript sources', () => {
    const offenders = filesUnderSrc(isSource).flatMap((path) =>
      scan(path, readFileSync(join(SRC, path), 'utf8'), SOURCE_RULES).map(
        (finding) => `${path} -> ${finding}`,
      ),
    );
    expect(offenders).toEqual([]);
  });

  it('has no forbidden patterns in stylesheets', () => {
    const offenders = filesUnderSrc(isStyle).flatMap((path) =>
      scan(path, readFileSync(join(SRC, path), 'utf8'), STYLE_RULES).map(
        (finding) => `${path} -> ${finding}`,
      ),
    );
    expect(offenders).toEqual([]);
  });
});
