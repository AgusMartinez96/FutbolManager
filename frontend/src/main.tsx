import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { router } from './app/router';
import './styles/index.css';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1 } },
});

const root = document.getElementById('root');
if (!root) {
  throw new Error('Falta el elemento #root en index.html');
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
