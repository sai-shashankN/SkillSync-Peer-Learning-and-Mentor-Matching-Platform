import api from './api';
import type { ApiResponse } from '../types';

export interface Skill {
  id: number;
  name: string;
  categoryId: number;
  description: string;
}

export interface Category {
  id: number;
  name: string;
  description: string;
}

export const skillService = {
  getAll: (params?: { categoryId?: number; search?: string }) =>
    api.get<ApiResponse<Skill[]>>('/skills', { params }),
  getById: (id: number) => api.get<ApiResponse<Skill>>(`/skills/${id}`),
  getCategories: () => api.get<ApiResponse<Category[]>>('/skills/categories'),
};
