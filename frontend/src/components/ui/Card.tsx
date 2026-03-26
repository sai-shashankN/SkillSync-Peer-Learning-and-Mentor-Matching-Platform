import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/utils';

export default function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'rounded-[var(--radius-card)] border border-white/60 bg-white/85 p-6 shadow-[var(--shadow-soft)] backdrop-blur dark:border-slate-800 dark:bg-slate-900/85',
        className,
      )}
      {...props}
    />
  );
}
