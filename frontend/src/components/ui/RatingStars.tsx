import { Star } from 'lucide-react';
import { cn } from '../../lib/utils';

interface RatingStarsProps {
  value: number;
  total?: number;
  size?: number;
  className?: string;
  onChange?: (value: number) => void;
}

export default function RatingStars({
  value,
  total = 5,
  size = 16,
  className,
  onChange,
}: RatingStarsProps) {
  const roundedValue = Math.round(value);
  const isInteractive = typeof onChange === 'function';

  return (
    <div className={cn('flex items-center gap-1', className)}>
      {Array.from({ length: total }, (_, index) => {
        const starValue = index + 1;
        const filled = roundedValue >= starValue;

        if (isInteractive) {
          return (
            <button
              key={starValue}
              type="button"
              className="rounded-full p-1 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-[var(--color-ring-soft)]"
              onClick={() => onChange?.(starValue)}
              aria-label={`Rate ${starValue} out of ${total}`}
            >
              <Star
                size={size}
                className={filled ? 'fill-amber-400 text-amber-400' : 'text-slate-300 dark:text-slate-600'}
              />
            </button>
          );
        }

        return (
          <Star
            key={starValue}
            size={size}
            className={filled ? 'fill-amber-400 text-amber-400' : 'text-slate-300 dark:text-slate-600'}
          />
        );
      })}
    </div>
  );
}
