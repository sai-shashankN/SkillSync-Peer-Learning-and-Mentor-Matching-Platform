import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface AdminUser {
  id: number;
  name: string;
  email: string;
  roles: string[];
  status?: string | null;
  avatarUrl: string | null;
  createdAt: string;
  lastLoginAt: string | null;
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
