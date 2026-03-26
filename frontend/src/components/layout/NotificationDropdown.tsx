import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { formatDistanceToNow } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { Button, Card } from '../ui';
import { getApiErrorMessage } from '../../lib/utils';
import { notificationService } from '../../services';

interface NotificationDropdownProps {
  onClose: () => void;
}

export default function NotificationDropdown({ onClose }: NotificationDropdownProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: async () => (await notificationService.getAll({ page: 0, size: 10 })).data.data.content,
  });

  const markAsReadMutation = useMutation({
    mutationFn: (notificationId: number) => notificationService.markAsRead(notificationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markAllAsReadMutation = useMutation({
    mutationFn: () => notificationService.markAllAsRead(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return (
    <Card className="absolute right-0 top-full z-40 mt-3 w-[min(24rem,calc(100vw-2rem))] p-0">
      <div className="flex items-center justify-between border-b border-slate-200/70 px-4 py-3 dark:border-slate-800">
        <h2 className="text-sm font-semibold text-slate-950 dark:text-white">
          {t('admin.notifications_title')}
        </h2>
        <Button
          variant="ghost"
          size="sm"
          isLoading={markAllAsReadMutation.isPending}
          onClick={() => markAllAsReadMutation.mutate()}
        >
          {t('admin.mark_all_read')}
        </Button>
      </div>

      <div className="max-h-96 overflow-y-auto p-2">
        {notificationsQuery.isLoading ? <p className="p-3 text-sm">{t('common.loading')}</p> : null}
        {notificationsQuery.isError ? (
          <p className="p-3 text-sm text-red-500">
            {getApiErrorMessage(notificationsQuery.error, t('common.error'))}
          </p>
        ) : null}
        {!notificationsQuery.isLoading && !notificationsQuery.isError && !notificationsQuery.data?.length ? (
          <p className="p-3 text-sm text-slate-500 dark:text-slate-400">
            {t('admin.no_notifications')}
          </p>
        ) : null}

        <div className="space-y-2">
          {notificationsQuery.data?.map((notification) => (
            <button
              key={notification.id}
              type="button"
              className={`w-full rounded-2xl border p-3 text-left transition hover:bg-slate-50 dark:hover:bg-slate-950/60 ${
                notification.isRead
                  ? 'border-transparent'
                  : 'border-blue-200 border-l-4 dark:border-blue-900'
              }`}
              onClick={async () => {
                if (!notification.isRead) {
                  await markAsReadMutation.mutateAsync(notification.id);
                }
                onClose();
              }}
            >
              <div className="flex items-start justify-between gap-3">
                <p className="font-semibold text-slate-950 dark:text-white">{notification.title}</p>
                <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
                  {formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })}
                </span>
              </div>
              <p className="mt-1 truncate text-sm text-slate-600 dark:text-slate-300">
                {notification.message}
              </p>
            </button>
          ))}
        </div>
      </div>
    </Card>
  );
}
