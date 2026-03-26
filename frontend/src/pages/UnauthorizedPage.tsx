import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button, Card } from '../components/ui';

export default function UnauthorizedPage() {
  const { t } = useTranslation();

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <Card className="w-full max-w-lg space-y-5 text-center">
        <p className="text-6xl font-semibold text-[var(--color-warning)]">403</p>
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('errors.unauthorized')}
        </h1>
        <p className="text-slate-500 dark:text-slate-400">{t('errors.unauthorized')}</p>
        <Link to="/">
          <Button size="lg">{t('common.go_home')}</Button>
        </Link>
      </Card>
    </div>
  );
}
