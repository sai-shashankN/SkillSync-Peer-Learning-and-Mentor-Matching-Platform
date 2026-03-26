import { useEffect, useState } from 'react';
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
  const redirectPath = (location.state as RedirectState | null)?.from?.pathname ?? '/dashboard';

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

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
      loginSuccess(data.data.accessToken, data.data.user);
      toast.success(t('auth.submit_success'));
      navigate(redirectPath, { replace: true });
    } catch (error) {
      toast.error(getApiErrorMessage(error, t('common.error')));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOAuthRedirect = (provider: 'google' | 'github') => {
    toast(t('auth.oauth_redirect', { provider: t(`auth.${provider}`) }));
    window.location.href = `/api/auth/oauth2/authorization/${provider}`;
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
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={() => handleOAuthRedirect('google')}
          >
            <Chrome className="size-4" />
            {t('auth.google')}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={() => handleOAuthRedirect('github')}
          >
            <Github className="size-4" />
            {t('auth.github')}
          </Button>
        </div>
      </Card>
    </AuthLayout>
  );
}
