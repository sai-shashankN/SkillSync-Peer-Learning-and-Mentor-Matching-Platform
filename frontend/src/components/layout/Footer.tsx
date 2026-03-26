import { useTranslation } from 'react-i18next';

export default function Footer() {
  const { t } = useTranslation();

  return (
    <footer className="border-t border-white/60 bg-white/70 px-4 py-4 text-sm text-slate-500 backdrop-blur dark:border-slate-800 dark:bg-slate-950/70 dark:text-slate-400">
      <div className="mx-auto flex max-w-7xl flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <p>&copy; {new Date().getFullYear()} SkillSync</p>
        <p>{t('layout.footer_copy')}</p>
      </div>
    </footer>
  );
}
