import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse, UserInfo } from '../types';
import { useAuthStore } from '../store/authStore';

interface RefreshResponse {
  accessToken: string;
  user: UserInfo;
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

let refreshRequest: Promise<RefreshResponse> | null = null;

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined;

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/login') &&
      !originalRequest.url?.includes('/auth/register') &&
      !originalRequest.url?.includes('/auth/oauth2/') &&
      !originalRequest.url?.includes('/auth/refresh')
    ) {
      originalRequest._retry = true;

      try {
        refreshRequest ??= axios
          .post<ApiResponse<RefreshResponse>>('/api/auth/refresh', null, {
            withCredentials: true,
          })
          .then((response) => response.data.data)
          .finally(() => {
            refreshRequest = null;
          });

        const refreshedSession = await refreshRequest;
        const { setAccessToken, setUser } = useAuthStore.getState();

        setAccessToken(refreshedSession.accessToken);
        setUser(refreshedSession.user);
        originalRequest.headers.Authorization = `Bearer ${refreshedSession.accessToken}`;

        return api(originalRequest);
      } catch {
        useAuthStore.getState().logout();
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  },
);

export default api;
