import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';

export function useNotificationSocket(): void {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) {
      return undefined;
    }

    // TODO: Replace with STOMP WebSocket connection to /ws/notifications for real-time push
    const intervalId = window.setInterval(() => {
      void queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] });
    }, 30000);

    return () => window.clearInterval(intervalId);
  }, [isAuthenticated, queryClient]);
}
