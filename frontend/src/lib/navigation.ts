import type { LucideIcon } from 'lucide-react';
import {
  BarChart3,
  CalendarClock,
  Coins,
  Compass,
  CreditCard,
  FileClock,
  LayoutDashboard,
  ShieldCheck,
  UserCircle2,
  UserRoundCog,
  Users,
} from 'lucide-react';
import type { UserInfo } from '../types';

export interface NavigationItem {
  labelKey: string;
  path: string;
  icon: LucideIcon;
}

function hasRole(user: UserInfo | null, role: string) {
  return user?.roles.includes(role) ?? false;
}

export function getNavbarItems(user: UserInfo | null): NavigationItem[] {
  if (hasRole(user, 'ADMIN')) {
    return [
      { labelKey: 'nav.dashboard', path: '/dashboard', icon: LayoutDashboard },
      { labelKey: 'nav.users', path: '/users', icon: Users },
      { labelKey: 'nav.analytics', path: '/analytics', icon: BarChart3 },
      { labelKey: 'nav.admin', path: '/admin', icon: ShieldCheck },
    ];
  }

  if (hasRole(user, 'MENTOR')) {
    return [
      { labelKey: 'nav.dashboard', path: '/dashboard', icon: LayoutDashboard },
      { labelKey: 'nav.mentor_sessions', path: '/mentor/sessions', icon: CalendarClock },
      { labelKey: 'nav.earnings', path: '/earnings', icon: Coins },
      { labelKey: 'nav.profile', path: '/profile', icon: UserCircle2 },
    ];
  }

  return [
    { labelKey: 'nav.dashboard', path: '/dashboard', icon: LayoutDashboard },
    { labelKey: 'nav.mentors', path: '/mentors', icon: Compass },
    { labelKey: 'nav.groups', path: '/groups', icon: Users },
    { labelKey: 'nav.profile', path: '/profile', icon: UserCircle2 },
  ];
}

export function getSidebarItems(user: UserInfo | null): NavigationItem[] {
  if (hasRole(user, 'ADMIN')) {
    return [
      { labelKey: 'nav.dashboard', path: '/dashboard', icon: LayoutDashboard },
      { labelKey: 'nav.users', path: '/users', icon: Users },
      { labelKey: 'admin.pending_approvals', path: '/admin/pending-approvals', icon: UserRoundCog },
      { labelKey: 'nav.payments', path: '/payments', icon: CreditCard },
      { labelKey: 'nav.audit_logs', path: '/audit-logs', icon: FileClock },
      { labelKey: 'nav.analytics', path: '/analytics', icon: BarChart3 },
    ];
  }

  if (hasRole(user, 'MENTOR')) {
    return [
      { labelKey: 'nav.dashboard', path: '/dashboard', icon: LayoutDashboard },
      { labelKey: 'nav.mentor_sessions', path: '/mentor/sessions', icon: CalendarClock },
      { labelKey: 'nav.earnings', path: '/earnings', icon: Coins },
      { labelKey: 'nav.availability', path: '/availability', icon: Compass },
      { labelKey: 'nav.profile', path: '/profile', icon: UserCircle2 },
    ];
  }

  return [
    { labelKey: 'nav.dashboard', path: '/dashboard', icon: LayoutDashboard },
    { labelKey: 'nav.mentors', path: '/mentors', icon: Compass },
    { labelKey: 'nav.sessions', path: '/sessions', icon: CalendarClock },
    { labelKey: 'nav.groups', path: '/groups', icon: Users },
    { labelKey: 'nav.profile', path: '/profile', icon: UserCircle2 },
  ];
}
