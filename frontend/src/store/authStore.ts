import { create } from 'zustand';
import type { UserInfo } from '../types';

interface AuthState {
  accessToken: string | null;
  user: UserInfo | null;
  isAuthenticated: boolean;
  setAccessToken: (token: string | null) => void;
  setUser: (user: UserInfo | null) => void;
  loginSuccess: (token: string, user: UserInfo) => void;
  logout: () => void;
  hasRole: (role: string) => boolean;
  hasPermission: (permission: string) => boolean;
  isAdmin: () => boolean;
  isMentor: () => boolean;
  isLearner: () => boolean;
}

function normalizeRoles(user: UserInfo | null): UserInfo | null {
  if (!user) {
    return null;
  }

  return {
    ...user,
    roles: user.roles.map((role) => role.replace(/^ROLE_/, '')),
  };
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  setAccessToken: (token) =>
    set((state) => ({
      accessToken: token,
      isAuthenticated: Boolean(token || state.user),
    })),
  setUser: (user) =>
    set((state) => ({
      user: normalizeRoles(user),
      isAuthenticated: Boolean(state.accessToken || user),
    })),
  loginSuccess: (token, user) =>
    set({ accessToken: token, user: normalizeRoles(user), isAuthenticated: true }),
  logout: () => set({ accessToken: null, user: null, isAuthenticated: false }),
  hasRole: (role) => get().user?.roles?.includes(role) ?? false,
  hasPermission: (permission) => get().user?.permissions?.includes(permission) ?? false,
  isAdmin: () => get().hasRole('ADMIN'),
  isMentor: () => get().hasRole('MENTOR'),
  isLearner: () => get().hasRole('LEARNER'),
}));
