import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface ThemeState {
  isDark: boolean;
  toggle: () => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      isDark: false,
      toggle: () => {
        const nextDarkMode = !get().isDark;
        set({ isDark: nextDarkMode });
        document.documentElement.classList.toggle('dark', nextDarkMode);
      },
    }),
    { name: 'theme-preference' },
  ),
);
