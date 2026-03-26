import { useEffect } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getSidebarItems } from '../../lib/navigation';
import { cn } from '../../lib/utils';
import { useAuthStore } from '../../store/authStore';
import { useUiStore } from '../../store/uiStore';

export default function Sidebar() {
  const { t } = useTranslation();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const isSidebarOpen = useUiStore((state) => state.isSidebarOpen);
  const closeSidebar = useUiStore((state) => state.closeSidebar);
  const items = getSidebarItems(user);

  useEffect(() => {
    closeSidebar();
  }, [closeSidebar, location.pathname]);

  return (
    <>
      {isSidebarOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-20 bg-slate-950/40 lg:hidden"
          onClick={closeSidebar}
          aria-label={t('common.cancel')}
        />
      ) : null}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 w-72 border-r border-white/60 bg-white/90 px-4 pb-6 pt-24 backdrop-blur-xl transition-transform dark:border-slate-800 dark:bg-slate-950/90 lg:sticky lg:top-[77px] lg:h-[calc(100vh-77px)] lg:translate-x-0',
          isSidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="space-y-2">
          {items.map((item) => {
            const Icon = item.icon;

            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium transition',
                    isActive
                      ? 'bg-slate-950 text-white shadow-lg shadow-slate-950/15 dark:bg-slate-100 dark:text-slate-950'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-900 dark:hover:text-white',
                  )
                }
              >
                <Icon className="size-4" />
                <span>{t(item.labelKey)}</span>
              </NavLink>
            );
          })}
        </div>
      </aside>
    </>
  );
}
