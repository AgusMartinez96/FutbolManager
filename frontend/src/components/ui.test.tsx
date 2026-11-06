import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { Alert, Button, DialogShell, Field, TeamTag, TextLink } from './ui';

describe('Button', () => {
  it.each(['primary', 'secondary', 'danger', 'ghost'] as const)(
    'renders the %s variant with an accessible name',
    (variant) => {
      render(<Button variant={variant}>Guardar</Button>);
      expect(screen.getByRole('button', { name: 'Guardar' })).toBeEnabled();
    },
  );

  it('is really disabled when disabled', () => {
    render(<Button disabled>Guardar</Button>);
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled();
  });

  it('uses 44px targets for md and 64px for live', () => {
    render(
      <>
        <Button>Normal</Button>
        <Button size="live">En vivo</Button>
      </>,
    );
    expect(screen.getByRole('button', { name: 'Normal' })).toHaveClass('min-h-11');
    expect(screen.getByRole('button', { name: 'En vivo' })).toHaveClass('min-h-16');
  });
});

describe('Field', () => {
  it('links the label, the invalid state and the error message', () => {
    render(<Field label="Usuario" error="Ingresá tu usuario." />);
    const input = screen.getByLabelText('Usuario');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Ingresá tu usuario.');
  });

  it('is not invalid without an error', () => {
    render(<Field label="Usuario" />);
    expect(screen.getByLabelText('Usuario')).not.toHaveAttribute('aria-invalid');
  });
});

describe('Alert', () => {
  it('announces errors with role alert, an aria-hidden icon and the message as text', () => {
    render(<Alert variant="error">Algo falló.</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Algo falló.');
    expect(alert.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('announces success with role status, an aria-hidden icon and the message as text', () => {
    render(<Alert variant="success">Listo.</Alert>);
    const status = screen.getByRole('status');
    expect(status).toHaveTextContent('Listo.');
    expect(status.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });
});

describe('TeamTag', () => {
  it.each(['A', 'B'] as const)('shows the letter and names team %s', (team) => {
    render(<TeamTag team={team} />);
    expect(screen.getByText(team)).toBeVisible();
    expect(screen.getByText(`Equipo ${team}`)).toBeInTheDocument();
  });
});

describe('TextLink', () => {
  it('exposes a link with its href', () => {
    render(
      <MemoryRouter>
        <TextLink to="/grupos">Mis grupos</TextLink>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Mis grupos' })).toHaveAttribute('href', '/grupos');
  });

  it('uses a 44px target when block', () => {
    render(
      <MemoryRouter>
        <TextLink to="/grupos" block>
          Mis grupos
        </TextLink>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Mis grupos' })).toHaveClass('min-h-11');
  });
});

describe('DialogShell', () => {
  it('has an accessible title and description', () => {
    render(
      <DialogShell title="Registrar gol" description="Elegí el autor." onClose={() => {}}>
        <p>Contenido</p>
      </DialogShell>,
    );
    const dialog = screen.getByRole('dialog', { name: 'Registrar gol' });
    expect(dialog).toHaveAccessibleDescription('Elegí el autor.');
  });
});
