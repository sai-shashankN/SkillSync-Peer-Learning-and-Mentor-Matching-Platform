import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

const STATUS_MAP: Record<string, string> = {
  PENDING: 'PAID',
  UPCOMING: 'ACCEPTED',
  CONFIRMED: 'ACCEPTED',
};

function mapStatus(status?: string): string | undefined {
  if (!status) {
    return undefined;
  }

  return STATUS_MAP[status] ?? status;
}

export interface CreateHoldRequest {
  mentorId: number;
  skillId: number;
  startAt: string;
  endAt: string;
}

export interface CreateSessionRequest {
  holdId: number;
  topic: string;
  notes?: string;
  learnerTimezone?: string;
}

export interface SessionSummary {
  id: number;
  mentorId: number;
  learnerId: number;
  skillId: number;
  topic: string;
  status: string;
  startAt: string;
  endAt: string;
  amount: number;
  bookingReference: string;
}

export interface SessionDetail extends SessionSummary {
  notes: string;
  zoomLink: string | null;
  calendarEventId: string | null;
  statusReason: string | null;
}

export interface FeedbackRequest {
  rating: number;
  comment: string;
}

export interface Feedback {
  id: number;
  sessionId: number;
  userId: number;
  rating: number;
  comment: string;
  createdAt: string;
}

export const sessionService = {
  createHold: (data: CreateHoldRequest, idempotencyKey: string) =>
    api.post<ApiResponse<unknown>>('/sessions/holds', data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  createSession: (data: CreateSessionRequest) =>
    api.post<ApiResponse<SessionSummary>>('/sessions', data),
  getMySessions: (params?: { status?: string; role?: string; page?: number; size?: number }) =>
    api.get<PagedResponse<SessionSummary>>('/sessions/me', {
      params: params ? { ...params, status: mapStatus(params.status) } : undefined,
    }),
  getById: (id: number) => api.get<ApiResponse<SessionDetail>>(`/sessions/${id}`),
  acceptSession: (id: number) => api.put<ApiResponse<SessionSummary>>(`/sessions/${id}/accept`),
  rejectSession: (id: number, reason: string) =>
    api.put<ApiResponse<SessionSummary>>(`/sessions/${id}/reject`, { reason }),
  cancel: (id: number, reason: string) =>
    api.put<ApiResponse<unknown>>(`/sessions/${id}/cancel`, { reason }),
  complete: (id: number) => api.put<ApiResponse<unknown>>(`/sessions/${id}/complete`),
  submitFeedback: (id: number, data: FeedbackRequest) =>
    api.post<ApiResponse<Feedback>>(`/sessions/${id}/feedback`, data),
  getFeedback: (id: number) => api.get<ApiResponse<Feedback[]>>(`/sessions/${id}/feedback`),
  getAdminSessions: (params?: {
    status?: string;
    mentorId?: number;
    learnerId?: number;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    api.get<PagedResponse<SessionSummary>>('/sessions/admin', {
      params: params ? { ...params, status: mapStatus(params.status) } : undefined,
    }),
};
