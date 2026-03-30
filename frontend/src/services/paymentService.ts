import type { AxiosResponse } from 'axios';
import api from './api';
import type { ApiResponse, PagedResponse } from '../types';

export interface InitiatePaymentRequest {
  sessionId: number;
}

export interface PaymentInitiateResponse {
  orderId: string;
  amount: number;
  currency: string;
  clientId: string;
}

export interface VerifyPaymentRequest {
  sessionId: number;
  orderId: string;
}

export interface EarningsResponse {
  totalEarnings: number;
  pendingAmount: number;
  paidAmount: number;
  availableForPayout: number;
}

export interface PaymentTransaction {
  id: number;
  sessionId: number;
  amount: number;
  status: string;
  provider: string;
  providerOrderId: string | null;
  providerPaymentId: string | null;
  createdAt: string;
  updatedAt: string;
}

interface RawEarningsResponse {
  totalEarnings?: number | string | null;
  pendingAmount?: number | string | null;
  paidAmount?: number | string | null;
  availableForPayout?: number | string | null;
}

interface RawPaymentTransaction {
  id: number;
  sessionId: number;
  amount?: number | string | null;
  status?: string | null;
  provider?: string | null;
  providerOrderId?: string | null;
  providerPaymentId?: string | null;
  createdAt: string;
  updatedAt: string;
}

function toNumber(value: number | string | null | undefined) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : 0;
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
}

function toUtcDateBoundary(date: string | undefined, boundary: 'start' | 'end') {
  if (!date) {
    return undefined;
  }

  const suffix = boundary === 'start' ? 'T00:00:00.000Z' : 'T23:59:59.999Z';
  return new Date(`${date}${suffix}`).toISOString();
}

function normalizePaymentStatus(status?: string | null) {
  return status?.trim() || 'PENDING';
}

function normalizeEarningsResponse(response: RawEarningsResponse): EarningsResponse {
  return {
    totalEarnings: toNumber(response.totalEarnings),
    pendingAmount: toNumber(response.pendingAmount),
    paidAmount: toNumber(response.paidAmount),
    availableForPayout: toNumber(response.availableForPayout),
  };
}

function normalizePaymentTransaction(transaction: RawPaymentTransaction): PaymentTransaction {
  return {
    id: transaction.id,
    sessionId: transaction.sessionId,
    amount: toNumber(transaction.amount),
    status: normalizePaymentStatus(transaction.status),
    provider: transaction.provider?.trim() || 'UNKNOWN',
    providerOrderId: transaction.providerOrderId ?? null,
    providerPaymentId: transaction.providerPaymentId ?? null,
    createdAt: transaction.createdAt,
    updatedAt: transaction.updatedAt,
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

export const paymentService = {
  initiate: (data: InitiatePaymentRequest, idempotencyKey: string) =>
    api.post<ApiResponse<PaymentInitiateResponse>>('/payments/initiate', data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  verify: (data: VerifyPaymentRequest, idempotencyKey: string) =>
    api.post<ApiResponse<unknown>>('/payments/verify', data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  getMyPayments: (params?: { status?: string; page?: number; size?: number }) =>
    api
      .get<PagedResponse<RawPaymentTransaction>>('/payments/me', { params })
      .then((response) => mapPagedResponse(response, normalizePaymentTransaction)),
  getMyEarnings: () =>
    api
      .get<ApiResponse<RawEarningsResponse>>('/payments/mentor/me/earnings')
      .then((response) => mapApiResponse(response, normalizeEarningsResponse)),
  requestPayout: (amount: number, idempotencyKey: string) =>
    api.post<ApiResponse<unknown>>(
      '/payments/mentor/me/payout',
      { amount },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),
  getAllTransactions: (params?: {
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    api
      .get<PagedResponse<RawPaymentTransaction>>('/payments/transactions', {
        params: params
          ? {
              ...params,
              from: toUtcDateBoundary(params.from, 'start'),
              to: toUtcDateBoundary(params.to, 'end'),
            }
          : undefined,
      })
      .then((response) => mapPagedResponse(response, normalizePaymentTransaction)),
  refundPayment: (id: number, reason: string, idempotencyKey: string) =>
    api.post<ApiResponse<unknown>>(
      `/payments/${id}/refund`,
      { reason },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),
};
