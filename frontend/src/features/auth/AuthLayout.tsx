import type { ReactNode } from 'react';
import { Sparkles, Stars } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface AuthLayoutProps {
  title: string;
  subtitle: ReactNode;
  children: ReactNode;
}

export default function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-transparent px-4 py-8 sm:px-6 lg:px-10">
      <div className="mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl overflow-hidden rounded-[2rem] border border-white/60 bg-white/70 shadow-[var(--shadow-soft)] backdrop-blur dark:border-slate-800 dark:bg-slate-950/70 lg:grid-cols-[1.05fr_0.95fr]">
        <section className="relative hidden overflow-hidden bg-slate-950 px-8 py-10 text-white lg:flex lg:flex-col lg:justify-between">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(37,99,235,0.35),_transparent_34%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.3),_transparent_28%)]" />
          <div className="relative flex items-center gap-3">
            <div className="flex size-11 items-center justify-center rounded-2xl bg-white/12 backdrop-blur">
              <Sparkles className="size-5" />
            </div>
            <div>
              <p className="font-semibold uppercase tracking-[0.18em] text-slate-200">
                {t('app.name')}
              </p>
              <p className="text-sm text-slate-300">{t('app.tagline')}</p>
            </div>
          </div>

          <div className="relative space-y-6">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm text-slate-200">
              <Stars className="size-4" />
              {t('auth.hero_title')}
            </div>
            <div className="space-y-4">
              <h1 className="max-w-md text-4xl font-semibold leading-tight">{t('auth.hero_title')}</h1>
              <p className="max-w-md text-lg text-slate-300">{t('auth.hero_copy')}</p>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-3xl border border-white/12 bg-white/8 p-5">
                <p className="text-sm text-slate-300">{t('nav.sessions')}</p>
                <p className="mt-2 text-3xl font-semibold">0</p>
              </div>
              <div className="rounded-3xl border border-white/12 bg-white/8 p-5">
                <p className="text-sm text-slate-300">{t('nav.groups')}</p>
                <p className="mt-2 text-3xl font-semibold">0</p>
              </div>
            </div>
          </div>

          <p className="relative text-sm text-slate-400">{t('layout.footer_copy')}</p>
        </section>

        <section className="flex items-center justify-center px-5 py-8 sm:px-8 lg:px-10">
          <div className="w-full max-w-md space-y-6">
            <div className="space-y-2">
              <p className="text-sm font-semibold uppercase tracking-[0.22em] text-[var(--color-primary)]">
                {t('app.name')}
              </p>
              <h2 className="text-3xl font-semibold text-slate-950 dark:text-white">{title}</h2>
              <div className="text-sm text-slate-500 dark:text-slate-400">{subtitle}</div>
            </div>
            {children}
          </div>
        </section>
      </div>
    </div>
  );
}
