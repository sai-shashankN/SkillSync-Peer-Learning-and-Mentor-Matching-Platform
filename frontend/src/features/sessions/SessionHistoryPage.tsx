import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { Button, Card } from '../../components/ui';
import { usePagination } from '../../hooks';
import { mentorService } from '../../services/mentorService';
import { sessionService } from '../../services/sessionService';
import SessionStatusBadge from './SessionStatusBadge';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

const statusOptions = ['ALL', 'UPCOMING', 'COMPLETED', 'CANCELLED'];

export default function SessionHistoryPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { page, size, nextPage, prevPage, setPage } = usePagination(0, 8);
  const [status, setStatus] = useState('ALL');

  const sessionsQuery = useQuery({
    queryKey: ['sessions', 'history', { status, page, size }],
    queryFn: async () =>
      (
        await sessionService.getMySessions({
          role: 'LEARNER',
          status: status === 'ALL' ? undefined : status,
          page,
          size,
        })
      ).data,
  });

  const mentorIds = [...new Set(sessionsQuery.data?.content.map((session) => session.mentorId) ?? [])];

  const mentorNamesQuery = useQuery({
    queryKey: ['mentors', 'names', mentorIds],
    enabled: mentorIds.length > 0,
    queryFn: async () => {
      const entries = await Promise.all(
        mentorIds.map(async (mentorId) => {
          const response = await mentorService.getById(mentorId);
          return [mentorId, response.data.data.name] as const;
        }),
      );

      return Object.fromEntries(entries);
    },
  });

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('sessions.history_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {t('sessions.history_subtitle')}
        </p>
      </div>

      <Card className="space-y-4">
        <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
          {t('sessions.status_filter')}
        </p>
        <div className="flex flex-wrap gap-3">
          {statusOptions.map((option) => (
            <Button
              key={option}
              variant={status === option ? 'primary' : 'outline'}
              onClick={() => {
                setStatus(option);
                setPage(0);
              }}
            >
              {t(`sessions.status_values.${option.toLowerCase()}`)}
            </Button>
          ))}
        </div>
      </Card>

      {sessionsQuery.isLoading ? (
        <Card>{t('common.loading')}</Card>
      ) : sessionsQuery.data?.content.length ? (
        <div className="space-y-4">
          {sessionsQuery.data.content.map((session) => (
            <Card
              key={session.id}
              className="cursor-pointer transition hover:border-blue-200 dark:hover:border-slate-700"
              onClick={() => navigate(`/sessions/${session.id}`)}
            >
              <div className="grid gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] lg:items-center">
                <div className="space-y-2">
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    {format(new Date(session.startAt), 'dd MMM yyyy, hh:mm a')}
                  </p>
                  <h2 className="text-lg font-semibold text-slate-950 dark:text-white">
                    {session.topic}
                  </h2>
                  <p className="text-sm text-slate-600 dark:text-slate-300">
                    {t('sessions.mentor')}: {mentorNamesQuery.data?.[session.mentorId] ?? `#${session.mentorId}`}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600 dark:text-slate-300">
                  <SessionStatusBadge status={session.status} />
                  <span>{currencyFormatter.format(session.amount)}</span>
                </div>

                <div className="flex justify-start lg:justify-end">
                  <Button variant="outline">{t('sessions.view_details')}</Button>
                </div>
              </div>
            </Card>
          ))}

          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={prevPage} disabled={page === 0}>
              {t('common.previous')}
            </Button>
            <Button
              variant="outline"
              onClick={nextPage}
              disabled={Boolean(sessionsQuery.data.last)}
            >
              {t('common.next')}
            </Button>
          </div>
        </div>
      ) : (
        <Card>{t('sessions.no_sessions')}</Card>
      )}
    </div>
  );
}
