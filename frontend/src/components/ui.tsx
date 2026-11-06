import { cva, type VariantProps } from 'class-variance-authority';
import { clsx, type ClassValue } from 'clsx';
import { CircleAlert, CircleCheck, Info } from 'lucide-react';
import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { Dialog } from 'radix-ui';
import { useId } from 'react';
import { Link, type LinkProps } from 'react-router';
import { twMerge } from 'tailwind-merge';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Focus is handled by the single global :focus-visible rule in index.css: no focus classes here.
const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-pill font-medium transition-opacity duration-150 ease-out active:opacity-80 disabled:cursor-not-allowed disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-primary text-on-primary hover:opacity-90',
        secondary: 'border border-border-strong text-text hover:bg-surface-raised',
        danger: 'bg-danger-bg text-on-danger hover:opacity-90',
        ghost: 'text-text hover:bg-surface-raised',
      },
      size: {
        md: 'min-h-11 px-6 py-2',
        live: 'min-h-16 w-full px-4 py-3 text-xl',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants>;

export function Button({ variant, size, className, type = 'button', ...props }: ButtonProps) {
  return (
    <button type={type} className={cn(buttonVariants({ variant, size }), className)} {...props} />
  );
}

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
};

export function Field({ label, error, id, className, ...props }: FieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = `${inputId}-error`;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={cn(
          'min-h-11 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text',
          className,
        )}
        {...props}
      />
      {error ? (
        <p id={errorId} className="flex items-start gap-1 text-sm text-danger">
          <CircleAlert aria-hidden="true" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  );
}

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('rounded-card border border-border bg-surface p-4', className)} {...props} />
  );
}

type DialogShellProps = {
  title: string;
  description: string;
  onClose: () => void;
  children: ReactNode;
};

/** Overlay z-40 and content z-50 sit above the sticky live scoreboard (z-10). */
export function DialogShell({ title, description, onClose, children }: DialogShellProps) {
  return (
    <Dialog.Root open onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-scrim" />
        <Dialog.Content className="fixed inset-x-4 top-16 z-50 mx-auto flex max-h-[calc(100dvh-8rem)] max-w-md flex-col gap-4 overflow-y-auto rounded-card border border-border-strong bg-surface p-4 shadow-dialog">
          <Dialog.Title className="text-xl font-medium">{title}</Dialog.Title>
          <Dialog.Description className="text-sm text-text-muted">{description}</Dialog.Description>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

type AlertVariant = 'error' | 'success' | 'info';

type AlertProps = {
  variant?: AlertVariant;
  children: ReactNode;
  className?: string;
};

const alertStyles: Record<AlertVariant, { box: string; Icon: typeof Info }> = {
  error: { box: 'border-danger text-danger', Icon: CircleAlert },
  success: { box: 'border-success text-success', Icon: CircleCheck },
  info: { box: 'border-border-strong bg-surface-raised text-text', Icon: Info },
};

/** Color is never the only cue: every variant carries an icon and its message as text. */
export function Alert({ variant = 'info', children, className }: AlertProps) {
  const { box, Icon } = alertStyles[variant];
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={cn('flex items-start gap-2 rounded-card border px-4 py-3 text-sm', box, className)}
    >
      <Icon aria-hidden="true" size={18} className="mt-0.5 shrink-0" />
      <div>{children}</div>
    </div>
  );
}

type TextLinkProps = LinkProps & { block?: boolean };

/** The only way to render a link: permanent underline, text color (color is never the only cue). */
export function TextLink({ block, className, ...props }: TextLinkProps) {
  return (
    <Link
      className={cn(
        'font-medium text-text underline underline-offset-4',
        block && 'inline-flex min-h-11 items-center',
        className,
      )}
      {...props}
    />
  );
}

/** Team A is a filled pill, team B an outlined one; the letter is always visible. */
export function TeamTag({ team }: { team: 'A' | 'B' }) {
  return (
    <span
      className={cn(
        'inline-flex min-h-6 min-w-6 items-center justify-center rounded-pill px-2 text-sm font-medium',
        team === 'A' ? 'bg-primary text-on-primary' : 'border border-border-strong text-text',
      )}
    >
      <span aria-hidden="true">{team}</span>
      <span className="sr-only">Equipo {team}</span>
    </span>
  );
}
