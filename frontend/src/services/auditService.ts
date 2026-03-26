import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface AuditLog {
  id: number;
  userId: number | null;
  actionType: string;
  serviceName: string;
  entityType: string;
  entityId: string | null;
  metadata: Record<string, unknown>;
  ipAddress: string | null;
  createdAt: string;
}

export interface AdminKpi {
  totalUsers: number;
  totalMentors: number;
  totalSessions: number;
  totalRevenue: number;
  activeSessions: number;
  pendingMentorApprovals: number;
}

export interface TimeSeriesPoint {
  date: string;
  value: number;
}

export interface TopSkill {
  skillId: number;
  skillName: string;
  sessionCount: number;
  revenue: number;
}

function toUtcDateBoundary(date: string | undefined, boundary: 'start' | 'end') {
  if (!date) {
    return undefined;
  }

  const suffix = boundary === 'start' ? 'T00:00:00.000Z' : 'T23:59:59.999Z';
  return new Date(`${date}${suffix}`).toISOString();
}

export const auditService = {
  getLogs: (params?: {
    userId?: number;
    actionType?: string;
    serviceName?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    api.get<ApiResponse<PagedResponse<AuditLog>>>('/audit/logs', {
      params: params
        ? {
            ...params,
            from: toUtcDateBoundary(params.from, 'start'),
            to: toUtcDateBoundary(params.to, 'end'),
          }
        : undefined,
    }),
  getUserLogs: (userId: number, params?: { page?: number; size?: number }) =>
    api.get<ApiResponse<PagedResponse<AuditLog>>>(`/audit/logs/user/${userId}`, { params }),
  getOverviewKpis: () => api.get<ApiResponse<AdminKpi>>('/audit/analytics/overview'),
  getSessionTimeSeries: (params?: { from?: string; to?: string; groupBy?: string }) =>
    api.get<ApiResponse<TimeSeriesPoint[]>>('/audit/analytics/sessions', { params }),
  getRevenueTimeSeries: (params?: { from?: string; to?: string; groupBy?: string }) =>
    api.get<ApiResponse<TimeSeriesPoint[]>>('/audit/analytics/revenue', { params }),
  getTopSkills: (params?: { from?: string; to?: string; limit?: number }) =>
    api.get<ApiResponse<TopSkill[]>>('/audit/analytics/top-skills', { params }),
};
