import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { AuthContext, type AuthState, type AuthUser } from './AuthContext.tsx';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string;

if (!CLIENT_ID) {
  throw new Error('VITE_GOOGLE_CLIENT_ID is not set');
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface JwtPayload {
  sub: string;
  email: string;
  name: string;
  picture: string;
  hd?: string;
  exp: number;
}

function parseJwt(token: string): JwtPayload {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(base64)) as JwtPayload;
}

function loadGisScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (document.getElementById('gis-script')) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.id = 'gis-script';
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => {
      resolve();
    };
    script.onerror = () => {
      reject(new Error('Failed to load GIS script'));
    };
    document.head.appendChild(script);
  });
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const applyToken = useCallback((token: string) => {
    const payload = parseJwt(token);
    const user: AuthUser = {
      sub: payload.sub,
      email: payload.email,
      name: payload.name,
      picture: payload.picture,
    };
    setState({ status: 'authenticated', token, user });

    // Schedule silent refresh 60 s before expiry.
    const msUntilRefresh = payload.exp * 1000 - Date.now() - 60_000;
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    if (msUntilRefresh > 0) {
      refreshTimerRef.current = setTimeout(() => {
        google.accounts.id.prompt();
      }, msUntilRefresh);
    }
  }, []);

  const handleCredentialResponse = useCallback(
    (response: google.accounts.id.CredentialResponse) => {
      applyToken(response.credential);
    },
    [applyToken],
  );

  const signIn = useCallback(() => {
    google.accounts.id.prompt();
  }, []);

  const handleUnauthorized = useCallback(() => {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    setState({ status: 'unauthenticated' });
    google.accounts.id.prompt();
  }, []);

  useEffect(() => {
    let cancelled = false;

    loadGisScript()
      .then(() => {
        if (cancelled) return;

        google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: handleCredentialResponse,
          hd: import.meta.env.VITE_GOOGLE_HD as string | undefined,
          auto_select: true,
        });

        google.accounts.id.prompt((notification) => {
          if (cancelled) return;
          if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
            // If we had a cached token we're already showing content — stay
            // authenticated optimistically until the server rejects the token.
            // Only fall back to the sign-in wall if there was nothing cached.
            setState((prev) =>
              prev.status === 'loading' ? { status: 'unauthenticated' } : prev,
            );
          }
        });
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'unauthenticated' });
      });

    return () => {
      cancelled = true;
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    };
  }, [handleCredentialResponse]);

  return (
    <AuthContext value={{ state, handleUnauthorized, signIn }}>
      {children}
    </AuthContext>
  );
}
