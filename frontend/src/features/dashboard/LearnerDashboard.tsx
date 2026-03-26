import { Award, CalendarRange, Compass, Users } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Card, RatingStars } from '../../components/ui';
import { useAuthStore } from '../../store/authStore';
import { groupService } from '../../services/groupService';
import { mentorService } from '../../services/mentorService';
import { reviewService } from '../../services/reviewService';
import { sessionService } from '../../services/sessionService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export default function LearnerDashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  const statsQuery = useQuery({
    queryKey: ['dashboard', 'learner', 'stats'],
    queryFn: async () => {
      const [sessions, groups, badges] = await Promise.all([
        sessionService.getMySessions({ role: 'LEARNER', page: 0, size: 1 }),
        groupService.getMyGroups(),
        reviewService.getMyBadges(),
      ]);

      return {
        totalSessions: sessions.data.totalElements,
        activeGroups: groups.data.data.length,
        badgesEarned: badges.data.data.length,
      };
    },
  });

  const upcomingSessionsQuery = useQuery({
    queryKey: ['dashboard', 'learner', 'upcoming-sessions'],
    queryFn: async () =>
      (await sessionService.getMySessions({ role: 'LEARNER', status: 'UPCOMING', page: 0, size: 3 }))
        .data.content,
  });

  const recommendedMentorsQuery = useQuery({
    queryKey: ['dashboard', 'learner', 'recommended-mentors'],
    queryFn: async () => {
      const mentors = (await mentorService.search({ page: 0, size: 4, sort: 'avgRating,desc' })).data
        .content;
      return [...mentors].sort((first, second) => (second.rating ?? 0) - (first.rating ?? 0));
    },
  });

  const upcomingMentorIds = [
    ...new Set(upcomingSessionsQuery.data?.map((session) => session.mentorId) ?? []),
  ];

  const mentorNamesQuery = useQuery({
    queryKey: ['dashboard', 'learner', 'mentor-names', upcomingMentorIds],
    enabled: upcomingMentorIds.length > 0,
    queryFn: async () => {
      const entries = await Promise.all(
        upcomingMentorIds.map(async (mentorId) => {
          const response = await mentorService.getById(mentorId);
          return [mentorId, response.data.data.name] as const;
        }),
      );

      return Object.fromEntries(entries);
    },
  });

  const statItems = [
    {
      label: t('dashboard.total_sessions'),
      value: statsQuery.data?.totalSessions ?? 0,
      icon: CalendarRange,
    },
    {
      label: t('dashboard.active_groups'),
      value: statsQuery.data?.activeGroups ?? 0,
      icon: Users,
    },
    {
      label: t('dashboard.badges_earned'),
      value: statsQuery.data?.badgesEarned ?? 0,
      icon: Award,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('dashboard.welcome', { name: user?.name ?? 'Learner' })}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('dashboard.subtitle')}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {statItems.map((item) => {
          const Icon = item.icon;

          return (
            <Card key={item.label} className="space-y-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-slate-500 dark:text-slate-400">{item.label}</p>
                <div className="rounded-2xl bg-blue-50 p-3 text-[var(--color-primary)] dark:bg-blue-950/40">
                  <Icon className="size-5" />
                </div>
              </div>
              <p className="text-4xl font-semibold text-slate-950 dark:text-white">{item.value}</p>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <Card className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {t('dashboard.upcoming_sessions')}
            </h2>
            <Button variant="outline" onClick={() => navigate('/sessions')}>
              {t('sessions.view_details')}
            </Button>
          </div>

          {upcomingSessionsQuery.data?.length ? (
            <div className="space-y-4">
              {upcomingSessionsQuery.data.map((session) => (
                <div
                  key={session.id}
                  className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
                >
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    {format(new Date(session.startAt), 'dd MMM yyyy, hh:mm a')}
                  </p>
                  <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="font-semibold text-slate-950 dark:text-white">{session.topic}</p>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        {mentorNamesQuery.data?.[session.mentorId] ?? `#${session.mentorId}`}
                      </p>
                    </div>
                    <Badge variant="info" className="normal-case tracking-normal">
                      {t('dashboard.next_session')}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('dashboard.no_upcoming')}</p>
          )}
        </Card>

        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('dashboard.quick_actions')}
          </h2>
          <div className="grid gap-3">
            <Button onClick={() => navigate('/mentors')}>
              <Compass className="size-4" />
              {t('dashboard.find_mentor')}
            </Button>
            <Button variant="outline" onClick={() => navigate('/groups')}>
              <Users className="size-4" />
              {t('dashboard.browse_groups')}
            </Button>
          </div>
        </Card>
      </div>

      <Card className="space-y-4">
        <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
          {t('dashboard.recommended_mentors')}
        </h2>

        {recommendedMentorsQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {recommendedMentorsQuery.data.map((mentor) => (
              <button
                key={mentor.id}
                type="button"
                className="rounded-3xl border border-slate-200/70 bg-white/50 p-4 text-left transition hover:border-blue-200 dark:border-slate-800 dark:bg-slate-950/30 dark:hover:border-slate-700"
                onClick={() => navigate(`/mentors/${mentor.id}`)}
              >
                <p className="font-semibold text-slate-950 dark:text-white">{mentor.name}</p>
                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{mentor.headline}</p>
                <div className="mt-3 flex items-center gap-2">
                  <RatingStars value={mentor.rating} size={14} />
                  <span className="text-sm text-slate-600 dark:text-slate-300">
                    {mentor.rating.toFixed(1)}
                  </span>
                </div>
                <p className="mt-3 text-sm font-medium text-slate-700 dark:text-slate-200">
                  {currencyFormatter.format(mentor.hourlyRate)}
                </p>
              </button>
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            {t('dashboard.no_recommendations')}
          </p>
        )}
      </Card>
    </div>
  );
}
