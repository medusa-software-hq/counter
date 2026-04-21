import { useEffect, useState } from 'react';
import { createClient } from '@connectrpc/connect';
import { createGrpcWebTransport } from '@connectrpc/connect-web';
import { CounterService } from './gen/medusa/counter/v1/counter_service_pb.ts';
import reactLogo from './assets/react.svg';
import viteLogo from './assets/vite.svg';
import heroImg from './assets/hero.png';
import './App.css';
import { useAuth } from './useAuth.tsx';
import { SignInWall } from './SignInWall.tsx';

const COUNTER_SERVICE_URL = import.meta.env.VITE_COUNTER_SERVICE_URL as string;

if (!COUNTER_SERVICE_URL) {
  throw new Error('VITE_COUNTER_SERVICE_URL is not set');
}

const transport = createGrpcWebTransport({
  baseUrl: COUNTER_SERVICE_URL,
});

const client = createClient(CounterService, transport);

function AppContent({ token }: { token: string }) {
  const { handleUnauthorized } = useAuth();
  const [message, setMessage] = useState<string | null>(null);
  const [messageError, setMessageError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const response = await client.sayHello(
          {},
          { headers: { Authorization: `Bearer ${token}` } },
        );
        if (!cancelled) setMessage(response.message);
      } catch (err: unknown) {
        if (!cancelled) {
          const message = err instanceof Error ? err.message : String(err);
          if (message.includes('401') || message.includes('unauthenticated')) {
            handleUnauthorized();
          } else {
            setMessageError(message);
          }
        }
        console.error('sayHello failed:', err);
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [token, handleUnauthorized]);

  return (
    <>
      <section id="center">
        <div className="hero">
          <img src={heroImg} className="base" width="170" height="179" alt="" />
          <img src={reactLogo} className="framework" alt="React logo" />
          <img src={viteLogo} className="vite" alt="Vite logo" />
        </div>
        <div>
          <h1>Get started</h1>
          <p>
            Edit <code>src/App.tsx</code> and save to test <code>HMR</code>
          </p>
        </div>
        {message !== null && <p className="service-message">{message}</p>}
        {messageError !== null && (
          <p className="service-error">
            Failed to reach counter-service: {messageError}
          </p>
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
