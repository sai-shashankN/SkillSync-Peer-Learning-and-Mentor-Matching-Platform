import { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { LoaderCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { authService } from '../../services';
import { useAuthStore } from '../../store/authStore';
import { getApiErrorMessage } from '../../lib/utils';
import { useTranslation } from 'react-i18next';

export default function GithubCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const loginSuccess = useAuthStore((state) => state.loginSuccess);
  const code = searchParams.get('code');
  const hasAttempted = useRef(false);

  useEffect(() => {
    if (!code) {
      toast.error('No authorization code provided by GitHub.');
      navigate('/login', { replace: true });
      return;
    }

    if (hasAttempted.current) return;
    hasAttempted.current = true;

    const authenticateGithub = async () => {
      try {
        const { data } = await authService.githubLogin(code);
        loginSuccess(data.data.accessToken, data.data.user);
        toast.success(t('auth.submit_success'));
        navigate('/dashboard', { replace: true });
      } catch (error) {
        toast.error(getApiErrorMessage(error, 'Unable to sign in with GitHub.'));
        navigate('/login', { replace: true });
      }
    };

    void authenticateGithub();
  }, [code, navigate, t, loginSuccess]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-gray-950">
      <div className="flex flex-col items-center justify-center gap-4 text-slate-600 dark:text-slate-300">
        <LoaderCircle className="size-10 animate-spin text-[var(--color-primary)]" />
        <h2 className="text-xl font-semibold">Completing GitHub sign-in...</h2>
        <p className="text-sm">Please wait while we authenticate your account.</p>
      </div>
    </div>
  );
}
