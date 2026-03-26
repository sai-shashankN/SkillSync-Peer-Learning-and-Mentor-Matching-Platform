import { useTranslation } from 'react-i18next';
import { Card } from '../components/ui';
import { useAuthStore } from '../store/authStore';

const statItems = [
  { labelKey: 'dashboard.total_sessions' },
  { labelKey: 'dashboard.active_groups' },
  { labelKey: 'dashboard.badges_earned' },
];

export default function DashboardPage() {
  const { t } = useTranslation();
  const user = useAuthStore((state) => state.user);

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('dashboard.welcome', { name: user?.name ?? 'Learner' })}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{user?.email}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {statItems.map((item) => (
          <Card key={item.labelKey} className="space-y-3">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">
              {t(item.labelKey)}
            </p>
            <p className="text-4xl font-semibold text-slate-950 dark:text-white">0</p>
          </Card>
        ))}
      </div>

      <Card>
        <p className="text-slate-600 dark:text-slate-300">{t('common.coming_soon')}</p>
      </Card>
    </div>
  );
}
