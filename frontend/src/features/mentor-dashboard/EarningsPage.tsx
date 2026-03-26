import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format, parseISO, startOfMonth } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button, Card, Input, Modal } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import type { PaymentTransaction } from '../../services/paymentService';
import { paymentService } from '../../services/paymentService';

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

function createIdempotencyKey(prefix: string) {
  return globalThis.crypto?.randomUUID?.() ?? `${prefix}-${Date.now()}`;
}

function getPaymentTextClass(status?: string | null) {
  switch (status?.toUpperCase()) {
    case 'COMPLETED':
    case 'PAID':
      return 'text-emerald-600 dark:text-emerald-300';
    case 'PENDING':
    case 'INITIATED':
      return 'text-amber-600 dark:text-amber-300';
    case 'FAILED':
      return 'text-red-600 dark:text-red-300';
    default:
      return 'text-slate-600 dark:text-slate-300';
  }
}

export default function EarningsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { page, size, nextPage, prevPage } = usePagination(0, 10);
  const [isPayoutModalOpen, setIsPayoutModalOpen] = useState(false);
  const [payoutAmount, setPayoutAmount] = useState('');

  const earningsQuery = useQuery({
    queryKey: ['payments', 'mentor', 'earnings'],
    queryFn: async () => (await paymentService.getMyEarnings()).data.data,
  });

  const chartPaymentsQuery = useQuery({
    queryKey: ['payments', 'mentor', 'chart'],
    queryFn: async () => (await paymentService.getMyPayments({ page: 0, size: 100 })).data.content,
  });

  const transactionsQuery = useQuery({
    queryKey: ['payments', 'mentor', 'transactions', { page, size }],
    queryFn: async () => (await paymentService.getMyPayments({ page, size })).data,
  });

  const payoutMutation = useMutation({
    mutationFn: (amount: number) => paymentService.requestPayout(amount, createIdempotencyKey('payout')),
    onSuccess: async () => {
      toast.success(t('mentor.payout_success'));
      setIsPayoutModalOpen(false);
      setPayoutAmount('');
      await queryClient.invalidateQueries({ queryKey: ['payments', 'mentor'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const chartData = useMemo(() => {
    const monthlyTotals = new Map<string, number>();

    (chartPaymentsQuery.data ?? []).forEach((payment: PaymentTransaction) => {
      if (['FAILED', 'REFUNDED'].includes(payment.status.toUpperCase())) {
        return;
      }

      const monthKey = format(startOfMonth(parseISO(payment.createdAt)), 'MMM yyyy');
      monthlyTotals.set(monthKey, (monthlyTotals.get(monthKey) ?? 0) + payment.amount);
    });

    return Array.from(monthlyTotals.entries()).map(([month, amount]) => ({ month, amount }));
  }, [chartPaymentsQuery.data]);

  if (earningsQuery.isLoading || chartPaymentsQuery.isLoading || transactionsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (earningsQuery.isError || chartPaymentsQuery.isError || transactionsQuery.isError || !earningsQuery.data) {
    return (
      <Card className="text-red-500">
        {getApiErrorMessage(
          earningsQuery.error ?? chartPaymentsQuery.error ?? transactionsQuery.error,
          t('common.error'),
        )}
      </Card>
    );
  }

  const stats = [
    {
      label: t('mentor.total_earnings'),
      value: currencyFormatter.format(earningsQuery.data.totalEarnings),
    },
    {
      label: t('mentor.pending_amount'),
      value: currencyFormatter.format(earningsQuery.data.pendingAmount),
    },
    {
      label: t('mentor.available_payout'),
      value: currencyFormatter.format(earningsQuery.data.availableForPayout),
    },
    {
      label: t('mentor.paid_out'),
      value: currencyFormatter.format(earningsQuery.data.paidAmount),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('mentor.earnings_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentor.earnings_subtitle')}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((item) => (
          <Card key={item.label} className="space-y-3">
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400">{item.label}</p>
            <p className="text-3xl font-semibold text-slate-950 dark:text-white">{item.value}</p>
          </Card>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.3fr)_minmax(320px,0.7fr)]">
        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('mentor.earnings_chart')}
          </h2>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="mentorEarningsGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563eb" stopOpacity={0.35} />
                    <stop offset="95%" stopColor="#2563eb" stopOpacity={0.05} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.2} />
                <XAxis dataKey="month" />
                <YAxis tickFormatter={(value) => currencyFormatter.format(Number(value))} />
                <Tooltip
                  formatter={(value) =>
                    currencyFormatter.format(Number(Array.isArray(value) ? value[0] : value ?? 0))
                  }
                />
                <Area
                  type="monotone"
                  dataKey="amount"
                  stroke="#2563eb"
                  fill="url(#mentorEarningsGradient)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('mentor.request_payout')}
          </h2>
          <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentor.available_payout')}</p>
            <p className="mt-2 text-3xl font-semibold text-slate-950 dark:text-white">
              {currencyFormatter.format(earningsQuery.data.availableForPayout)}
            </p>
          </div>
          <Button onClick={() => setIsPayoutModalOpen(true)}>{t('mentor.request_payout')}</Button>
        </Card>
      </div>

      <Card className="space-y-4">
        <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
          {t('mentor.transaction_history')}
        </h2>
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-slate-500 dark:text-slate-400">
              <tr>
                <th className="pb-3 font-medium">{t('common.date')}</th>
                <th className="pb-3 font-medium">{t('mentor.session_id')}</th>
                <th className="pb-3 font-medium">{t('common.amount')}</th>
                <th className="pb-3 font-medium">{t('common.status')}</th>
              </tr>
            </thead>
            <tbody>
              {transactionsQuery.data?.content.map((transaction) => (
                <tr key={transaction.id} className="border-t border-slate-200/70 dark:border-slate-800">
                  <td className="py-3 text-slate-700 dark:text-slate-200">
                    {format(new Date(transaction.createdAt), 'dd MMM yyyy')}
                  </td>
                  <td className="py-3 text-slate-700 dark:text-slate-200">#{transaction.sessionId}</td>
                  <td className="py-3 text-slate-700 dark:text-slate-200">
                    {currencyFormatter.format(transaction.amount)}
                  </td>
                  <td className={`py-3 font-medium ${getPaymentTextClass(transaction.status)}`}>
                    {transaction.status}
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
          <Button variant="outline" onClick={nextPage} disabled={Boolean(transactionsQuery.data?.last)}>
            {t('common.next')}
          </Button>
        </div>
      </Card>

      <Modal
        isOpen={isPayoutModalOpen}
        title={t('mentor.request_payout')}
        onClose={() => setIsPayoutModalOpen(false)}
      >
        <div className="space-y-4">
          <Input
            type="number"
            min="1"
            max={earningsQuery.data.availableForPayout}
            label={t('mentor.payout_amount')}
            value={payoutAmount}
            onChange={(event) => setPayoutAmount(event.target.value)}
          />
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setIsPayoutModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              isLoading={payoutMutation.isPending}
              onClick={() => {
                const amount = Number(payoutAmount);
                if (!amount || amount <= 0 || amount > earningsQuery.data.availableForPayout) {
                  toast.error(t('common.error'));
                  return;
                }

                payoutMutation.mutate(amount);
              }}
            >
              {t('mentor.request_payout')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
