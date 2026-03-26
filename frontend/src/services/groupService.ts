import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface GroupSummary {
  id: number;
  name: string;
  slug: string;
  description: string;
  memberCount: number;
  maxMembers: number;
  skillIds: number[];
  createdAt: string;
}

export interface GroupDetail extends GroupSummary {
  creatorId: number;
}

export interface GroupMessage {
  id: number;
  groupId: number;
  senderId: number;
  senderName: string;
  content: string;
  createdAt: string;
  isDeleted: boolean;
}

export interface CreateGroupRequest {
  name: string;
  description: string;
  maxMembers: number;
  skillIds: number[];
}

export const groupService = {
  search: (params?: { search?: string; skillId?: number; page?: number; size?: number }) =>
    api.get<ApiResponse<PagedResponse<GroupSummary>>>('/groups', { params }),
  getMyGroups: () => api.get<ApiResponse<GroupSummary[]>>('/groups/me'),
  getById: (id: number) => api.get<ApiResponse<GroupDetail>>(`/groups/${id}`),
  create: (data: CreateGroupRequest) => api.post<ApiResponse<GroupSummary>>('/groups', data),
  join: (id: number) => api.post<ApiResponse<unknown>>(`/groups/${id}/join`),
  leave: (id: number) => api.post<ApiResponse<unknown>>(`/groups/${id}/leave`),
  getMessages: (id: number, params?: { page?: number; size?: number }) =>
    api.get<ApiResponse<PagedResponse<GroupMessage>>>(`/groups/${id}/messages`, { params }),
  sendMessage: (id: number, content: string) =>
    api.post<ApiResponse<GroupMessage>>(`/groups/${id}/messages`, { content }),
  deleteMessage: (groupId: number, messageId: number) =>
    api.delete<ApiResponse<void>>(`/groups/${groupId}/messages/${messageId}`),
};
