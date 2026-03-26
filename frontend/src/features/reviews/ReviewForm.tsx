import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, RatingStars } from '../../components/ui';

interface ReviewFormProps {
  initialRating?: number;
  initialComment?: string;
  isSubmitting?: boolean;
  submitLabel?: string;
  onSubmit: (values: { rating: number; comment: string }) => void | Promise<void>;
}

export default function ReviewForm({
  initialRating = 0,
  initialComment = '',
  isSubmitting = false,
  submitLabel,
  onSubmit,
}: ReviewFormProps) {
  const { t } = useTranslation();
  const [rating, setRating] = useState(initialRating);
  const [comment, setComment] = useState(initialComment);
  const [error, setError] = useState('');

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (rating < 1 || !comment.trim()) {
      setError(t('errors.required'));
      return;
    }

    setError('');
    await onSubmit({ rating, comment: comment.trim() });
  };

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      <div className="space-y-2">
        <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
          {t('reviews.rating_label')}
        </p>
        <RatingStars value={rating} size={24} onChange={setRating} />
      </div>

      <label className="block space-y-2">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
          {t('reviews.comment_label')}
        </span>
        <textarea
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          rows={4}
          placeholder={t('reviews.placeholder')}
          className="w-full rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
        />
      </label>

      {error ? <p className="text-sm text-red-500">{error}</p> : null}

      <Button type="submit" isLoading={isSubmitting}>
        {submitLabel ?? t('reviews.submit_review')}
      </Button>
    </form>
  );
}
