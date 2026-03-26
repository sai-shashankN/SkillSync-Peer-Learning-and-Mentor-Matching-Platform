import { useState } from 'react';
import { CalendarClock, Clock3, IndianRupee, Star } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Button, Card, Input, RatingStars } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import { mentorService } from '../../services/mentorService';
import { paymentService } from '../../services/paymentService';
import { sessionService } from '../../services/sessionService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export default function MentorDashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [rejectingSessionId, setRejectingSessionId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const statsQuery = useQuery({
    queryKey: ['dashboard', 'mentor', 'stats'],
    queryFn: async () => {
      const [sessionsResponse, pendingResponse, profileResponse, earningsResponse] = await Promise.all([
        sessionService.getMySessions({ role: 'MENTOR', page: 0, size: 1 }),
        sessionService.getMySessions({ role: 'MENTOR', status: 'PENDING', page: 0, size: 1 }),
        mentorService.getMyProfile(),
        paymentService.getMyEarnings(),
      ]);

      return {
        totalSessions: sessionsResponse.data.totalElements,
        pendingCount: pendingResponse.data.totalElements,
        averageRating: profileResponse.data.data.rating,
        totalEarnings: earningsResponse.data.data.totalEarnings,
      };
    },
  });

  const pendingSessionsQuery = useQuery({
    queryKey: ['sessions', 'mentor', 'pending-dashboard'],
    queryFn: async () =>
      (
        await sessionService.getMySessions({ role: 'MENTOR', status: 'PENDING', page: 0, size: 10 })
      ).data.content,
  });

  const upcomingSessionsQuery = useQuery({
    queryKey: ['sessions', 'mentor', 'upcoming-dashboard'],
    queryFn: async () =>
      (
        await sessionService.getMySessions({ role: 'MENTOR', status: 'UPCOMING', page: 0, size: 3 })
      ).data.content,
  });

  const invalidateMentorQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['sessions', 'mentor'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard', 'mentor'] }),
    ]);
  };

  const acceptMutation = useMutation({
    mutationFn: (sessionId: number) => sessionService.acceptSession(sessionId),
    onSuccess: async () => {
      toast.success(t('mentor.accept_success'));
      await invalidateMentorQueries();
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ sessionId, reason }: { sessionId: number; reason: string }) =>
      sessionService.rejectSession(sessionId, reason),
    onSuccess: async () => {
      toast.success(t('mentor.reject_success'));
      setRejectReason('');
      setRejectingSessionId(null);
      await invalidateMentorQueries();
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const statItems = [
    {
      label: t('mentor.total_sessions'),
      value: statsQuery.data?.totalSessions ?? 0,
      icon: CalendarClock,
    },
    {
      label: t('mentor.pending_count'),
      value: statsQuery.data?.pendingCount ?? 0,
      icon: Clock3,
    },
    {
      label: t('mentor.avg_rating'),
      value: (
        <div className="flex items-center gap-3">
          <RatingStars value={statsQuery.data?.averageRating ?? 0} size={16} />
          <span>{(statsQuery.data?.averageRating ?? 0).toFixed(1)}</span>
        </div>
      ),
      icon: Star,
    },
    {
      label: t('mentor.total_earnings'),
      value: currencyFormatter.format(statsQuery.data?.totalEarnings ?? 0),
      icon: IndianRupee,
    },
  ];

  if (statsQuery.isLoading || pendingSessionsQuery.isLoading || upcomingSessionsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (statsQuery.isError || pendingSessionsQuery.isError || upcomingSessionsQuery.isError) {
    return (
      <Card className="text-red-500">
        {getApiErrorMessage(
          statsQuery.error ?? pendingSessionsQuery.error ?? upcomingSessionsQuery.error,
          t('common.error'),
        )}
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('mentor.dashboard_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentor.dashboard_subtitle')}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {statItems.map((item) => {
          const Icon = item.icon;

          return (
            <Card key={item.label} className="space-y-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium text-slate-500 dark:text-slate-400">{item.label}</p>
                <div className="rounded-2xl bg-blue-50 p-3 text-[var(--color-primary)] dark:bg-blue-950/40">
                  <Icon className="size-5" />
                </div>
              </div>
              <div className="text-3xl font-semibold text-slate-950 dark:text-white">{item.value}</div>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('mentor.pending_requests')}
          </h2>

          {pendingSessionsQuery.data?.length ? (
            <div className="space-y-4">
              {pendingSessionsQuery.data.map((session) => (
                <div
                  key={session.id}
                  className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="space-y-2">
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {format(new Date(session.startAt), 'dd MMM yyyy, hh:mm a')}
                      </p>
                      <h3 className="text-lg font-semibold text-slate-950 dark:text-white">
                        {session.topic}
                      </h3>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        #{session.learnerId}
                      </p>
                    </div>

                    <div className="flex flex-wrap gap-3">
                      <Button
                        size="sm"
                        isLoading={acceptMutation.isPending && acceptMutation.variables === session.id}
                        onClick={() => acceptMutation.mutate(session.id)}
                      >
                        {t('mentor.accept')}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setRejectingSessionId(session.id);
                          setRejectReason('');
                        }}
                      >
                        {t('mentor.reject')}
                      </Button>
                    </div>
                  </div>

                  {rejectingSessionId === session.id ? (
                    <div className="mt-4 space-y-3 rounded-3xl bg-slate-50 p-4 dark:bg-slate-950/60">
                      <Input
                        label={t('mentor.reject_reason')}
                        value={rejectReason}
                        onChange={(event) => setRejectReason(event.target.value)}
                        placeholder={t('mentor.reject_reason')}
                      />
                      <div className="flex flex-wrap gap-3">
                        <Button
                          variant="danger"
                          size="sm"
                          isLoading={rejectMutation.isPending}
                          onClick={() => {
                            if (!rejectReason.trim()) {
                              toast.error(t('errors.required'));
                              return;
                            }

                            rejectMutation.mutate({
                              sessionId: session.id,
                              reason: rejectReason.trim(),
                            });
                          }}
                        >
                          {t('mentor.reject')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setRejectingSessionId(null);
                            setRejectReason('');
                          }}
                        >
                          {t('common.cancel')}
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentor.no_pending')}</p>
          )}
        </Card>

        <div className="space-y-6">
          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {t('mentor.upcoming_sessions')}
            </h2>
            {upcomingSessionsQuery.data?.length ? (
              <div className="space-y-3">
                {upcomingSessionsQuery.data.map((session) => (
                  <div
                    key={session.id}
                    className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
                  >
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      {format(new Date(session.startAt), 'dd MMM yyyy, hh:mm a')}
                    </p>
                    <p className="mt-2 font-semibold text-slate-950 dark:text-white">{session.topic}</p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentor.no_upcoming')}</p>
            )}
          </Card>

          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {t('dashboard.quick_actions')}
            </h2>
            <div className="grid gap-3">
              <Button onClick={() => navigate('/availability')}>{t('nav.availability')}</Button>
              <Button variant="outline" onClick={() => navigate('/earnings')}>
                {t('nav.earnings')}
              </Button>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
