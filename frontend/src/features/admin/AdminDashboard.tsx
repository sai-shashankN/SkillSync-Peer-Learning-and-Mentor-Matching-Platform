import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { format, subDays, subWeeks } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button, Card } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import { auditService } from '../../services/auditService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export default function AdminDashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const sessionsFrom = useMemo(() => format(subDays(new Date(), 30), 'yyyy-MM-dd'), []);
  const revenueFrom = useMemo(() => format(subWeeks(new Date(), 12), 'yyyy-MM-dd'), []);

  const kpisQuery = useQuery({
    queryKey: ['admin', 'kpis'],
    queryFn: async () => (await auditService.getOverviewKpis()).data.data,
  });

  const sessionsSeriesQuery = useQuery({
    queryKey: ['admin', 'analytics', 'sessions-dashboard'],
    queryFn: async () =>
      (
        await auditService.getSessionTimeSeries({
          from: sessionsFrom,
          groupBy: 'day',
        })
      ).data.data,
  });

  const revenueSeriesQuery = useQuery({
    queryKey: ['admin', 'analytics', 'revenue-dashboard'],
    queryFn: async () =>
      (
        await auditService.getRevenueTimeSeries({
          from: revenueFrom,
          groupBy: 'week',
        })
      ).data.data,
  });

  const topSkillsQuery = useQuery({
    queryKey: ['admin', 'analytics', 'top-skills-dashboard'],
    queryFn: async () => (await auditService.getTopSkills({ limit: 5 })).data.data,
  });

  if (kpisQuery.isLoading || sessionsSeriesQuery.isLoading || revenueSeriesQuery.isLoading || topSkillsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (kpisQuery.isError || sessionsSeriesQuery.isError || revenueSeriesQuery.isError || topSkillsQuery.isError || !kpisQuery.data) {
    return (
      <Card className="text-red-500">
        {getApiErrorMessage(
          kpisQuery.error ?? sessionsSeriesQuery.error ?? revenueSeriesQuery.error ?? topSkillsQuery.error,
          t('common.error'),
        )}
      </Card>
    );
  }

  const statItems = [
    { label: t('admin.total_users'), value: kpisQuery.data.totalUsers },
    { label: t('admin.total_mentors'), value: kpisQuery.data.totalMentors },
    { label: t('admin.total_sessions'), value: kpisQuery.data.totalSessions },
    { label: t('admin.total_revenue'), value: currencyFormatter.format(kpisQuery.data.totalRevenue) },
    { label: t('admin.active_sessions'), value: kpisQuery.data.activeSessions },
    { label: t('admin.pending_approvals'), value: kpisQuery.data.pendingMentorApprovals },
  ];

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('admin.dashboard_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('admin.dashboard_subtitle')}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {statItems.map((item) => (
          <Card key={item.label} className="space-y-3">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">{item.label}</p>
            <p className="text-3xl font-semibold text-slate-950 dark:text-white">{item.value}</p>
          </Card>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('admin.sessions_chart')}
          </h2>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={sessionsSeriesQuery.data}>
                <defs>
                  <linearGradient id="adminSessionsGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563eb" stopOpacity={0.35} />
                    <stop offset="95%" stopColor="#2563eb" stopOpacity={0.05} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
                <XAxis dataKey="date" />
                <YAxis />
                <Tooltip />
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke="#2563eb"
                  fill="url(#adminSessionsGradient)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('admin.revenue_chart')}
          </h2>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={revenueSeriesQuery.data}>
                <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
                <XAxis dataKey="date" />
                <YAxis tickFormatter={(value) => currencyFormatter.format(Number(value))} />
                <Tooltip
                  formatter={(value) =>
                    currencyFormatter.format(Number(Array.isArray(value) ? value[0] : value ?? 0))
                  }
                />
                <Bar dataKey="value" radius={[12, 12, 0, 0]} fill="#0f172a" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(300px,0.8fr)]">
        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('admin.top_skills_chart')}
          </h2>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={topSkillsQuery.data} layout="vertical" margin={{ left: 24 }}>
                <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
                <XAxis type="number" />
                <YAxis type="category" dataKey="skillName" width={120} />
                <Tooltip />
                <Bar dataKey="sessionCount" radius={[0, 12, 12, 0]} fill="#2563eb" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('dashboard.quick_actions')}
          </h2>
          <div className="grid gap-3">
            <Button onClick={() => navigate('/admin/pending-approvals')}>{t('admin.pending_approvals')}</Button>
            <Button variant="outline" onClick={() => navigate('/users')}>
              {t('admin.user_management')}
            </Button>
            <Button variant="outline" onClick={() => navigate('/audit-logs')}>
              {t('admin.audit_logs')}
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}
