import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Toaster } from 'sonner';
import './index.css';
import App from './App.tsx';
import { LocalAuthProvider } from './LocalAuthProvider.tsx';

const root = document.getElementById('root');
if (!root) throw new Error('Root element not found');

createRoot(root).render(
  <StrictMode>
    <LocalAuthProvider>
      <App />
    </LocalAuthProvider>
    <Toaster richColors position="bottom-right" />
  </StrictMode>,
);
