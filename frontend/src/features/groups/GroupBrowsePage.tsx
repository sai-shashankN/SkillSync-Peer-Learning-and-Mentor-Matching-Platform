import { useDeferredValue, useState } from 'react';
import { Users } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Card, Input } from '../../components/ui';
import { usePagination } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import { groupService } from '../../services/groupService';
import { skillService } from '../../services/skillService';
import CreateGroupModal from './CreateGroupModal';

export default function GroupBrowsePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { page, size, setPage, nextPage, prevPage } = usePagination(0, 6);
  const [search, setSearch] = useState('');
  const [skillId, setSkillId] = useState<number | undefined>();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const deferredSearch = useDeferredValue(search);

  const skillsQuery = useQuery({
    queryKey: ['skills', 'groups'],
    queryFn: async () => (await skillService.getAll()).data.data,
  });

  const groupsQuery = useQuery({
    queryKey: ['groups', 'browse', { deferredSearch, skillId, page, size }],
    queryFn: async () =>
      (
        await groupService.search({
          search: deferredSearch || undefined,
          skillId,
          page,
          size,
        })
      ).data.data,
  });

  const myGroupsQuery = useQuery({
    queryKey: ['groups', 'mine'],
    queryFn: async () => (await groupService.getMyGroups()).data.data,
  });

  const skillMap = Object.fromEntries((skillsQuery.data ?? []).map((skill) => [skill.id, skill.name]));
  const joinedGroupIds = new Set((myGroupsQuery.data ?? []).map((group) => group.id));

  const joinMutation = useMutation({
    mutationFn: async (groupId: number) => groupService.join(groupId),
    onSuccess: async () => {
      toast.success(t('groups.join_success'));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['groups', 'browse'] }),
        queryClient.invalidateQueries({ queryKey: ['groups', 'mine'] }),
      ]);
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const createMutation = useMutation({
    mutationFn: groupService.create,
    onSuccess: async (response) => {
      toast.success(t('groups.create_success'));
      setIsModalOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['groups', 'browse'] }),
        queryClient.invalidateQueries({ queryKey: ['groups', 'mine'] }),
      ]);
      navigate(`/groups/${response.data.data.id}`);
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div className="space-y-2">
          <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
            {t('groups.browse_title')}
          </h1>
          <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">
            {t('groups.browse_subtitle')}
          </p>
        </div>

        <Button onClick={() => setIsModalOpen(true)}>{t('groups.create_group')}</Button>
      </div>

      <Card className="grid gap-4 md:grid-cols-[minmax(0,1fr)_240px]">
        <Input
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
          placeholder={t('groups.search_placeholder')}
        />
        <select
          value={skillId ?? ''}
          onChange={(event) => {
            setSkillId(event.target.value ? Number(event.target.value) : undefined);
            setPage(0);
          }}
          className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
        >
          <option value="">{t('mentors.all_skills')}</option>
          {skillsQuery.data?.map((skill) => (
            <option key={skill.id} value={skill.id}>
              {skill.name}
            </option>
          ))}
        </select>
      </Card>

      {groupsQuery.isLoading ? (
        <Card>{t('common.loading')}</Card>
      ) : groupsQuery.isError ? (
        <Card className="text-red-500">
          {getApiErrorMessage(groupsQuery.error, t('common.error'))}
        </Card>
      ) : groupsQuery.data?.content.length ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {groupsQuery.data.content.map((group) => {
            const isJoined = joinedGroupIds.has(group.id);
            const isFull = group.memberCount >= group.maxMembers;

            return (
              <Card key={group.id} className="flex h-full flex-col gap-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-semibold text-slate-950 dark:text-white">
                      {group.name}
                    </h2>
                    <p className="text-sm text-slate-500 dark:text-slate-400">{group.slug}</p>
                  </div>
                  {isJoined ? <Badge variant="success">{t('groups.joined')}</Badge> : null}
                </div>

                <p className="text-sm leading-7 text-slate-600 dark:text-slate-300">
                  {group.description}
                </p>

                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
                  <Users className="size-4 text-[var(--color-primary)]" />
                  <span>
                    {group.memberCount}/{group.maxMembers} {t('common.members')}
                  </span>
                </div>

                <div className="flex flex-wrap gap-2">
                  {group.skillIds.slice(0, 3).map((groupSkillId) => (
                    <Badge key={groupSkillId} variant="info" className="normal-case tracking-normal">
                      {skillMap[groupSkillId] ?? `#${groupSkillId}`}
                    </Badge>
                  ))}
                </div>

                <div className="mt-auto flex gap-3">
                  <Button variant="outline" className="flex-1" onClick={() => navigate(`/groups/${group.id}`)}>
                    {t('groups.open_group')}
                  </Button>
                  <Button
                    className="flex-1"
                    onClick={() => joinMutation.mutate(group.id)}
                    disabled={isJoined || isFull || joinMutation.isPending}
                  >
                    {isJoined ? t('groups.joined') : isFull ? t('groups.group_full') : t('common.join')}
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      ) : (
        <Card>{t('groups.no_groups')}</Card>
      )}

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={prevPage} disabled={page === 0}>
          {t('common.previous')}
        </Button>
        <Button variant="outline" onClick={nextPage} disabled={Boolean(groupsQuery.data?.last)}>
          {t('common.next')}
        </Button>
      </div>

      <CreateGroupModal
        isOpen={isModalOpen}
        isSubmitting={createMutation.isPending}
        skills={skillsQuery.data ?? []}
        onClose={() => setIsModalOpen(false)}
        onSubmit={async (values) => {
          await createMutation.mutateAsync(values);
        }}
      />
    </div>
  );
}
