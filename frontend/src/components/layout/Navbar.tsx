import { useEffect, useRef, useState } from 'react';
import { Bell, ChevronDown, LogOut, Menu, Moon, Sparkles, Sun } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { useNotificationSocket } from '../../hooks/useNotificationSocket';
import { authService, notificationService } from '../../services';
import { useAuthStore } from '../../store/authStore';
import { useThemeStore } from '../../store/themeStore';
import { useUiStore } from '../../store/uiStore';
import { getNavbarItems } from '../../lib/navigation';
import { cn, getInitials } from '../../lib/utils';
import NotificationDropdown from './NotificationDropdown';
import Button from '../ui/Button';

export default function Navbar() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const isDark = useThemeStore((state) => state.isDark);
  const toggleTheme = useThemeStore((state) => state.toggle);
  const toggleSidebar = useUiStore((state) => state.toggleSidebar);
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const notificationRef = useRef<HTMLDivElement | null>(null);
  const navbarItems = getNavbarItems(user);
  const activeLanguage = i18n.resolvedLanguage?.startsWith('hi') ? 'hi' : 'en';

  useNotificationSocket();

  const unreadCountQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => (await notificationService.getUnreadCount()).data.data.count,
    enabled: Boolean(user),
  });

  useEffect(() => {
    setIsProfileMenuOpen(false);
    setIsNotificationOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!isNotificationOpen) {
      return undefined;
    }

    const handleClickOutside = (event: MouseEvent) => {
      if (!notificationRef.current?.contains(event.target as Node)) {
        setIsNotificationOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isNotificationOpen]);

  const handleLogout = async () => {
    try {
      await authService.logout();
    } catch {
      toast.error(t('common.error'));
    } finally {
      logout();
      navigate('/login', { replace: true });
    }
  };

  return (
    <header className="sticky top-0 z-30 border-b border-white/60 bg-white/80 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-950/80">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            className="lg:hidden"
            onClick={toggleSidebar}
            aria-label={t('layout.menu')}
          >
            <Menu className="size-4" />
          </Button>
          <button
            className="flex items-center gap-3"
            type="button"
            onClick={() => navigate('/dashboard')}
          >
            <div className="flex size-11 items-center justify-center rounded-2xl bg-slate-950 text-white shadow-lg shadow-blue-500/20 dark:bg-white dark:text-slate-950">
              <Sparkles className="size-5" />
            </div>
            <div className="text-left">
              <p className="text-lg font-semibold text-slate-950 dark:text-white">{t('app.name')}</p>
              <p className="hidden text-xs text-slate-500 dark:text-slate-400 sm:block">
                {t('app.tagline')}
              </p>
            </div>
          </button>
        </div>

        <nav className="hidden items-center gap-2 lg:flex">
          {navbarItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                cn(
                  'rounded-full px-4 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-blue-50 text-[var(--color-primary)] dark:bg-blue-950/50 dark:text-blue-200'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white',
                )
              }
            >
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>

        <div className="flex items-center gap-2 sm:gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleTheme}
            aria-label={t('common.dark_mode')}
          >
            {isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>

          <label className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white/80 px-3 py-2 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-200 sm:flex">
            <span className="sr-only">{t('common.language')}</span>
            <select
              value={activeLanguage}
              onChange={(event) => i18n.changeLanguage(event.target.value)}
              className="bg-transparent outline-none"
              aria-label={t('common.language')}
            >
              <option value="en">EN</option>
              <option value="hi">HI</option>
            </select>
          </label>

          <div className="relative" ref={notificationRef}>
            <Button
              variant="ghost"
              size="sm"
              className="relative"
              aria-label={t('common.notifications')}
              aria-expanded={isNotificationOpen}
              onClick={() => setIsNotificationOpen((current) => !current)}
            >
              <Bell className="size-4" />
              {(unreadCountQuery.data ?? 0) > 0 ? (
                <span className="absolute right-2 top-1 inline-flex min-w-4 items-center justify-center rounded-full bg-[var(--color-danger)] px-1 text-[10px] font-bold text-white">
                  {unreadCountQuery.data}
                </span>
              ) : null}
            </Button>

            {isNotificationOpen ? (
              <NotificationDropdown onClose={() => setIsNotificationOpen(false)} />
            ) : null}
          </div>

          <div className="relative">
            <button
              type="button"
              className="flex items-center gap-3 rounded-full border border-slate-200 bg-white/85 px-2 py-2 pr-3 transition hover:border-blue-200 hover:bg-white dark:border-slate-700 dark:bg-slate-900/75 dark:hover:border-slate-500"
              onClick={() => setIsProfileMenuOpen((current) => !current)}
            >
              <span className="flex size-10 items-center justify-center rounded-full bg-slate-950 text-sm font-semibold text-white dark:bg-slate-100 dark:text-slate-950">
                {getInitials(user?.name)}
              </span>
              <span className="hidden text-left sm:block">
                <span className="block text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {user?.name ?? t('layout.guest')}
                </span>
                <span className="block text-xs text-slate-500 dark:text-slate-400">
                  {user?.email ?? 'guest@skillsync.app'}
                </span>
              </span>
              <ChevronDown className="size-4 text-slate-500" />
            </button>

            {isProfileMenuOpen ? (
              <div className="absolute right-0 mt-3 w-60 rounded-3xl border border-white/60 bg-white/95 p-2 shadow-[var(--shadow-soft)] backdrop-blur dark:border-slate-800 dark:bg-slate-950/95">
                <div className="rounded-2xl bg-slate-50 px-4 py-3 dark:bg-slate-900">
                  <p className="font-semibold text-slate-900 dark:text-slate-100">{user?.name}</p>
                  <p className="text-sm text-slate-500 dark:text-slate-400">{user?.email}</p>
                </div>
                <button
                  type="button"
                  className="mt-2 flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 dark:text-slate-200 dark:hover:bg-slate-900 dark:hover:text-white"
                  onClick={handleLogout}
                >
                  <LogOut className="size-4" />
                  {t('nav.logout')}
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </header>
  );
}
