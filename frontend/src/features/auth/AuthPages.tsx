import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { Alert, Button, Card, Field } from '../../components/ui';
import { ApiError, apiFetch } from '../../lib/api';
import type { Me } from './session';

type FieldErrors = Record<string, string>;
type Values = Record<string, string>;

const USERNAME_PATTERN = /^[A-Za-z0-9_.]{3,30}$/;

function safeNext(raw: string | null): string {
  return raw && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/grupos';
}

function validateLogin(values: Values): FieldErrors {
  const errors: FieldErrors = {};
  if (!values.username?.trim()) {
    errors.username = 'Ingresá tu usuario.';
  }
  if (!values.password) {
    errors.password = 'Ingresá tu contraseña.';
  }
  return errors;
}

function validateRegister(values: Values): FieldErrors {
  const errors: FieldErrors = {};
  if (!USERNAME_PATTERN.test(values.username ?? '')) {
    errors.username = 'Usá entre 3 y 30 letras, números, guion bajo o punto.';
  }
  const displayName = values.displayName?.trim() ?? '';
  if (displayName.length < 1 || displayName.length > 60) {
    errors.displayName = 'Ingresá un nombre de hasta 60 caracteres.';
  }
  if ((values.password ?? '').length < 8) {
    errors.password = 'La contraseña debe tener al menos 8 caracteres.';
  }
  return errors;
}

function throttleMessage(error: ApiError): string {
  const seconds = error.retryAfterSeconds;
  return seconds === undefined
    ? 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.'
    : `Demasiados intentos fallidos. Probá de nuevo en ${seconds} segundos.`;
}

type FormConfig = {
  path: string;
  initial: Values;
  validate: (values: Values) => FieldErrors;
};

function useAuthForm({ path, initial, validate }: FormConfig) {
  const [values, setValues] = useState<Values>(initial);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [summary, setSummary] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const formRef = useRef<HTMLFormElement>(null);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => apiFetch<Me>(path, { method: 'POST', body: values }),
    onSuccess: async (me) => {
      queryClient.setQueryData(['me'], me);
      await navigate(safeNext(searchParams.get('next')), { replace: true });
    },
    onError: (error) => {
      if (!(error instanceof ApiError)) {
        setSummary('No pudimos conectar con el servidor. Probá de nuevo.');
      } else if (error.status === 429) {
        setSummary(throttleMessage(error));
      } else if (error.status === 400 && error.errors?.length) {
        const byField: FieldErrors = {};
        for (const item of error.errors) {
          byField[item.field] = item.message;
        }
        setFieldErrors(byField);
        setSummary('Revisá los campos marcados.');
        setAttempt((n) => n + 1);
      } else {
        setSummary(error.detail);
      }
    },
  });

  // Focus moves after React commits the aria-invalid attributes.
  useEffect(() => {
    if (attempt > 0) {
      formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    }
  }, [attempt]);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const errors = validate(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setSummary('Revisá los campos marcados.');
      setAttempt((n) => n + 1);
      return;
    }
    setSummary(null);
    mutation.mutate();
  }

  function bind(name: string) {
    return {
      name,
      value: values[name] ?? '',
      error: fieldErrors[name],
      onChange: (event: { target: { value: string } }) =>
        setValues((current) => ({ ...current, [name]: event.target.value })),
    };
  }

  return { formRef, summary, bind, onSubmit, pending: mutation.isPending };
}

function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main id="main" className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center gap-4 p-4">
      <h1 className="text-2xl font-bold">{title}</h1>
      <Card className="flex flex-col gap-4">{children}</Card>
    </main>
  );
}

function Summary({ message }: { message: string | null }) {
  return message ? <Alert variant="error">{message}</Alert> : null;
}

export function LoginPage() {
  const form = useAuthForm({
    path: '/api/auth/login',
    initial: { username: '', password: '' },
    validate: validateLogin,
  });
  const { error: usernameError, ...username } = form.bind('username');
  const { error: passwordError, ...password } = form.bind('password');

  return (
    <AuthLayout title="Ingresar">
      <form ref={form.formRef} onSubmit={form.onSubmit} noValidate className="flex flex-col gap-4">
        <Summary message={form.summary} />
        <Field label="Usuario" autoComplete="username" error={usernameError} {...username} />
        <Field
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          error={passwordError}
          {...password}
        />
        <Button type="submit" disabled={form.pending}>
          Ingresar
        </Button>
      </form>
      <p className="text-sm text-text-muted">
        ¿No tenés cuenta?{' '}
        <Link to="/registro" className="text-accent underline">
          Creá una
        </Link>
      </p>
    </AuthLayout>
  );
}

export function RegisterPage() {
  const form = useAuthForm({
    path: '/api/auth/register',
    initial: { username: '', displayName: '', password: '' },
    validate: validateRegister,
  });
  const { error: usernameError, ...username } = form.bind('username');
  const { error: displayNameError, ...displayName } = form.bind('displayName');
  const { error: passwordError, ...password } = form.bind('password');

  return (
    <AuthLayout title="Crear cuenta">
      <form ref={form.formRef} onSubmit={form.onSubmit} noValidate className="flex flex-col gap-4">
        <Summary message={form.summary} />
        <Field label="Usuario" autoComplete="username" error={usernameError} {...username} />
        <Field
          label="Nombre para mostrar"
          autoComplete="name"
          error={displayNameError}
          {...displayName}
        />
        <Field
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          error={passwordError}
          {...password}
        />
        <Button type="submit" disabled={form.pending}>
          Crear cuenta
        </Button>
      </form>
      <p className="text-sm text-text-muted">
        ¿Ya tenés cuenta?{' '}
        <Link to="/ingresar" className="text-accent underline">
          Ingresá
        </Link>
      </p>
    </AuthLayout>
  );
}
