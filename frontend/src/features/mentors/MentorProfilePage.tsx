import { useState } from 'react';
import { CalendarClock, Globe2, MessageSquareQuote } from 'lucide-react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { useNavigate, useParams } from 'react-router-dom';
import { Badge, Button, Card, RatingStars } from '../../components/ui';
import { getApiErrorMessage, getInitials } from '../../lib/utils';
import { mentorService } from '../../services/mentorService';
import { reviewService } from '../../services/reviewService';
import { skillService } from '../../services/skillService';

const weekdayOrder = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

function formatWeekday(day: string) {
  return `${day.slice(0, 1)}${day.slice(1).toLowerCase()}`;
}

export default function MentorProfilePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams();
  const mentorId = Number(id);
  const [reviewPage, setReviewPage] = useState(0);
  const [isSubmittingWaitlist, setIsSubmittingWaitlist] = useState(false);

  const mentorQuery = useQuery({
    queryKey: ['mentor', mentorId],
    enabled: Number.isFinite(mentorId),
    queryFn: async () => (await mentorService.getById(mentorId)).data.data,
  });

  const availabilityQuery = useQuery({
    queryKey: ['mentor', mentorId, 'availability'],
    enabled: Number.isFinite(mentorId),
    queryFn: async () => (await mentorService.getAvailability(mentorId)).data.data,
  });

  const reviewsQuery = useQuery({
    queryKey: ['mentor', mentorId, 'reviews', reviewPage],
    enabled: Number.isFinite(mentorId),
    queryFn: async () =>
      (await reviewService.getMentorReviews(mentorId, { page: reviewPage, size: 5 })).data.data,
  });

  const skillsQuery = useQuery({
    queryKey: ['skills', 'mentor', mentorId],
    queryFn: async () => (await skillService.getAll()).data.data,
  });

  const waitlistMutation = useMutation({
    mutationFn: async () => mentorService.joinWaitlist(mentorId),
    onSuccess: () => {
      toast.success(t('mentors.waitlist_success'));
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
    onSettled: () => setIsSubmittingWaitlist(false),
  });

  if (mentorQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (mentorQuery.isError || !mentorQuery.data) {
    return <Card className="text-red-500">{t('common.error')}</Card>;
  }

  const mentor = mentorQuery.data;
  const skillMap = Object.fromEntries((skillsQuery.data ?? []).map((skill) => [skill.id, skill.name]));
  const skillNames =
    mentor.skills.length > 0
      ? mentor.skills
      : mentor.skillIds.map((skillId) => skillMap[skillId]).filter(Boolean);
  const availabilityMap = weekdayOrder.map((day) => ({
    day,
    slots:
      availabilityQuery.data?.filter((slot) => slot.dayOfWeek.toUpperCase() === day).map((slot) => slot) ??
      [],
  }));

  return (
    <div className="space-y-6">
      <Card className="overflow-hidden p-0">
        <div className="grid gap-6 bg-gradient-to-br from-blue-600 to-slate-950 px-6 py-8 text-white md:grid-cols-[auto_minmax(0,1fr)_auto] md:px-8">
          <div className="flex items-center justify-center">
            {mentor.avatarUrl ? (
              <img
                src={mentor.avatarUrl}
                alt={mentor.name}
                className="size-24 rounded-[2rem] border border-white/20 object-cover"
              />
            ) : (
              <div className="flex size-24 items-center justify-center rounded-[2rem] bg-white/10 text-2xl font-semibold">
                {getInitials(mentor.name)}
              </div>
            )}
          </div>

          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-3xl font-semibold">{mentor.name}</h1>
              {mentor.isAvailable ? <Badge variant="success">{t('mentors.available')}</Badge> : null}
            </div>
            <p className="max-w-2xl text-sm text-blue-100">{mentor.headline}</p>
            <div className="flex flex-wrap items-center gap-4 text-sm text-blue-100">
              <div className="flex items-center gap-2">
                <RatingStars value={mentor.rating} size={14} />
                <span>{mentor.rating.toFixed(1)}</span>
                <span>({mentor.totalReviews})</span>
              </div>
              <span>{mentor.totalSessions} {t('mentors.sessions_completed')}</span>
            </div>
            <div className="flex flex-wrap gap-2">
              {skillNames.map((skill) => (
                <Badge key={skill} className="normal-case tracking-normal">
                  {skill}
                </Badge>
              ))}
            </div>
          </div>

          <div className="flex flex-col justify-between gap-3">
            <div className="text-right text-2xl font-semibold">
              {new Intl.NumberFormat('en-IN', {
                style: 'currency',
                currency: 'INR',
                maximumFractionDigits: 0,
              }).format(mentor.hourlyRate)}
            </div>
            <Button onClick={() => navigate(`/mentors/${mentor.id}/book`)}>
              {t('mentors.book_session')}
            </Button>
            {!mentor.isAvailable ? (
              <Button
                variant="outline"
                className="border-white/30 bg-white/10 text-white hover:bg-white/20"
                isLoading={isSubmittingWaitlist}
                onClick={() => {
                  setIsSubmittingWaitlist(true);
                  waitlistMutation.mutate();
                }}
              >
                {t('mentors.join_waitlist')}
              </Button>
            ) : null}
          </div>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        <div className="space-y-6">
          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">{t('mentors.about')}</h2>
            <p className="whitespace-pre-wrap text-sm leading-7 text-slate-600 dark:text-slate-300">
              {mentor.bio}
            </p>
          </Card>

          <Card className="space-y-4">
            <div className="flex items-center gap-3">
              <MessageSquareQuote className="size-5 text-[var(--color-primary)]" />
              <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
                {t('mentors.reviews')}
              </h2>
            </div>

            {reviewsQuery.data?.content.length ? (
              <div className="space-y-4">
                {reviewsQuery.data.content.map((review) => (
                  <div
                    key={review.id}
                    className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <RatingStars value={review.rating} size={14} />
                      <span className="text-sm text-slate-500 dark:text-slate-400">
                        {format(new Date(review.createdAt), 'dd MMM yyyy')}
                      </span>
                    </div>
                    <p className="mt-3 text-sm leading-7 text-slate-600 dark:text-slate-300">
                      {review.comment}
                    </p>
                  </div>
                ))}
                <div className="flex justify-end gap-3">
                  <Button variant="outline" onClick={() => setReviewPage((current) => Math.max(0, current - 1))} disabled={reviewPage === 0}>
                    {t('common.previous')}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => setReviewPage((current) => current + 1)}
                    disabled={Boolean(reviewsQuery.data?.last)}
                  >
                    {t('common.next')}
                  </Button>
                </div>
              </div>
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentors.no_reviews')}</p>
            )}
          </Card>
        </div>

        <div className="space-y-6">
          <Card className="space-y-4">
            <div className="flex items-center gap-3">
              <CalendarClock className="size-5 text-[var(--color-primary)]" />
              <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
                {t('mentors.weekly_availability')}
              </h2>
            </div>

            {availabilityQuery.data?.length ? (
              <div className="grid gap-3 sm:grid-cols-2">
                {availabilityMap.map((item) => (
                  <div
                    key={item.day}
                    className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
                  >
                    <p className="font-semibold text-slate-900 dark:text-slate-100">
                      {formatWeekday(item.day)}
                    </p>
                    <div className="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-300">
                      {item.slots.length ? (
                        item.slots.map((slot) => (
                          <div key={slot.id}>
                            {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
                          </div>
                        ))
                      ) : (
                        <span className="text-slate-400 dark:text-slate-500">
                          {t('common.unavailable')}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentors.no_availability')}</p>
            )}
          </Card>

          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {t('mentors.experience')}
            </h2>
            <p className="text-sm leading-7 text-slate-600 dark:text-slate-300">
              {mentor.experience || t('common.no_results')}
            </p>
          </Card>

          <Card className="space-y-4">
            <div className="flex items-center gap-3">
              <Globe2 className="size-5 text-[var(--color-primary)]" />
              <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
                {t('mentors.languages')}
              </h2>
            </div>
            <div className="flex flex-wrap gap-2">
              {mentor.languages.length ? (
                mentor.languages.map((language) => (
                  <Badge key={language} variant="info" className="normal-case tracking-normal">
                    {language}
                  </Badge>
                ))
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.no_results')}</p>
              )}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
