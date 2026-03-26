import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Button, Card, Input, Modal } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { sessionService } from '../../services/sessionService';
import SessionStatusBadge from '../sessions/SessionStatusBadge';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

const statusOptions = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'] as const;

export default function MentorSessionsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { page, size, nextPage, prevPage, setPage } = usePagination(0, 8);
  const [status, setStatus] = useState<(typeof statusOptions)[number]>('ALL');
  const [rejectingSessionId, setRejectingSessionId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  useEffect(() => {
    setPage(0);
  }, [setPage, status]);

  const sessionsQuery = useQuery({
    queryKey: ['sessions', 'mentor', 'list', { status, page, size }],
    queryFn: async () =>
      (
        await sessionService.getMySessions({
          role: 'MENTOR',
          status: status === 'ALL' ? undefined : status,
          page,
          size,
        })
      ).data,
  });

  const refreshSessions = async () => {
    await queryClient.invalidateQueries({ queryKey: ['sessions', 'mentor'] });
  };

  const acceptMutation = useMutation({
    mutationFn: (sessionId: number) => sessionService.acceptSession(sessionId),
    onSuccess: async () => {
      toast.success(t('mentor.accept_success'));
      await refreshSessions();
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
      await refreshSessions();
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const completeMutation = useMutation({
    mutationFn: (sessionId: number) => sessionService.complete(sessionId),
    onSuccess: async () => {
      toast.success(t('mentor.complete_success'));
      await refreshSessions();
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (sessionsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (sessionsQuery.isError) {
    return <Card className="text-red-500">{getApiErrorMessage(sessionsQuery.error, t('common.error'))}</Card>;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('mentor.sessions_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {t('mentor.dashboard_subtitle')}
        </p>
      </div>

      <Card className="space-y-4">
        <div className="flex flex-wrap gap-3">
          {statusOptions.map((option) => (
            <Button
              key={option}
              variant={status === option ? 'primary' : 'outline'}
              onClick={() => setStatus(option)}
            >
              {t(`mentor.filter_${option.toLowerCase()}`)}
            </Button>
          ))}
        </div>
      </Card>

      {sessionsQuery.data?.content.length ? (
        <div className="space-y-4">
          {sessionsQuery.data.content.map((session) => (
            <Card key={session.id} className="space-y-4">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div className="space-y-2">
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    {format(new Date(session.startAt), 'dd MMM yyyy, hh:mm a')}
                  </p>
                  <h2 className="text-lg font-semibold text-slate-950 dark:text-white">{session.topic}</h2>
                  <p className="text-sm text-slate-600 dark:text-slate-300">#{session.learnerId}</p>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                  <SessionStatusBadge status={session.status} />
                  <span className="font-medium text-slate-700 dark:text-slate-200">
                    {currencyFormatter.format(session.amount)}
                  </span>
                </div>
              </div>

              <div className="flex flex-wrap gap-3">
                {session.status === 'PENDING' ? (
                  <>
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
                  </>
                ) : null}

                {session.status === 'CONFIRMED' ? (
                  <Button
                    variant="outline"
                    size="sm"
                    isLoading={completeMutation.isPending && completeMutation.variables === session.id}
                    onClick={() => completeMutation.mutate(session.id)}
                  >
                    {t('mentor.mark_complete')}
                  </Button>
                ) : null}
              </div>
            </Card>
          ))}

          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={prevPage} disabled={page === 0}>
              {t('common.previous')}
            </Button>
            <Button variant="outline" onClick={nextPage} disabled={Boolean(sessionsQuery.data.last)}>
              {t('common.next')}
            </Button>
          </div>
        </div>
      ) : (
        <Card>{t('sessions.no_sessions')}</Card>
      )}

      <Modal
        isOpen={rejectingSessionId !== null}
        title={t('mentor.reject')}
        onClose={() => {
          setRejectingSessionId(null);
          setRejectReason('');
        }}
      >
        <div className="space-y-4">
          <Input
            label={t('mentor.reject_reason')}
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
            placeholder={t('mentor.reject_reason')}
          />
          <div className="flex flex-wrap justify-end gap-3">
            <Button variant="ghost" onClick={() => setRejectingSessionId(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              isLoading={rejectMutation.isPending}
              onClick={() => {
                if (!rejectReason.trim() || rejectingSessionId === null) {
                  toast.error(t('errors.required'));
                  return;
                }

                rejectMutation.mutate({
                  sessionId: rejectingSessionId,
                  reason: rejectReason.trim(),
                });
              }}
            >
              {t('mentor.reject')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
