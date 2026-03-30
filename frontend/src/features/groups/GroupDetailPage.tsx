import { useEffect, useRef, useState } from 'react';
import { MessageCircleMore, Send, Trash2, Users } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { useNavigate, useParams } from 'react-router-dom';
import { Badge, Button, Card } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import { useAuthStore } from '../../store/authStore';
import { groupService } from '../../services/groupService';
import { skillService } from '../../services/skillService';

function getSenderName(senderId: number, senderName?: string) {
  return senderName?.trim() ? senderName : `User #${senderId}`;
}

export default function GroupDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const { id } = useParams();
  const groupId = Number(id);
  const [message, setMessage] = useState('');
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const groupQuery = useQuery({
    queryKey: ['group', groupId],
    enabled: Number.isFinite(groupId),
    queryFn: async () => (await groupService.getById(groupId)).data.data,
  });

  const messagesQuery = useQuery({
    queryKey: ['group', groupId, 'messages'],
    enabled: Number.isFinite(groupId),
    queryFn: async () => (await groupService.getMessages(groupId, { page: 0, size: 100 })).data.data,
  });

  const skillsQuery = useQuery({
    queryKey: ['skills', 'group-detail'],
    queryFn: async () => (await skillService.getAll()).data.data,
  });

  const myGroupsQuery = useQuery({
    queryKey: ['groups', 'mine'],
    queryFn: async () => (await groupService.getMyGroups()).data.data,
  });

  const skillMap = Object.fromEntries((skillsQuery.data ?? []).map((skill) => [skill.id, skill.name]));
  const isMember = Boolean(myGroupsQuery.data?.some((group) => group.id === groupId));

  const sortedMessages = [...(messagesQuery.data?.content ?? [])].sort(
    (first, second) => new Date(first.createdAt).getTime() - new Date(second.createdAt).getTime(),
  );

  const memberMap = new Map<number, string>();
  if (groupQuery.data) {
    memberMap.set(groupQuery.data.creatorId, `${t('groups.creator')} #${groupQuery.data.creatorId}`);
  }
  sortedMessages.forEach((item) => {
    memberMap.set(item.senderId, getSenderName(item.senderId, item.senderName));
  });
  const participants = Array.from(memberMap.entries()).map(([memberId, memberName]) => ({
    memberId,
    memberName,
  }));

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [sortedMessages.length]);

  const sendMutation = useMutation({
    mutationFn: async () => groupService.sendMessage(groupId, message.trim()),
    onSuccess: async () => {
      setMessage('');
      await queryClient.invalidateQueries({ queryKey: ['group', groupId, 'messages'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (messageId: number) => groupService.deleteMessage(groupId, messageId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['group', groupId, 'messages'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const joinMutation = useMutation({
    mutationFn: async () => groupService.join(groupId),
    onSuccess: async () => {
      toast.success(t('groups.join_success'));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['groups', 'mine'] }),
        queryClient.invalidateQueries({ queryKey: ['group', groupId] }),
      ]);
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const leaveMutation = useMutation({
    mutationFn: async () => groupService.leave(groupId),
    onSuccess: async () => {
      toast.success(t('groups.leave_success'));
      await queryClient.invalidateQueries({ queryKey: ['groups', 'mine'] });
      navigate('/groups');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (groupQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (groupQuery.isError || !groupQuery.data) {
    return <Card className="text-red-500">{t('common.error')}</Card>;
  }

  const group = groupQuery.data;

  return (
    <div className="space-y-6">
      <Card className="space-y-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">{group.name}</h1>
              <Badge variant="info" className="normal-case tracking-normal">
                {group.slug}
              </Badge>
            </div>
            <p className="max-w-3xl text-sm leading-7 text-slate-600 dark:text-slate-300">
              {group.description}
            </p>
            <div className="flex flex-wrap gap-2">
              {group.skillIds.map((skillId) => (
                <Badge key={skillId} variant="info" className="normal-case tracking-normal">
                  {skillMap[skillId] ?? `#${skillId}`}
                </Badge>
              ))}
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            {!isMember ? (
              <Button isLoading={joinMutation.isPending} onClick={() => joinMutation.mutate()}>
                {t('common.join')}
              </Button>
            ) : null}
            <Button
              variant="danger"
              onClick={() => leaveMutation.mutate()}
              isLoading={leaveMutation.isPending}
              disabled={!isMember}
            >
              {t('common.leave')}
            </Button>
          </div>
        </div>

        <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
          <Users className="size-4 text-[var(--color-primary)]" />
          <span>
            {group.memberCount}/{group.maxMembers} {t('common.members')}
          </span>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
        <Card className="space-y-4">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
            {t('groups.active_members')}
          </h2>
          <div className="space-y-3">
            {participants.length ? (
              participants.map((participant) => (
                <div
                  key={participant.memberId}
                  className="rounded-3xl border border-slate-200/70 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:text-slate-200"
                >
                  {participant.memberName}
                </div>
              ))
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.no_results')}</p>
            )}
          </div>
        </Card>

        <Card className="flex h-[70vh] flex-col gap-4">
          <div className="flex items-center gap-3">
            <MessageCircleMore className="size-5 text-[var(--color-primary)]" />
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {t('groups.messages')}
            </h2>
          </div>

          <div className="flex-1 space-y-3 overflow-y-auto rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
            {sortedMessages.length ? (
              sortedMessages.map((item) => (
                <div
                  key={item.id}
                  className="rounded-3xl bg-slate-50 p-4 dark:bg-slate-900"
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="font-semibold text-slate-950 dark:text-white">
                        {getSenderName(item.senderId, item.senderName)}
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {format(new Date(item.createdAt), 'dd MMM yyyy, hh:mm a')}
                      </p>
                    </div>
                    {item.senderId === user?.id && !item.isDeleted ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => deleteMutation.mutate(item.id)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    ) : null}
                  </div>
                  <p className="mt-3 text-sm leading-7 text-slate-700 dark:text-slate-300">
                    {item.isDeleted ? t('common.delete') : item.content}
                  </p>
                </div>
              ))
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.no_results')}</p>
            )}
            <div ref={bottomRef} />
          </div>

          {isMember ? (
            <div className="flex gap-3">
              <textarea
                rows={2}
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                placeholder={t('groups.message_placeholder')}
                className="flex-1 rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
              />
              <Button
                className="self-end"
                isLoading={sendMutation.isPending}
                onClick={() => {
                  if (!message.trim()) {
                    toast.error(t('errors.required'));
                    return;
                  }
                  sendMutation.mutate();
                }}
              >
                <Send className="size-4" />
                {t('groups.send_message')}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.join')}</p>
          )}
        </Card>
      </div>
    </div>
  );
}
