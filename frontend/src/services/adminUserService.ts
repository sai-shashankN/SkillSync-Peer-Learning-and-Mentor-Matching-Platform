import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface AdminUser {
  userId: number;
  name?: string | null;
  email?: string | null;
  roles: string[];
  isActive: boolean;
  avatarUrl?: string | null;
  createdAt: string;
}

export const adminUserService = {
  getAll: (params?: {
    search?: string;
    role?: string;
    status?: string;
    page?: number;
    size?: number;
  }) => api.get<PagedResponse<AdminUser>>('/users/admin', { params }),
  getById: (id: number) => api.get<ApiResponse<AdminUser>>(`/users/admin/${id}`),
  banUser: (id: number) => api.patch<ApiResponse<void>>(`/users/admin/${id}/ban`),
  unbanUser: (id: number) => api.patch<ApiResponse<void>>(`/users/admin/${id}/unban`),
};
