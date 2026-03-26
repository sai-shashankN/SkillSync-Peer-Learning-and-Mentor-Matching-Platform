import { Clock3 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Card, RatingStars } from '../../components/ui';
import type { MentorSummary } from '../../services/mentorService';
import { getInitials } from '../../lib/utils';

interface MentorCardProps {
  mentor: MentorSummary;
  skillNames?: string[];
}

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export default function MentorCard({ mentor, skillNames }: MentorCardProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const visibleSkills = skillNames ?? mentor.skills;

  return (
    <Card className="flex h-full flex-col gap-5">
      <div className="flex items-start gap-4">
        {mentor.avatarUrl ? (
          <img
            src={mentor.avatarUrl}
            alt={mentor.name}
            className="size-16 rounded-3xl object-cover"
          />
        ) : (
          <div className="flex size-16 items-center justify-center rounded-3xl bg-slate-950 text-lg font-semibold text-white dark:bg-slate-100 dark:text-slate-950">
            {getInitials(mentor.name)}
          </div>
        )}

        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-semibold text-slate-950 dark:text-white">{mentor.name}</h3>
            {mentor.isAvailable ? (
              <Badge variant="success">{t('mentors.available')}</Badge>
            ) : null}
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-300">{mentor.headline}</p>
          <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
            <RatingStars value={mentor.rating} size={14} />
            <span>{mentor.rating.toFixed(1)}</span>
            <span>({mentor.totalReviews})</span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
        <Clock3 className="size-4 text-[var(--color-primary)]" />
        <span>
          {t('mentors.price_per_hour', { amount: currencyFormatter.format(mentor.hourlyRate) })}
        </span>
      </div>

      <div className="flex flex-wrap gap-2">
        {visibleSkills.slice(0, 3).map((skill) => (
          <Badge key={skill} variant="info" className="normal-case tracking-normal">
            {skill}
          </Badge>
        ))}
      </div>

      <Button className="mt-auto w-full" onClick={() => navigate(`/mentors/${mentor.id}`)}>
        {t('mentors.view_profile')}
      </Button>
    </Card>
  );
}
