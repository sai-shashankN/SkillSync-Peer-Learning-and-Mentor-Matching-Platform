import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Button, Card, Input } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import type { RegisterPayload } from '../../services';
import { authService } from '../../services';
import { useAuthStore } from '../../store/authStore';
import AuthLayout from './AuthLayout';

interface RegisterFormState extends RegisterPayload {}

interface RegisterFormErrors {
  name?: string;
  email?: string;
  password?: string;
  termsAccepted?: string;
}

const PRIVACY_POLICY_VERSION = 'v1';
type EditableRegisterField = 'name' | 'email' | 'password';

function validate(values: RegisterFormState, t: (key: string) => string): RegisterFormErrors {
  const errors: RegisterFormErrors = {};

  if (!values.name.trim()) {
    errors.name = t('errors.required');
  }

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

  if (!values.termsAccepted) {
    errors.termsAccepted = 'You must accept the terms and privacy policy';
  }

  return errors;
}

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const loginSuccess = useAuthStore((state) => state.loginSuccess);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [values, setValues] = useState<RegisterFormState>({
    name: '',
    email: '',
    password: '',
    termsAccepted: false,
    privacyPolicyVersion: PRIVACY_POLICY_VERSION,
  });
  const [errors, setErrors] = useState<RegisterFormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const handleChange = (field: EditableRegisterField, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleTermsChange = (checked: boolean) => {
    setValues((current) => ({ ...current, termsAccepted: checked }));
    setErrors((current) => ({ ...current, termsAccepted: undefined }));
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
      const { data } = await authService.register(values);
      loginSuccess(data.data.accessToken, data.data.user);
      toast.success(t('auth.register_success'));
      navigate('/dashboard', { replace: true });
    } catch (error) {
      toast.error(getApiErrorMessage(error, t('common.error')));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title={t('auth.register_title')}
      subtitle={
        <p>
          {t('auth.has_account')}{' '}
          <Link
            className="font-semibold text-[var(--color-primary)] hover:text-[var(--color-primary-dark)]"
            to="/login"
          >
            {t('nav.login')}
          </Link>
        </p>
      }
    >
      <Card className="space-y-6 rounded-[2rem] border-slate-200/70 bg-white/80 p-6 dark:border-slate-800 dark:bg-slate-950/75">
        <form className="space-y-4" onSubmit={handleSubmit}>
          <Input
            id="name"
            label={t('auth.name')}
            placeholder="Aisha Khan"
            value={values.name}
            error={errors.name}
            onChange={(event) => handleChange('name', event.target.value)}
          />
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
          <label className="block space-y-2 rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-200">
            <span className="flex items-start gap-3">
              <input
                type="checkbox"
                checked={values.termsAccepted}
                onChange={(event) => handleTermsChange(event.target.checked)}
                className="mt-1 size-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
              />
              <span>I agree to the Terms and Privacy Policy.</span>
            </span>
            {errors.termsAccepted ? (
              <span className="block text-sm text-rose-500">{errors.termsAccepted}</span>
            ) : null}
          </label>
          <Button
            type="submit"
            className="w-full"
            size="lg"
            isLoading={isSubmitting}
          >
            {t('auth.register_btn')}
          </Button>
        </form>
      </Card>
    </AuthLayout>
  );
}
