import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface Notification {
  id: number;
  userId: number;
  type: string;
  title: string;
  message: string;
  data: string | null;
  channel: string;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export const notificationService = {
  getAll: (params?: { read?: boolean; page?: number; size?: number }) =>
    api.get<ApiResponse<PagedResponse<Notification>>>('/notifications/me', { params }),
  markAsRead: (id: number) => api.put<ApiResponse<void>>(`/notifications/${id}/read`),
  markAllAsRead: () => api.put<ApiResponse<void>>('/notifications/me/read-all'),
  getUnreadCount: () =>
    api.get<ApiResponse<{ count: number }>>('/notifications/me/unread-count'),
};
