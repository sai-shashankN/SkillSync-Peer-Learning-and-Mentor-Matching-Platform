import { useEffect, useRef, useState } from 'react';
import { Chrome, Github } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Button, Card, Input } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import type { LoginPayload } from '../../services';
import { authService } from '../../services';
import { useAuthStore } from '../../store/authStore';
import AuthLayout from './AuthLayout';

interface LoginFormState extends LoginPayload {}

interface LoginFormErrors {
  email?: string;
  password?: string;
}

interface RedirectState {
  from?: {
    pathname?: string;
  };
}

let googleIdentityScriptPromise: Promise<void> | null = null;

function loadGoogleIdentityScript() {
  if (window.google?.accounts?.id) {
    return Promise.resolve();
  }

  googleIdentityScriptPromise ??= new Promise<void>((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>(
      'script[data-google-identity-services="true"]',
    );

    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(), { once: true });
      existingScript.addEventListener(
        'error',
        () => reject(new Error('Unable to load Google sign-in.')),
        { once: true },
      );
      return;
    }

    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.dataset.googleIdentityServices = 'true';
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Unable to load Google sign-in.'));
    document.body.appendChild(script);
  });

  return googleIdentityScriptPromise;
}

function validate(values: LoginFormState, t: (key: string) => string): LoginFormErrors {
  const errors: LoginFormErrors = {};

  if (!values.email.trim()) {
    errors.email = t('errors.required');
  } else if (!/\S+@\S+\.\S+/.test(values.email)) {
    errors.email = t('errors.invalid_email');
  }

  if (!values.password.trim()) {
    errors.password = t('errors.required');
  } else if (values.password.length < 8) {
    errors.password = t('errors.min_password');
  }

  return errors;
}

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const loginSuccess = useAuthStore((state) => state.loginSuccess);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [values, setValues] = useState<LoginFormState>({ email: '', password: '' });
  const [errors, setErrors] = useState<LoginFormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isGoogleReady, setIsGoogleReady] = useState(false);
  const [isGoogleLoading, setIsGoogleLoading] = useState(false);
  const [googleInitError, setGoogleInitError] = useState<string | null>(null);
  const googleButtonRef = useRef<HTMLDivElement | null>(null);
  const redirectPath = (location.state as RedirectState | null)?.from?.pathname ?? '/dashboard';
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? '';
  const githubClientId = import.meta.env.VITE_GITHUB_CLIENT_ID?.trim() ?? '';
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const completeLogin = (accessToken: string, user: Parameters<typeof loginSuccess>[1]) => {
    loginSuccess(accessToken, user);
    toast.success(t('auth.submit_success'));
    navigate(redirectPath, { replace: true });
  };

  useEffect(() => {
    let isActive = true;

    const initializeGoogleSignIn = async () => {
      if (!googleClientId) {
        setGoogleInitError('Google sign-in is not configured.');
        setIsGoogleReady(false);
        return;
      }

      setIsGoogleLoading(true);
      setGoogleInitError(null);

      try {
        await loadGoogleIdentityScript();

        if (!isActive) {
          return;
        }

        const google = window.google?.accounts?.id;
        const buttonContainer = googleButtonRef.current;

        if (!google || !buttonContainer) {
          throw new Error('Google sign-in is unavailable.');
        }

        google.initialize({
          client_id: googleClientId,
          callback: async (response: GoogleCredentialResponse) => {
            if (!response.credential) {
              toast.error('Google did not return a valid sign-in token.');
              return;
            }

            setIsSubmitting(true);

            try {
              const { data } = await authService.googleLogin(response.credential);
              completeLogin(data.data.accessToken, data.data.user);
            } catch (error) {
              toast.error(getApiErrorMessage(error, 'Unable to sign in with Google.'));
            } finally {
              setIsSubmitting(false);
            }
          },
        });

        buttonContainer.replaceChildren();
        google.renderButton(buttonContainer, {
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'pill',
          width: buttonContainer.offsetWidth || 240,
        });

        setIsGoogleReady(true);
      } catch (error) {
        const message =
          error instanceof Error ? error.message : 'Unable to initialize Google sign-in.';

        if (!isActive) {
          return;
        }

        setGoogleInitError(message);
        setIsGoogleReady(false);
        toast.error(message);
      } finally {
        if (isActive) {
          setIsGoogleLoading(false);
        }
      }
    };

    void initializeGoogleSignIn();

    return () => {
      isActive = false;
    };
  }, [googleClientId]);

  const handleChange = (field: keyof LoginFormState, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors = validate(values, t);

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    setIsSubmitting(true);

    try {
      const { data } = await authService.login(values);
      completeLogin(data.data.accessToken, data.data.user);
    } catch (error) {
      toast.error(getApiErrorMessage(error, t('common.error')));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGoogleUnavailable = () => {
    toast.error(googleInitError ?? 'Unable to initialize Google sign-in.');
  };

  const handleGithubRedirect = () => {
    if (!githubClientId) {
      toast.error('GitHub sign-in is not configured.');
      return;
    }
    toast(t('auth.oauth_redirect', { provider: t('auth.github') }));
    const redirectUri = encodeURIComponent(`${window.location.origin}/auth/github/callback`);
    window.location.href = `https://github.com/login/oauth/authorize?client_id=${githubClientId}&redirect_uri=${redirectUri}&scope=read:user user:email`;
  };

  return (
    <AuthLayout
      title={t('auth.login_title')}
      subtitle={
        <p>
          {t('auth.no_account')}{' '}
          <Link
            className="font-semibold text-[var(--color-primary)] hover:text-[var(--color-primary-dark)]"
            to="/register"
          >
            {t('nav.register')}
          </Link>
        </p>
      }
    >
      <Card className="space-y-6 rounded-[2rem] border-slate-200/70 bg-white/80 p-6 dark:border-slate-800 dark:bg-slate-950/75">
        <form className="space-y-4" onSubmit={handleSubmit}>
          <Input
            id="email"
            type="email"
            label={t('auth.email')}
            placeholder="name@example.com"
            value={values.email}
            error={errors.email}
            onChange={(event) => handleChange('email', event.target.value)}
          />
          <Input
            id="password"
            type="password"
            label={t('auth.password')}
            placeholder="••••••••"
            value={values.password}
            error={errors.password}
            onChange={(event) => handleChange('password', event.target.value)}
          />
          <Button
            type="submit"
            className="w-full"
            size="lg"
            isLoading={isSubmitting}
          >
            {t('auth.login_btn')}
          </Button>
        </form>

        <div className="flex items-center gap-4 text-xs uppercase tracking-[0.2em] text-slate-400">
          <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
          {t('auth.or_continue_with')}
          <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="relative min-h-12">
            <div
              className={`absolute inset-0 flex items-center justify-center rounded-[var(--radius-pill)] border border-slate-300 bg-white/80 px-3 transition-opacity dark:border-slate-700 dark:bg-slate-900/70 ${
                isGoogleReady ? 'opacity-100' : 'pointer-events-none opacity-0'
              }`}
            >
              <div className="w-full" ref={googleButtonRef} />
            </div>

            {!isGoogleReady ? (
              <Button
                type="button"
                variant="outline"
                size="lg"
                className="w-full"
                isLoading={isGoogleLoading}
                onClick={handleGoogleUnavailable}
              >
                <Chrome className="size-4" />
                {t('auth.google')}
              </Button>
            ) : null}
          </div>
          <Button
            type="button"
            className="w-full bg-[#24292e] text-white hover:bg-[#1b1f23] border border-[#24292e] dark:bg-white dark:text-[#24292e] dark:hover:bg-gray-100 hover:text-white dark:hover:text-[#24292e] transition duration-200"
            size="lg"
            onClick={handleGithubRedirect}
          >
            <Github className="size-5" />
            {t('auth.github')}
          </Button>
        </div>
      </Card>
    </AuthLayout>
  );
}
