import { useCallback, useEffect, useMemo, useState } from 'react';
import { createClient } from '@connectrpc/connect';
import { createGrpcWebTransport } from '@connectrpc/connect-web';
import { CounterService } from './gen/medusa/counter/v1/counter_service_pb.ts';
import reactLogo from './assets/react.svg';
import viteLogo from './assets/vite.svg';
import heroImg from './assets/hero.png';
import './App.css';
import { useAuth } from './useAuth.tsx';
import { SignInWall } from './SignInWall.tsx';

const CORE_SERVICE_URL = import.meta.env.VITE_CORE_SERVICE_URL as string;

if (!CORE_SERVICE_URL) {
  throw new Error('VITE_CORE_SERVICE_URL is not set');
}

const transport = createGrpcWebTransport({
  baseUrl: CORE_SERVICE_URL,
});

const client = createClient(CounterService, transport);

function AppContent({ token }: { token: string }) {
  const { handleUnauthorized } = useAuth();
  const [count, setCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const headers = useMemo(
    () => ({ Authorization: `Bearer ${token}` }),
    [token],
  );

  const handleError = useCallback(
    (err: unknown) => {
      const message = err instanceof Error ? err.message : String(err);
      if (message.includes('401') || message.includes('unauthenticated')) {
        handleUnauthorized();
      } else {
        setError(message);
      }
    },
    [handleUnauthorized],
  );

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const response = await client.getCount({}, { headers });
        if (!cancelled) setCount(response.count);
      } catch (err: unknown) {
        if (!cancelled) handleError(err);
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [headers, handleError]);

  async function increment() {
    try {
      const response = await client.increment({}, { headers });
      setCount(response.count);
      setError(null);
    } catch (err: unknown) {
      handleError(err);
    }
  }

  async function decrement() {
    try {
      const response = await client.decrement({}, { headers });
      setCount(response.count);
      setError(null);
    } catch (err: unknown) {
      handleError(err);
    }
  }

  return (
    <>
      <section id="center">
        <div className="hero">
          <img src={heroImg} className="base" width="170" height="179" alt="" />
          <img src={reactLogo} className="framework" alt="React logo" />
          <img src={viteLogo} className="vite" alt="Vite logo" />
        </div>
        <div>
          <h1>{count ?? '…'}</h1>
          <div
            style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center' }}
          >
            <button className="counter" onClick={() => void decrement()}>
              −
            </button>
            <button className="counter" onClick={() => void increment()}>
              +
            </button>
          </div>
        </div>
        {error !== null && (
          <p className="service-error">Failed to reach core-service: {error}</p>
        )}
      </section>

      <div className="ticks"></div>

      <section id="next-steps">
        <div id="docs">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#documentation-icon"></use>
          </svg>
          <h2>Documentation</h2>
          <p>Your questions, answered</p>
          <ul>
            <li>
              <a href="https://vite.dev/" target="_blank">
                <img className="logo" src={viteLogo} alt="" />
                Explore Vite
              </a>
            </li>
            <li>
              <a href="https://react.dev/" target="_blank">
                <img className="button-icon" src={reactLogo} alt="" />
                Learn more
              </a>
            </li>
          </ul>
        </div>
        <div id="social">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#social-icon"></use>
          </svg>
          <h2>Connect with us</h2>
          <p>Join the Vite community</p>
          <ul>
            <li>
              <a href="https://github.com/vitejs/vite" target="_blank">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#github-icon"></use>
                </svg>
                GitHub
              </a>
            </li>
            <li>
              <a href="https://chat.vite.dev/" target="_blank">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#discord-icon"></use>
                </svg>
                Discord
              </a>
            </li>
            <li>
              <a href="https://x.com/vite_js" target="_blank">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#x-icon"></use>
                </svg>
                X.com
              </a>
            </li>
            <li>
              <a href="https://bsky.app/profile/vite.dev" target="_blank">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#bluesky-icon"></use>
                </svg>
                Bluesky
              </a>
            </li>
          </ul>
        </div>
      </section>

      <div className="ticks"></div>
      <section id="spacer"></section>
    </>
  );
}

function App() {
  const { state } = useAuth();

  if (state.status === 'loading') return null;
  if (state.status === 'unauthenticated') return <SignInWall />;
  return <AppContent token={state.token} />;
}

export default App;
