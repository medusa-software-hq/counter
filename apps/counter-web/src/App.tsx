import { useEffect, useState } from 'react';
import { createClient } from '@connectrpc/connect';
import { createGrpcWebTransport } from '@connectrpc/connect-web';
import { CounterService } from './gen/medusa/counter/v1/counter_service_pb.ts';
import reactLogo from './assets/react.svg';
import viteLogo from './assets/vite.svg';
import heroImg from './assets/hero.png';
import './App.css';

const transport = createGrpcWebTransport({
  // In production, nginx injects the counter-service URL via sub_filter (see infra/).
  // In dev, the Vite proxy (vite.config.ts) forwards gRPC-Web requests to localhost:8080.
  baseUrl:
    document.querySelector<HTMLMetaElement>('meta[name="counter-service-url"]')
      ?.content ?? window.location.origin,
});

const client = createClient(CounterService, transport);

function App() {
  const [count, setCount] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [isIncrementing, setIsIncrementing] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const response = await client.getCounter({});
        if (!cancelled) setCount(response.count);
      } catch (err) {
        if (!cancelled) setError('Could not load counter.');
        console.error('getCounter failed:', err);
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  async function increment() {
    setIsIncrementing(true);
    setError('');
    try {
      const response = await client.incrementCounter({});
      setCount(response.count);
    } catch (err) {
      setError('Could not increment counter.');
      console.error('incrementCounter failed:', err);
    } finally {
      setIsIncrementing(false);
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
          <h1>Counter</h1>
        </div>
        <div className="counter-panel">
          <p className="counter">
            {count === null ? 'Loading…' : `Count is ${count.toString()}`}
          </p>
          <button
            className="counter"
            onClick={() => void increment()}
            disabled={isIncrementing}
          >
            {isIncrementing ? 'Incrementing…' : 'Increment'}
          </button>
          {error ? <p className="counter-error">{error}</p> : null}
        </div>
      </section>

      <div className="ticks"></div>

      <section id="next-steps">
        <div id="docs">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#documentation-icon"></use>
          </svg>
          <h2>Template notes</h2>
          <p>Useful places to change first</p>
          <ul>
            <li>
              <a href="https://vite.dev/" target="_blank" rel="noreferrer">
                <img className="button-icon" src={reactLogo} alt="" />
                Explore Vite
              </a>
            </li>
          </ul>
        </div>
        <div id="social">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#social-icon"></use>
          </svg>
          <h2>Next steps</h2>
          <p>Template values to replace after fork</p>
          <ul>
            <li>
              <a href="/health">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#discord-icon"></use>
                </svg>
                Check health
              </a>
            </li>
            <li>
              <a
                href="https://cloud.google.com/run"
                target="_blank"
                rel="noreferrer"
              >
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#x-icon"></use>
                </svg>
                Cloud Run docs
              </a>
            </li>
            <li>
              <a
                href="https://developer.hashicorp.com/terraform"
                target="_blank"
                rel="noreferrer"
              >
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#bluesky-icon"></use>
                </svg>
                Terraform docs
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

export default App;
