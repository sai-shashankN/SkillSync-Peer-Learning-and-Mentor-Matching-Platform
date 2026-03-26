import type { ApiResponse, UserInfo } from '../types';
import api from './api';

export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
  termsAccepted: boolean;
  privacyPolicyVersion: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  user: UserInfo;
}

export const authService = {
  register: (payload: RegisterPayload) =>
    api.post<ApiResponse<AuthResponse>>('/auth/register', payload),
  login: (payload: LoginPayload) => api.post<ApiResponse<AuthResponse>>('/auth/login', payload),
  googleLogin: (idToken: string) =>
    api.post<ApiResponse<AuthResponse>>('/auth/oauth2/google', { idToken }),
  githubLogin: (code: string) => api.post<ApiResponse<AuthResponse>>('/auth/oauth2/github', { code }),
  refresh: () => api.post<ApiResponse<AuthResponse>>('/auth/refresh'),
  logout: () => api.post('/auth/logout'),
};
