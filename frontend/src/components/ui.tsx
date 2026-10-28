import { cva, type VariantProps } from 'class-variance-authority';
import { clsx, type ClassValue } from 'clsx';
import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { Dialog } from 'radix-ui';
import { useId } from 'react';
import { twMerge } from 'tailwind-merge';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-opacity duration-150 ease-out disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-primary text-on-primary hover:opacity-90',
        secondary: 'border border-border-strong text-text hover:bg-surface-raised',
        danger: 'bg-danger-bg text-on-danger hover:opacity-90',
        ghost: 'text-text hover:bg-surface-raised',
      },
      size: {
        md: 'min-h-10 px-4 py-2',
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
      <label htmlFor={inputId} className="text-sm font-semibold">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={cn(
          'min-h-10 rounded-lg border border-border-strong bg-surface px-3 py-2 text-text',
          className,
        )}
        {...props}
      />
      {error ? (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('rounded-xl border border-border bg-surface p-4', className)} {...props} />
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
        <Dialog.Overlay className="fixed inset-0 z-40 bg-background/80" />
        <Dialog.Content className="fixed inset-x-4 top-16 z-50 mx-auto flex max-h-[calc(100dvh-8rem)] max-w-md flex-col gap-4 overflow-y-auto rounded-xl border border-border bg-surface p-4 shadow-[0_8px_24px_rgb(0_0_0/0.35)]">
          <Dialog.Title className="text-xl font-bold">{title}</Dialog.Title>
          <Dialog.Description className="text-sm text-text-muted">{description}</Dialog.Description>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

type AlertProps = {
  variant?: 'error' | 'info';
  children: ReactNode;
  className?: string;
};

export function Alert({ variant = 'info', children, className }: AlertProps) {
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={cn(
        'rounded-lg border px-4 py-3 text-sm',
        variant === 'error'
          ? 'border-danger text-danger'
          : 'border-border-strong bg-surface-raised text-text',
        className,
      )}
    >
      {children}
    </div>
  );
}
