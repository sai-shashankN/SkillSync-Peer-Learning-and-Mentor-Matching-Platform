import { useDeferredValue, useState } from 'react';
import { Search, SlidersHorizontal } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, Card, Input } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { mentorService } from '../../services/mentorService';
import { skillService } from '../../services/skillService';
import MentorCard from './MentorCard';

export default function MentorDiscoveryPage() {
  const { t } = useTranslation();
  const { page, size, setPage, nextPage, prevPage } = usePagination(0, 6);
  const [searchTerm, setSearchTerm] = useState('');
  const [skillId, setSkillId] = useState<number | undefined>();
  const [minRating, setMinRating] = useState(0);
  const [maxPrice, setMaxPrice] = useState(5000);
  const [sort, setSort] = useState('avgRating,desc');
  const deferredSearch = useDeferredValue(searchTerm.trim().toLowerCase());

  const skillsQuery = useQuery({
    queryKey: ['skills', 'mentor-discovery'],
    queryFn: async () => (await skillService.getAll()).data.data,
  });

  const mentorsQuery = useQuery({
    queryKey: ['mentors', 'discovery', { skillId, minRating, maxPrice, page, size, sort }],
    queryFn: async () =>
      (
        await mentorService.search({
          skillId,
          minRating,
          maxPrice,
          page,
          size,
          sort,
        })
      ).data,
  });

  const skillMap = Object.fromEntries((skillsQuery.data ?? []).map((skill) => [skill.id, skill.name]));

  const mentors =
    mentorsQuery.data?.content.filter((mentor) => {
      if (!deferredSearch) {
        return true;
      }

      const mentorSkillNames =
        mentor.skills.length > 0
          ? mentor.skills
          : mentor.skillIds.map((skillId) => skillMap[skillId]).filter(Boolean);

      const haystack = [mentor.name, mentor.headline, mentorSkillNames.join(' ')]
        .join(' ')
        .toLowerCase();

      return haystack.includes(deferredSearch);
    }) ?? [];

  const clearFilters = () => {
    setSkillId(undefined);
    setMinRating(0);
    setMaxPrice(5000);
    setSort('avgRating,desc');
    setPage(0);
  };

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('mentors.discover_title')}
        </h1>
        <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">
          {t('mentors.discover_subtitle')}
        </p>
      </div>

      <div className="grid gap-6 xl:grid-cols-[300px_minmax(0,1fr)]">
        <Card className="space-y-5">
          <div className="flex items-center gap-3">
            <SlidersHorizontal className="size-5 text-[var(--color-primary)]" />
            <h2 className="text-lg font-semibold text-slate-950 dark:text-white">
              {t('common.filters')}
            </h2>
          </div>

          <div className="space-y-4">
            <label className="block space-y-2">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                {t('mentors.skill_filter')}
              </span>
              <select
                value={skillId ?? ''}
                onChange={(event) => {
                  setSkillId(event.target.value ? Number(event.target.value) : undefined);
                  setPage(0);
                }}
                className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
              >
                <option value="">{t('mentors.all_skills')}</option>
                {skillsQuery.data?.map((skill) => (
                  <option key={skill.id} value={skill.id}>
                    {skill.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="block space-y-2">
              <span className="flex items-center justify-between text-sm font-medium text-slate-700 dark:text-slate-200">
                <span>{t('mentors.min_rating')}</span>
                <span>{minRating.toFixed(1)}</span>
              </span>
              <input
                type="range"
                min="0"
                max="5"
                step="0.5"
                value={minRating}
                onChange={(event) => {
                  setMinRating(Number(event.target.value));
                  setPage(0);
                }}
                className="w-full accent-[var(--color-primary)]"
              />
            </label>

            <label className="block space-y-2">
              <span className="flex items-center justify-between text-sm font-medium text-slate-700 dark:text-slate-200">
                <span>{t('mentors.max_price')}</span>
                <span>
                  {new Intl.NumberFormat('en-IN', {
                    style: 'currency',
                    currency: 'INR',
                    maximumFractionDigits: 0,
                  }).format(maxPrice)}
                </span>
              </span>
              <input
                type="range"
                min="500"
                max="10000"
                step="250"
                value={maxPrice}
                onChange={(event) => {
                  setMaxPrice(Number(event.target.value));
                  setPage(0);
                }}
                className="w-full accent-[var(--color-primary)]"
              />
            </label>

            <label className="block space-y-2">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                {t('mentors.sort_by')}
              </span>
              <select
                value={sort}
                onChange={(event) => {
                  setSort(event.target.value);
                  setPage(0);
                }}
                className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
              >
                <option value="avgRating,desc">{t('mentors.sort_rating')}</option>
                <option value="hourlyRate,asc">{t('mentors.sort_price_low')}</option>
                <option value="hourlyRate,desc">{t('mentors.sort_price_high')}</option>
              </select>
            </label>
          </div>

          <Button variant="outline" className="w-full" onClick={clearFilters}>
            {t('common.clear_filters')}
          </Button>
        </Card>

        <div className="space-y-6">
          <Card className="space-y-4">
            <div className="relative">
              <Search className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
              <Input
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                placeholder={t('mentors.search_placeholder')}
                className="pl-11"
              />
            </div>
            <div className="text-sm text-slate-500 dark:text-slate-400">
              {t('mentors.results_count', { count: mentors.length })}
            </div>
          </Card>

          {mentorsQuery.isLoading ? (
            <Card>{t('common.loading')}</Card>
          ) : mentorsQuery.isError ? (
            <Card className="text-red-500">
              {getApiErrorMessage(mentorsQuery.error, t('common.error'))}
            </Card>
          ) : mentors.length === 0 ? (
            <Card>{t('common.no_results')}</Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {mentors.map((mentor) => (
                <MentorCard
                  key={mentor.id}
                  mentor={mentor}
                  skillNames={mentor.skillIds.map((skillId) => skillMap[skillId]).filter(Boolean)}
                />
              ))}
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-sm text-slate-500 dark:text-slate-400">
              {mentorsQuery.data ? `${page + 1} / ${Math.max(1, mentorsQuery.data.totalPages)}` : ''}
            </span>
            <div className="flex gap-3">
              <Button variant="outline" onClick={prevPage} disabled={page === 0}>
                {t('common.previous')}
              </Button>
              <Button
                variant="outline"
                onClick={nextPage}
                disabled={Boolean(mentorsQuery.data?.last)}
              >
                {t('common.next')}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
