import { useState } from 'react';
import { ExternalLink, Video } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { useParams } from 'react-router-dom';
import { Button, Card, RatingStars } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import { mentorService } from '../../services/mentorService';
import { sessionService } from '../../services/sessionService';
import ReviewForm from '../reviews/ReviewForm';
import SessionStatusBadge from './SessionStatusBadge';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export default function SessionDetailPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { id } = useParams();
  const sessionId = Number(id);
  const [cancelReason, setCancelReason] = useState('');
  const [showCancelForm, setShowCancelForm] = useState(false);

  const sessionQuery = useQuery({
    queryKey: ['session', sessionId],
    enabled: Number.isFinite(sessionId),
    queryFn: async () => (await sessionService.getById(sessionId)).data.data,
  });

  const mentorQuery = useQuery({
    queryKey: ['mentor', sessionQuery.data?.mentorId],
    enabled: Boolean(sessionQuery.data?.mentorId),
    queryFn: async () => (await mentorService.getById(sessionQuery.data!.mentorId)).data.data,
  });

  const feedbackQuery = useQuery({
    queryKey: ['session', sessionId, 'feedback'],
    enabled: Number.isFinite(sessionId),
    queryFn: async () => (await sessionService.getFeedback(sessionId)).data.data,
  });

  const cancelMutation = useMutation({
    mutationFn: async () => sessionService.cancel(sessionId, cancelReason.trim()),
    onSuccess: async () => {
      toast.success(t('sessions.cancel_success'));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['session', sessionId] }),
        queryClient.invalidateQueries({ queryKey: ['sessions', 'history'] }),
      ]);
      setShowCancelForm(false);
      setCancelReason('');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const feedbackMutation = useMutation({
    mutationFn: async (values: { rating: number; comment: string }) =>
      sessionService.submitFeedback(sessionId, values),
    onSuccess: async () => {
      toast.success(t('sessions.feedback_success'));
      await queryClient.invalidateQueries({ queryKey: ['session', sessionId, 'feedback'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (sessionQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (sessionQuery.isError || !sessionQuery.data) {
    return <Card className="text-red-500">{t('common.error')}</Card>;
  }

  const session = sessionQuery.data;
  const feedback = feedbackQuery.data ?? [];
  const status = session.status.toUpperCase();
  const canCancel = !['COMPLETED', 'CANCELLED'].includes(status);

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('sessions.session_details')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{session.bookingReference}</p>
      </div>

      <Card className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('sessions.mentor')}</p>
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {mentorQuery.data?.name ?? `#${session.mentorId}`}
            </h2>
          </div>
          <SessionStatusBadge status={session.status} />
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.date')}</p>
            <p className="mt-1 font-semibold text-slate-950 dark:text-white">
              {format(new Date(session.startAt), 'dd MMM yyyy')}
            </p>
          </div>
          <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.time')}</p>
            <p className="mt-1 font-semibold text-slate-950 dark:text-white">
              {format(new Date(session.startAt), 'hh:mm a')} - {format(new Date(session.endAt), 'hh:mm a')}
            </p>
          </div>
          <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.topic')}</p>
            <p className="mt-1 font-semibold text-slate-950 dark:text-white">{session.topic}</p>
          </div>
          <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.amount')}</p>
            <p className="mt-1 font-semibold text-slate-950 dark:text-white">
              {currencyFormatter.format(session.amount)}
            </p>
          </div>
        </div>

        <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
          <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.notes')}</p>
          <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">
            {session.notes || t('common.optional')}
          </p>
        </div>

        <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
          <div className="flex items-center gap-3">
            <Video className="size-4 text-[var(--color-primary)]" />
            <p className="font-semibold text-slate-950 dark:text-white">{t('sessions.meeting_link')}</p>
          </div>
          {session.zoomLink ? (
            <a
              href={session.zoomLink}
              target="_blank"
              rel="noreferrer"
              className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-[var(--color-primary)]"
            >
              {t('sessions.open_zoom')}
              <ExternalLink className="size-4" />
            </a>
          ) : (
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">{t('common.unavailable')}</p>
          )}
        </div>

        {canCancel ? (
          !showCancelForm ? (
            <Button variant="danger" onClick={() => setShowCancelForm(true)}>
              {t('sessions.cancel_session')}
            </Button>
          ) : (
            <div className="space-y-3 rounded-3xl border border-red-200 p-4 dark:border-red-950/60">
              <label className="block space-y-2">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                  {t('sessions.cancel_reason')}
                </span>
                <textarea
                  rows={3}
                  value={cancelReason}
                  onChange={(event) => setCancelReason(event.target.value)}
                  className="w-full rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
                />
              </label>
              <div className="flex flex-wrap gap-3">
                <Button
                  variant="danger"
                  isLoading={cancelMutation.isPending}
                  onClick={() => {
                    if (!cancelReason.trim()) {
                      toast.error(t('errors.required'));
                      return;
                    }
                    cancelMutation.mutate();
                  }}
                >
                  {t('sessions.cancel_session')}
                </Button>
                <Button variant="outline" onClick={() => setShowCancelForm(false)}>
                  {t('common.cancel')}
                </Button>
              </div>
            </div>
          )
        ) : null}
      </Card>

      <Card className="space-y-4">
        <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
          {t('sessions.feedback_title')}
        </h2>

        {feedback.length ? (
          <div className="space-y-4">
            {feedback.map((item) => (
              <div
                key={item.id}
                className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <RatingStars value={item.rating} size={16} />
                  <span className="text-sm text-slate-500 dark:text-slate-400">
                    {format(new Date(item.createdAt), 'dd MMM yyyy')}
                  </span>
                </div>
                <p className="mt-3 text-sm leading-7 text-slate-700 dark:text-slate-300">
                  {item.comment}
                </p>
              </div>
            ))}
          </div>
        ) : session.status.toUpperCase() === 'COMPLETED' ? (
          <ReviewForm
            isSubmitting={feedbackMutation.isPending}
            onSubmit={async (values) => {
              await feedbackMutation.mutateAsync(values);
            }}
          />
        ) : (
          <p className="text-sm text-slate-500 dark:text-slate-400">{t('sessions.no_feedback')}</p>
        )}
      </Card>
    </div>
  );
}
