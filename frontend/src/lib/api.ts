export type FieldError = { field: string; message: string };

export class ApiError extends Error {
  readonly status: number;
  readonly detail: string;
  readonly errors?: FieldError[];
  readonly retryAfterSeconds?: number;

  constructor(status: number, detail: string, errors?: FieldError[], retryAfterSeconds?: number) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
    this.errors = errors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

type RequestInitLite = { method?: string; body?: unknown };

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) {
      return decodeURIComponent(cookie.slice(prefix.length));
    }
  }
  return undefined;
}

async function csrfToken(): Promise<string> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) {
    return existing;
  }
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  const fromCookie = readCookie(CSRF_COOKIE);
  if (fromCookie) {
    return fromCookie;
  }
  const payload = (await response.json().catch(() => null)) as { token?: string } | null;
  return payload?.token ?? '';
}

async function toApiError(response: Response): Promise<ApiError> {
  const problem = (await response.json().catch(() => null)) as {
    detail?: string;
    errors?: FieldError[];
  } | null;
  const retryAfter = Number.parseInt(response.headers.get('Retry-After') ?? '', 10);
  return new ApiError(
    response.status,
    problem?.detail ?? 'Algo salió mal. Probá de nuevo.',
    problem?.errors,
    Number.isFinite(retryAfter) ? retryAfter : undefined,
  );
}

export async function apiFetch<T>(path: string, init: RequestInitLite = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    headers[CSRF_HEADER] = await csrfToken();
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
