import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Badge, Button, Card, Input } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { adminUserService } from '../../services/adminUserService';

const roleOptions = ['ALL', 'LEARNER', 'MENTOR', 'ADMIN'] as const;
const statusOptions = ['ALL', 'ACTIVE', 'BANNED'] as const;

function getUserStatus(status?: string | null): { label: string; variant: 'success' | 'danger' | 'default' } {
  const normalizedStatus = status?.trim().toUpperCase();

  if (normalizedStatus === 'BANNED') {
    return { label: 'BANNED', variant: 'danger' };
  }

  if (normalizedStatus === 'ACTIVE') {
    return { label: 'ACTIVE', variant: 'success' };
  }

  if (status?.trim()) {
    return { label: status.trim(), variant: 'default' };
  }

  return { label: 'UNKNOWN', variant: 'default' };
}

export default function UserManagementPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { page, size, nextPage, prevPage, setPage } = usePagination(0, 10);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<(typeof roleOptions)[number]>('ALL');
  const [status, setStatus] = useState<(typeof statusOptions)[number]>('ALL');
  const deferredSearch = useDeferredValue(search.trim());

  useEffect(() => {
    setPage(0);
  }, [deferredSearch, role, setPage, status]);

  const usersQuery = useQuery({
    queryKey: ['admin', 'users', { deferredSearch, role, status, page, size }],
    queryFn: async () =>
      (
        await adminUserService.getAll({
          search: deferredSearch || undefined,
          role: role === 'ALL' ? undefined : role,
          status: status === 'ALL' ? undefined : status,
          page,
          size,
        })
      ).data,
  });

  const filteredUsers = useMemo(() => {
    const users = usersQuery.data?.content ?? [];
    if (!deferredSearch) {
      return users;
    }

    const normalizedSearch = deferredSearch.toLowerCase();
    return users.filter((user) =>
      [user.name, user.email].join(' ').toLowerCase().includes(normalizedSearch),
    );
  }, [deferredSearch, usersQuery.data?.content]);

  const updateUserStatusMutation = useMutation({
    mutationFn: ({ id, status: nextStatus }: { id: number; status: 'BANNED' | 'ACTIVE' }) =>
      nextStatus === 'BANNED' ? adminUserService.banUser(id) : adminUserService.unbanUser(id),
    onSuccess: async () => {
      toast.success(t('admin.user_status_updated'));
      await queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (usersQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (usersQuery.isError) {
    return <Card className="text-red-500">{getApiErrorMessage(usersQuery.error, t('common.error'))}</Card>;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('admin.user_management')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {t('admin.dashboard_subtitle')}
        </p>
      </div>

      <Card className="space-y-4">
        <div className="grid gap-4 lg:grid-cols-3">
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={t('admin.search_users')}
          />

          <label className="block space-y-2">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {t('admin.role_filter')}
            </span>
            <select
              value={role}
              onChange={(event) => setRole(event.target.value as (typeof roleOptions)[number])}
              className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
            >
              {roleOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

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
                <th className="pb-3 font-medium">{t('admin.name')}</th>
                <th className="pb-3 font-medium">{t('admin.email')}</th>
                <th className="pb-3 font-medium">{t('admin.roles')}</th>
                <th className="pb-3 font-medium">{t('common.status')}</th>
                <th className="pb-3 font-medium">{t('admin.joined')}</th>
                <th className="pb-3 font-medium">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.map((user) => {
                const userStatus = getUserStatus(user.status);
                const isBanned = userStatus.label === 'BANNED';

                return (
                  <tr key={user.id} className="border-t border-slate-200/70 dark:border-slate-800">
                    <td className="py-3 font-medium text-slate-900 dark:text-slate-100">{user.name}</td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">{user.email}</td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-2">
                        {(user.roles ?? []).map((userRole) => (
                          <Badge key={userRole} variant="info" className="normal-case tracking-normal">
                            {userRole}
                          </Badge>
                        ))}
                      </div>
                    </td>
                    <td className="py-3">
                      <Badge variant={userStatus.variant} className="normal-case tracking-normal">
                        {userStatus.label}
                      </Badge>
                    </td>
                    <td className="py-3 text-slate-700 dark:text-slate-200">
                      {format(new Date(user.createdAt), 'dd MMM yyyy')}
                    </td>
                    <td className="py-3">
                      <Button
                        size="sm"
                        variant={isBanned ? 'outline' : 'danger'}
                        isLoading={
                          updateUserStatusMutation.isPending &&
                          updateUserStatusMutation.variables?.id === user.id
                        }
                        onClick={() => {
                          const nextStatus = isBanned ? 'ACTIVE' : 'BANNED';
                          const confirmed = window.confirm(
                            nextStatus === 'BANNED' ? t('admin.ban_confirm') : t('admin.unban_confirm'),
                          );
                          if (!confirmed) {
                            return;
                          }

                          updateUserStatusMutation.mutate({ id: user.id, status: nextStatus });
                        }}
                      >
                        {isBanned ? t('admin.unban_user') : t('admin.ban_user')}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={prevPage} disabled={page === 0}>
            {t('common.previous')}
          </Button>
          <Button variant="outline" onClick={nextPage} disabled={Boolean(usersQuery.data?.last)}>
            {t('common.next')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
