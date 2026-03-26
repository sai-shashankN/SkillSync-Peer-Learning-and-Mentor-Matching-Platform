import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface CreateReviewRequest {
  mentorId: number;
  sessionId: number;
  rating: number;
  comment: string;
}

export interface Review {
  id: number;
  mentorId: number;
  learnerId: number;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface UserBadge {
  badgeId: number;
  badgeName: string;
  badgeDescription: string;
  badgeIcon: string;
  earnedAt: string;
}

export const reviewService = {
  submit: (data: CreateReviewRequest) => api.post<ApiResponse<Review>>('/reviews', data),
  getMentorReviews: (mentorId: number, params?: { page?: number; size?: number }) =>
    api.get<ApiResponse<PagedResponse<Review>>>(`/reviews/mentor/${mentorId}`, { params }),
  getMentorAverage: (mentorId: number) =>
    api.get<ApiResponse<{ averageRating: number; totalReviews: number }>>(
      `/reviews/mentor/${mentorId}/average`,
    ),
  getMyBadges: () => api.get<ApiResponse<UserBadge[]>>('/reviews/badges/me'),
  getAvailableBadges: () => api.get<ApiResponse<unknown[]>>('/reviews/badges/available'),
};
