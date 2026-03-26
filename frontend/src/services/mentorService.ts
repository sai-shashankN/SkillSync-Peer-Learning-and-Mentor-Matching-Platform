import type { AxiosResponse } from 'axios';
import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface MentorSummary {
  id: number;
  userId: number;
  name: string;
  avatarUrl: string | null;
  headline: string;
  bio?: string;
  experience?: string;
  experienceYears?: number | null;
  rating: number;
  totalSessions: number;
  totalReviews: number;
  hourlyRate: number;
  skillIds: number[];
  skills: string[];
  isAvailable: boolean;
  status?: string;
  appliedAt?: string;
  createdAt?: string;
}

export interface MentorDetail extends MentorSummary {
  bio: string;
  experience: string;
  languages: string[];
  availability: AvailabilitySlot[];
}

export interface UpdateMentorRequest {
  headline?: string;
  bio?: string;
  experienceYears?: number;
  hourlyRate?: number;
}

export interface SetAvailabilitySlot {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}

export interface AvailabilitySlot {
  id: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}

interface RawMentorSummary {
  id: number;
  userId: number;
  userName?: string | null;
  headline?: string | null;
  bio?: string | null;
  experienceYears?: number | null;
  hourlyRate?: number | string | null;
  avgRating?: number | string | null;
  totalSessions?: number | null;
  totalReviews?: number | null;
  status?: string | null;
  skillIds?: number[] | null;
  createdAt?: string | null;
}

interface RawMentorDetail extends RawMentorSummary {
  availability?: AvailabilitySlot[] | null;
}

function toNumber(value: number | string | null | undefined) {
  if (typeof value === 'number') {
    return value;
  }

  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
}

function formatExperienceYears(experienceYears: number | null | undefined) {
  if (typeof experienceYears !== 'number') {
    return '';
  }

  return `${experienceYears} year${experienceYears === 1 ? '' : 's'}`;
}

function normalizeMentorSummary(mentor: RawMentorSummary): MentorSummary {
  const experienceYears = mentor.experienceYears ?? null;

  return {
    id: mentor.id,
    userId: mentor.userId,
    name: mentor.userName?.trim() || `Mentor #${mentor.userId ?? mentor.id}`,
    avatarUrl: null,
    headline: mentor.headline ?? '',
    bio: mentor.bio ?? undefined,
    experience: formatExperienceYears(experienceYears),
    experienceYears,
    rating: toNumber(mentor.avgRating),
    totalSessions: mentor.totalSessions ?? 0,
    totalReviews: mentor.totalReviews ?? 0,
    hourlyRate: toNumber(mentor.hourlyRate),
    skillIds: mentor.skillIds ?? [],
    skills: [],
    isAvailable: false,
    status: mentor.status ?? undefined,
    appliedAt: mentor.createdAt ?? undefined,
    createdAt: mentor.createdAt ?? undefined,
  };
}

function normalizeMentorDetail(mentor: RawMentorDetail): MentorDetail {
  const availability = mentor.availability ?? [];
  const normalized = normalizeMentorSummary(mentor);

  return {
    ...normalized,
    bio: mentor.bio ?? '',
    experience: normalized.experience || '',
    languages: [],
    availability,
    isAvailable: availability.length > 0,
  };
}

function mapApiResponse<TRaw, TMapped>(
  response: AxiosResponse<ApiResponse<TRaw>>,
  mapper: (value: TRaw) => TMapped,
): AxiosResponse<ApiResponse<TMapped>> {
  return {
    ...response,
    data: {
      ...response.data,
      data: mapper(response.data.data),
    },
  };
}

function mapPagedResponse<TRaw, TMapped>(
  response: AxiosResponse<PagedResponse<TRaw>>,
  mapper: (value: TRaw) => TMapped,
): AxiosResponse<PagedResponse<TMapped>> {
  return {
    ...response,
    data: {
      ...response.data,
      content: response.data.content.map(mapper),
    },
  };
}

export const mentorService = {
  search: (params: {
    skillId?: number;
    minRating?: number;
    maxPrice?: number;
    status?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) =>
    api
      .get<PagedResponse<RawMentorSummary>>('/mentors', { params })
      .then((response) => mapPagedResponse(response, normalizeMentorSummary)),
  listAdmin: (params?: { status?: string; page?: number; size?: number }) =>
    api
      .get<PagedResponse<RawMentorSummary>>('/admin/mentors', { params })
      .then((response) => mapPagedResponse(response, normalizeMentorSummary)),
  getById: (id: number) =>
    api
      .get<ApiResponse<RawMentorDetail>>(`/mentors/${id}`)
      .then((response) => mapApiResponse(response, normalizeMentorDetail)),
  getMyProfile: () =>
    api
      .get<ApiResponse<RawMentorDetail>>('/mentors/me')
      .then((response) => mapApiResponse(response, normalizeMentorDetail)),
  updateProfile: (id: number, data: UpdateMentorRequest) =>
    api
      .put<ApiResponse<RawMentorSummary>>(`/mentors/${id}`, data)
      .then((response) => mapApiResponse(response, normalizeMentorSummary)),
  updateSkills: (id: number, data: { skillIds: number[] }) =>
    api
      .put<ApiResponse<RawMentorSummary>>(`/mentors/${id}/skills`, data)
      .then((response) => mapApiResponse(response, normalizeMentorSummary)),
  getAvailability: (id: number) =>
    api.get<ApiResponse<AvailabilitySlot[]>>(`/mentors/${id}/availability`),
  setAvailability: (id: number, slots: SetAvailabilitySlot[]) =>
    api.put<ApiResponse<AvailabilitySlot[]>>(`/mentors/${id}/availability`, { slots }),
  joinWaitlist: (id: number) => api.post<ApiResponse<unknown>>(`/mentors/${id}/waitlist`),
  approveMentor: (id: number) =>
    api
      .post<ApiResponse<RawMentorSummary>>(`/mentors/${id}/approve`)
      .then((response) => mapApiResponse(response, normalizeMentorSummary)),
  rejectMentor: (id: number, reason: string) =>
    api
      .post<ApiResponse<RawMentorSummary>>(`/mentors/${id}/reject`, { reason })
      .then((response) => mapApiResponse(response, normalizeMentorSummary)),
  banMentor: (id: number) =>
    api
      .post<ApiResponse<RawMentorSummary>>(`/mentors/${id}/ban`)
      .then((response) => mapApiResponse(response, normalizeMentorSummary)),
};
