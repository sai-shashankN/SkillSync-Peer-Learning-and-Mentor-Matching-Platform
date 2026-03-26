import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { format, subDays } from 'date-fns';
import { useTranslation } from 'react-i18next';
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
import { Card, Input } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import { auditService } from '../../services/auditService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

const groupByOptions = ['day', 'week', 'month'] as const;

export default function AnalyticsPage() {
  const { t } = useTranslation();
  const defaultFrom = useMemo(() => format(subDays(new Date(), 90), 'yyyy-MM-dd'), []);
  const defaultTo = useMemo(() => format(new Date(), 'yyyy-MM-dd'), []);
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(defaultTo);
  const [groupBy, setGroupBy] = useState<(typeof groupByOptions)[number]>('week');

  const sessionsQuery = useQuery({
    queryKey: ['admin', 'analytics', 'sessions', { from, to, groupBy }],
    queryFn: async () =>
      (
        await auditService.getSessionTimeSeries({
          from,
          to,
          groupBy,
        })
      ).data.data,
  });

  const revenueQuery = useQuery({
    queryKey: ['admin', 'analytics', 'revenue', { from, to, groupBy }],
    queryFn: async () =>
      (
        await auditService.getRevenueTimeSeries({
          from,
          to,
          groupBy,
        })
      ).data.data,
  });

  const topSkillsQuery = useQuery({
    queryKey: ['admin', 'analytics', 'top-skills', { from, to }],
    queryFn: async () => (await auditService.getTopSkills({ from, to, limit: 10 })).data.data,
  });

  if (sessionsQuery.isLoading || revenueQuery.isLoading || topSkillsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (sessionsQuery.isError || revenueQuery.isError || topSkillsQuery.isError) {
    return (
      <Card className="text-red-500">
        {getApiErrorMessage(
          sessionsQuery.error ?? revenueQuery.error ?? topSkillsQuery.error,
          t('common.error'),
        )}
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('admin.analytics_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('admin.dashboard_subtitle')}</p>
      </div>

      <Card className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input type="date" label={t('admin.from')} value={from} onChange={(event) => setFrom(event.target.value)} />
          <Input type="date" label={t('admin.to')} value={to} onChange={(event) => setTo(event.target.value)} />
          <label className="block space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {t('admin.group_by')}
            </span>
            <select
              value={groupBy}
              onChange={(event) => setGroupBy(event.target.value as (typeof groupByOptions)[number])}
              className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
            >
              <option value="day">{t('admin.by_day')}</option>
              <option value="week">{t('admin.by_week')}</option>
              <option value="month">{t('admin.by_month')}</option>
            </select>
          </label>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('admin.sessions_chart')}
          </h2>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={sessionsQuery.data}>
                <defs>
                  <linearGradient id="analyticsSessionsGradient" x1="0" y1="0" x2="0" y2="1">
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
                  fill="url(#analyticsSessionsGradient)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('admin.revenue_chart')}
          </h2>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={revenueQuery.data}>
                <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
                <XAxis dataKey="date" />
                <YAxis tickFormatter={(value) => currencyFormatter.format(Number(value))} />
                <Tooltip
                  formatter={(value) =>
                    currencyFormatter.format(Number(Array.isArray(value) ? value[0] : value ?? 0))
                  }
                />
                <Bar dataKey="value" fill="#0f172a" radius={[12, 12, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="space-y-4 xl:col-span-2">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('admin.top_skills_chart')}
          </h2>
          <div className="h-96">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={topSkillsQuery.data} layout="vertical" margin={{ left: 32 }}>
                <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
                <XAxis type="number" />
                <YAxis type="category" dataKey="skillName" width={140} />
                <Tooltip />
                <Bar dataKey="sessionCount" fill="#2563eb" radius={[0, 12, 12, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>
    </div>
  );
}
