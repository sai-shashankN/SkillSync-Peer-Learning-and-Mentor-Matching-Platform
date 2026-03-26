import api from './api';
import type { ApiResponse } from '../types';

export interface UserProfile {
  id: number;
  name: string;
  email: string;
  avatarUrl: string | null;
  bio: string | null;
  phone: string | null;
  roles: string[];
  referralCode?: string;
  createdAt: string;
}

export interface UpdateProfileRequest {
  name?: string;
  bio?: string;
  phone?: string;
}

export interface UserSkill {
  skillId: number;
  skillName: string;
}

export const userService = {
  getProfile: () => api.get<ApiResponse<UserProfile>>('/users/me'),
  updateProfile: (data: UpdateProfileRequest) => api.put<ApiResponse<UserProfile>>('/users/me', data),
  uploadAvatar: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);

    return api.post<ApiResponse<{ url: string }>>('/users/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  getMySkills: () => api.get<ApiResponse<UserSkill[]>>('/users/me/skills'),
  addSkill: (skillId: number) => api.post<ApiResponse<UserSkill>>('/users/me/skills', { skillId }),
  removeSkill: (skillId: number) => api.delete<ApiResponse<void>>(`/users/me/skills/${skillId}`),
  getReferralCode: () =>
    api.get<ApiResponse<{ referralCode: string }>>('/users/me/referral-code'),
  applyReferral: (code: string) =>
    api.post<ApiResponse<unknown>>('/users/apply-referral', { referralCode: code }),
};
