import { Fragment, useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { Badge, Button, Card, Input } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { auditService } from '../../services/auditService';

const actionTypes = ['ALL', 'LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'PAYMENT', 'APPROVE', 'REJECT', 'BAN'] as const;
const serviceNames = [
  'ALL',
  'auth-service',
  'user-service',
  'mentor-service',
  'session-service',
  'payment-service',
  'review-service',
  'group-service',
  'notification-service',
  'audit-service',
] as const;

function getActionVariant(actionType: string): 'success' | 'warning' | 'danger' | 'default' {
  switch (actionType.toUpperCase()) {
    case 'DELETE':
    case 'BAN':
    case 'REJECT':
      return 'danger';
    case 'UPDATE':
    case 'PAYMENT':
      return 'warning';
    case 'CREATE':
    case 'APPROVE':
    case 'LOGIN':
      return 'success';
    default:
      return 'default';
  }
}

export default function AuditLogPage() {
  const { t } = useTranslation();
  const { page, size, nextPage, prevPage, setPage } = usePagination(0, 10);
  const [expandedLogId, setExpandedLogId] = useState<number | null>(null);
  const [userId, setUserId] = useState('');
  const [actionType, setActionType] = useState<(typeof actionTypes)[number]>('ALL');
  const [serviceName, setServiceName] = useState<(typeof serviceNames)[number]>('ALL');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  useEffect(() => {
    setPage(0);
  }, [actionType, from, serviceName, setPage, to, userId]);

  const logsQuery = useQuery({
    queryKey: ['admin', 'audit-logs', { userId, actionType, serviceName, from, to, page, size }],
    queryFn: async () =>
      (
        await auditService.getLogs({
          userId: userId ? Number(userId) : undefined,
          actionType: actionType === 'ALL' ? undefined : actionType,
          serviceName: serviceName === 'ALL' ? undefined : serviceName,
          from: from || undefined,
          to: to || undefined,
          page,
          size,
        })
      ).data.data,
  });

  if (logsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (logsQuery.isError) {
    return <Card className="text-red-500">{getApiErrorMessage(logsQuery.error, t('common.error'))}</Card>;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">{t('admin.audit_logs')}</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('admin.dashboard_subtitle')}</p>
      </div>

      <Card className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          <Input
            label={t('admin.user_id')}
            value={userId}
            onChange={(event) => setUserId(event.target.value)}
            placeholder="123"
          />
          <label className="block space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {t('admin.action_type')}
            </span>
            <select
              value={actionType}
              onChange={(event) => setActionType(event.target.value as (typeof actionTypes)[number])}
              className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
            >
              {actionTypes.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {t('admin.service_name')}
            </span>
            <select
              value={serviceName}
              onChange={(event) => setServiceName(event.target.value as (typeof serviceNames)[number])}
              className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
            >
              {serviceNames.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <Input type="date" label={t('admin.from')} value={from} onChange={(event) => setFrom(event.target.value)} />
          <Input type="date" label={t('admin.to')} value={to} onChange={(event) => setTo(event.target.value)} />
        </div>
      </Card>

      <Card className="space-y-4">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-slate-500 dark:text-slate-400">
              <tr>
                <th className="pb-3 font-medium">{t('admin.timestamp')}</th>
                <th className="pb-3 font-medium">{t('admin.user_id')}</th>
                <th className="pb-3 font-medium">{t('admin.action_type')}</th>
                <th className="pb-3 font-medium">{t('admin.service_name')}</th>
                <th className="pb-3 font-medium">{t('admin.entity_type')}</th>
                <th className="pb-3 font-medium">{t('admin.entity_id')}</th>
                <th className="pb-3 font-medium">{t('admin.ip_address')}</th>
              </tr>
            </thead>
            <tbody>
              {logsQuery.data?.content.map((log) => (
                <Fragment key={log.id}>
                  <tr
                    className="cursor-pointer border-t border-slate-200/70 dark:border-slate-800"
                    onClick={() => setExpandedLogId((current) => (current === log.id ? null : log.id))}
                  >
                    <td className="py-3 text-slate-700 dark:text-slate-200">
                      {format(new Date(log.createdAt), 'dd MMM yyyy, hh:mm a')}
                    </td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">{log.userId ?? '-'}</td>
                    <td className="py-3">
                      <Badge
                        variant={getActionVariant(log.actionType)}
                        className="normal-case tracking-normal"
                      >
                        {log.actionType}
                      </Badge>
                    </td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">{log.serviceName}</td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">{log.entityType}</td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">{log.entityId ?? '-'}</td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">{log.ipAddress ?? '-'}</td>
                  </tr>
                  {expandedLogId === log.id ? (
                    <tr className="border-t border-slate-200/70 bg-slate-50/70 dark:border-slate-800 dark:bg-slate-950/60">
                      <td colSpan={7} className="p-4">
                        <p className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-200">
                          {t('admin.metadata')}
                        </p>
                        <pre className="overflow-x-auto rounded-3xl bg-slate-950 p-4 text-xs text-slate-100">
                          {JSON.stringify(log.metadata, null, 2)}
                        </pre>
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={prevPage} disabled={page === 0}>
            {t('common.previous')}
          </Button>
          <Button variant="outline" onClick={nextPage} disabled={Boolean(logsQuery.data?.last)}>
            {t('common.next')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
