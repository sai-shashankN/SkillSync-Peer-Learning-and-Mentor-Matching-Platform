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
  isActive?: boolean;
}

interface RawAvailabilitySlot {
  id: number;
  dayOfWeek: number | string;
  startTime: string;
  endTime: string;
  isActive?: boolean | null;
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
  availability?: RawAvailabilitySlot[] | null;
}

const weekdayNames = [
  'SUNDAY',
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
] as const;

const weekdayToIndex = Object.fromEntries(
  weekdayNames.map((day, index) => [day, index]),
) as Record<(typeof weekdayNames)[number], number>;

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

function normalizeDayOfWeek(dayOfWeek: number | string | null | undefined) {
  if (typeof dayOfWeek === 'number') {
    return weekdayNames[dayOfWeek] ?? weekdayNames[0];
  }

  if (typeof dayOfWeek === 'string') {
    const trimmed = dayOfWeek.trim();
    if (!trimmed) {
      return weekdayNames[0];
    }

    const upper = trimmed.toUpperCase() as (typeof weekdayNames)[number];
    if (upper in weekdayToIndex) {
      return upper;
    }

    const numeric = Number(trimmed);
    if (Number.isInteger(numeric)) {
      return weekdayNames[numeric] ?? weekdayNames[0];
    }
  }

  return weekdayNames[0];
}

function serializeDayOfWeek(dayOfWeek: string) {
  return weekdayToIndex[normalizeDayOfWeek(dayOfWeek)];
}

function normalizeAvailabilitySlot(slot: RawAvailabilitySlot): AvailabilitySlot {
  return {
    id: slot.id,
    dayOfWeek: normalizeDayOfWeek(slot.dayOfWeek),
    startTime: slot.startTime,
    endTime: slot.endTime,
    isActive: slot.isActive ?? undefined,
  };
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
  const availability = (mentor.availability ?? []).map(normalizeAvailabilitySlot);
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
    api
      .get<ApiResponse<RawAvailabilitySlot[]>>(`/mentors/${id}/availability`)
      .then((response) => mapApiResponse(response, (slots) => slots.map(normalizeAvailabilitySlot))),
  setAvailability: (id: number, slots: SetAvailabilitySlot[]) =>
    api
      .put<ApiResponse<RawAvailabilitySlot[]>>(`/mentors/${id}/availability`, {
        slots: slots.map((slot) => ({
          ...slot,
          dayOfWeek: serializeDayOfWeek(slot.dayOfWeek),
        })),
      })
      .then((response) => mapApiResponse(response, (items) => items.map(normalizeAvailabilitySlot))),
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
