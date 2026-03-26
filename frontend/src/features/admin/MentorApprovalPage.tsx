import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Button, Card, Modal } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { mentorService } from '../../services/mentorService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export default function MentorApprovalPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { page, size, nextPage, prevPage } = usePagination(0, 8);
  const [selectedMentorId, setSelectedMentorId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const mentorsQuery = useQuery({
    queryKey: ['mentors', 'approvals', { page, size }],
    queryFn: async () => (await mentorService.listAdmin({ page, size, status: 'PENDING' })).data,
  });

  const mentors = mentorsQuery.data?.content ?? [];

  const refreshMentors = async () => {
    await queryClient.invalidateQueries({ queryKey: ['mentors'] });
  };

  const approveMutation = useMutation({
    mutationFn: (mentorId: number) => mentorService.approveMentor(mentorId),
    onSuccess: async () => {
      toast.success(t('admin.approve_success'));
      await refreshMentors();
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ mentorId, reason }: { mentorId: number; reason: string }) =>
      mentorService.rejectMentor(mentorId, reason),
    onSuccess: async () => {
      toast.success(t('admin.reject_success'));
      setSelectedMentorId(null);
      setRejectReason('');
      await refreshMentors();
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (mentorsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (mentorsQuery.isError) {
    return <Card className="text-red-500">{getApiErrorMessage(mentorsQuery.error, t('common.error'))}</Card>;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('admin.mentor_approval')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('admin.dashboard_subtitle')}</p>
      </div>

      {mentors.length ? (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            {mentors.map((mentor) => (
              <Card key={mentor.id} className="space-y-4">
                <div className="space-y-2">
                  <h2 className="text-xl font-semibold text-slate-950 dark:text-white">{mentor.name}</h2>
                  <p className="text-sm font-medium text-slate-600 dark:text-slate-300">{mentor.headline}</p>
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    {(mentor.bio ?? '').slice(0, 160) || t('common.no_results')}
                  </p>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <p className="text-xs uppercase tracking-[0.18em] text-slate-400">
                      {t('admin.experience_label')}
                    </p>
                    <p className="mt-1 text-sm text-slate-700 dark:text-slate-200">
                      {mentor.experience || t('common.no_results')}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-[0.18em] text-slate-400">
                      {t('admin.rate_label')}
                    </p>
                    <p className="mt-1 text-sm text-slate-700 dark:text-slate-200">
                      {currencyFormatter.format(mentor.hourlyRate)}
                    </p>
                  </div>
                  <div className="sm:col-span-2">
                    <p className="text-xs uppercase tracking-[0.18em] text-slate-400">
                      {t('admin.applied_label')}
                    </p>
                    <p className="mt-1 text-sm text-slate-700 dark:text-slate-200">
                      {mentor.appliedAt || mentor.createdAt
                        ? format(new Date(mentor.appliedAt ?? mentor.createdAt!), 'dd MMM yyyy')
                        : t('common.no_results')}
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap gap-3">
                  <Button
                    isLoading={approveMutation.isPending && approveMutation.variables === mentor.id}
                    onClick={() => approveMutation.mutate(mentor.id)}
                  >
                    {t('admin.approve')}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => {
                      setSelectedMentorId(mentor.id);
                      setRejectReason('');
                    }}
                  >
                    {t('admin.reject')}
                  </Button>
                </div>
              </Card>
            ))}
          </div>

          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={prevPage} disabled={page === 0}>
              {t('common.previous')}
            </Button>
            <Button variant="outline" onClick={nextPage} disabled={Boolean(mentorsQuery.data?.last)}>
              {t('common.next')}
            </Button>
          </div>
        </>
      ) : (
        <Card>{t('admin.no_pending_mentors')}</Card>
      )}

      <Modal
        isOpen={selectedMentorId !== null}
        title={t('admin.reject')}
        onClose={() => setSelectedMentorId(null)}
      >
        <div className="space-y-4">
          <label className="block space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {t('admin.reject_reason')}
            </span>
            <textarea
              rows={4}
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              className="w-full rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
            />
          </label>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setSelectedMentorId(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              isLoading={rejectMutation.isPending}
              onClick={() => {
                if (!rejectReason.trim() || selectedMentorId === null) {
                  toast.error(t('errors.required'));
                  return;
                }

                rejectMutation.mutate({
                  mentorId: selectedMentorId,
                  reason: rejectReason.trim(),
                });
              }}
            >
              {t('admin.reject')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
