import { useEffect, useRef } from 'react';
import { useAuth } from './useAuth.tsx';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string;

export function SignInWall() {
  const { signIn } = useAuth();
  const buttonRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (buttonRef.current) {
      google.accounts.id.renderButton(buttonRef.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text: 'signin_with',
        logo_alignment: 'left',
      });
    }
  }, []);

  // The rendered button handles the click itself; signIn() is a fallback.
  void signIn;
  void CLIENT_ID;

  return (
    <div className="sign-in-wall">
      <h1>Sign in</h1>
      <p>Use your company account to continue.</p>
      <div ref={buttonRef} className="sign-in-button" />
    </div>
  );
}
