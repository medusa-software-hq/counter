import type { ReactNode } from 'react';
import { AuthContext, type AuthUser } from './AuthContext.tsx';

// Google Sign-In was removed for the AWS port (#121); Cognito login lands in a
// later issue. Until then this is a dev pass-through: the app is always
// authenticated with a placeholder identity. The backend uses no-op auth, so an
// optional bearer token is read from VITE_DEV_TOKEN, defaulting to empty.
const DEV_TOKEN = (import.meta.env.VITE_DEV_TOKEN as string | undefined) ?? '';

const devUser: AuthUser = {
  sub: 'dev',
  email: 'dev@localhost',
  name: 'Dev',
  picture: '',
};

export function AuthProvider({ children }: { children: ReactNode }) {
  return (
    <AuthContext
      value={{
        state: { status: 'authenticated', token: DEV_TOKEN, user: devUser },
        handleUnauthorized: () => undefined,
        signIn: () => undefined,
      }}
    >
      {children}
    </AuthContext>
  );
}
