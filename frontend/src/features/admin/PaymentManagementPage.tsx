import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Badge, Button, Card, Input, Modal } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { paymentService } from '../../services/paymentService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

const statusOptions = ['ALL', 'INITIATED', 'COMPLETED', 'REFUNDED', 'FAILED'] as const;

function createIdempotencyKey(prefix: string) {
  return globalThis.crypto?.randomUUID?.() ?? `${prefix}-${Date.now()}`;
}

function getPaymentBadgeVariant(status: string): 'success' | 'warning' | 'danger' | 'default' {
  switch (status.toUpperCase()) {
    case 'COMPLETED':
      return 'success';
    case 'INITIATED':
      return 'warning';
    case 'FAILED':
      return 'danger';
    default:
      return 'default';
  }
}

export default function PaymentManagementPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { page, size, nextPage, prevPage, setPage } = usePagination(0, 10);
  const [status, setStatus] = useState<(typeof statusOptions)[number]>('ALL');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [selectedPaymentId, setSelectedPaymentId] = useState<number | null>(null);
  const [refundReason, setRefundReason] = useState('');

  useEffect(() => {
    setPage(0);
  }, [from, setPage, status, to]);

  const paymentsQuery = useQuery({
    queryKey: ['admin', 'payments', { status, from, to, page, size }],
    queryFn: async () =>
      (
        await paymentService.getAllTransactions({
          status: status === 'ALL' ? undefined : status,
          from: from || undefined,
          to: to || undefined,
          page,
          size,
        })
      ).data,
  });

  const refundMutation = useMutation({
    mutationFn: ({ paymentId, reason }: { paymentId: number; reason: string }) =>
      paymentService.refundPayment(paymentId, reason, createIdempotencyKey('refund')),
    onSuccess: async () => {
      toast.success(t('admin.refund_success'));
      setSelectedPaymentId(null);
      setRefundReason('');
      await queryClient.invalidateQueries({ queryKey: ['admin', 'payments'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (paymentsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (paymentsQuery.isError) {
    return <Card className="text-red-500">{getApiErrorMessage(paymentsQuery.error, t('common.error'))}</Card>;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('admin.payment_management')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('admin.dashboard_subtitle')}</p>
      </div>

      <Card className="space-y-4">
        <div className="grid gap-4 md:grid-cols-3">
          <Input type="date" label={t('admin.from')} value={from} onChange={(event) => setFrom(event.target.value)} />
          <Input type="date" label={t('admin.to')} value={to} onChange={(event) => setTo(event.target.value)} />
          <label className="block space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {t('admin.status_filter')}
            </span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value as (typeof statusOptions)[number])}
              className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
            >
              {statusOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Card>

      <Card className="space-y-4">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-slate-500 dark:text-slate-400">
              <tr>
                <th className="pb-3 font-medium">{t('common.date')}</th>
                <th className="pb-3 font-medium">{t('admin.session_id')}</th>
                <th className="pb-3 font-medium">{t('common.amount')}</th>
                <th className="pb-3 font-medium">{t('common.status')}</th>
                <th className="pb-3 font-medium">{t('admin.razorpay_order_id')}</th>
                <th className="pb-3 font-medium">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {paymentsQuery.data?.content.map((payment) => (
                <tr key={payment.id} className="border-t border-slate-200/70 dark:border-slate-800">
                  <td className="py-3 text-slate-700 dark:text-slate-200">
                    {format(new Date(payment.createdAt), 'dd MMM yyyy')}
                  </td>
                  <td className="py-3 text-slate-700 dark:text-slate-200">#{payment.sessionId}</td>
                  <td className="py-3 text-slate-700 dark:text-slate-200">
                    {currencyFormatter.format(payment.amount)}
                  </td>
                  <td className="py-3">
                    <Badge
                      variant={getPaymentBadgeVariant(payment.status)}
                      className="normal-case tracking-normal"
                    >
                      {payment.status}
                    </Badge>
                  </td>
                  <td className="py-3 text-slate-700 dark:text-slate-200">
                    {payment.razorpayOrderId ?? '-'}
                  </td>
                  <td className="py-3">
                    {payment.status.toUpperCase() === 'COMPLETED' ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setSelectedPaymentId(payment.id);
                          setRefundReason('');
                        }}
                      >
                        {t('admin.refund')}
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={prevPage} disabled={page === 0}>
            {t('common.previous')}
          </Button>
          <Button variant="outline" onClick={nextPage} disabled={Boolean(paymentsQuery.data?.last)}>
            {t('common.next')}
          </Button>
        </div>
      </Card>

      <Modal
        isOpen={selectedPaymentId !== null}
        title={t('admin.refund')}
        onClose={() => setSelectedPaymentId(null)}
      >
        <div className="space-y-4">
          <Input
            label={t('admin.refund_reason')}
            value={refundReason}
            onChange={(event) => setRefundReason(event.target.value)}
            placeholder={t('admin.refund_reason')}
          />
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setSelectedPaymentId(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              isLoading={refundMutation.isPending}
              onClick={() => {
                if (!refundReason.trim() || selectedPaymentId === null) {
                  toast.error(t('errors.required'));
                  return;
                }

                refundMutation.mutate({
                  paymentId: selectedPaymentId,
                  reason: refundReason.trim(),
                });
              }}
            >
              {t('admin.refund_confirm')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
