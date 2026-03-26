import { useEffect, useState } from 'react';
import { Copy, MoonStar, Palette, UploadCloud } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Badge, Button, Card, Input } from '../../components/ui';
import { getApiErrorMessage, getInitials } from '../../lib/utils';
import { reviewService } from '../../services/reviewService';
import { skillService } from '../../services/skillService';
import { userService } from '../../services/userService';
import { useAuthStore } from '../../store/authStore';
import { useThemeStore } from '../../store/themeStore';

interface ProfileDraft {
  name: string;
  bio: string;
  phone: string;
}

export default function ProfilePage() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const isDark = useThemeStore((state) => state.isDark);
  const toggleTheme = useThemeStore((state) => state.toggle);
  const [isEditing, setIsEditing] = useState(false);
  const [selectedSkillId, setSelectedSkillId] = useState('');
  const [draft, setDraft] = useState<ProfileDraft>({ name: '', bio: '', phone: '' });

  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: async () => (await userService.getProfile()).data.data,
  });

  const mySkillsQuery = useQuery({
    queryKey: ['profile', 'skills'],
    queryFn: async () => (await userService.getMySkills()).data.data,
  });

  const allSkillsQuery = useQuery({
    queryKey: ['skills', 'profile'],
    queryFn: async () => (await skillService.getAll()).data.data,
  });

  const badgesQuery = useQuery({
    queryKey: ['profile', 'badges'],
    queryFn: async () => (await reviewService.getMyBadges()).data.data,
  });

  const referralQuery = useQuery({
    queryKey: ['profile', 'referral'],
    queryFn: async () => (await userService.getReferralCode()).data.data,
  });

  useEffect(() => {
    if (!profileQuery.data || isEditing) {
      return;
    }

    setDraft({
      name: profileQuery.data.name,
      bio: profileQuery.data.bio ?? '',
      phone: profileQuery.data.phone ?? '',
    });
  }, [isEditing, profileQuery.data]);

  const updateProfileMutation = useMutation({
    mutationFn: async () =>
      userService.updateProfile({
        name: draft.name.trim(),
        bio: draft.bio.trim(),
        phone: draft.phone.trim(),
      }),
    onSuccess: async (response) => {
      toast.success(t('profile.profile_saved'));
      const updated = response.data.data;
      if (user) {
        setUser({
          ...user,
          name: updated.name,
          email: updated.email,
          roles: updated.roles,
        });
      }
      setIsEditing(false);
      await queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const uploadAvatarMutation = useMutation({
    mutationFn: async (file: File) => userService.uploadAvatar(file),
    onSuccess: async () => {
      toast.success(t('profile.avatar_updated'));
      await queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const addSkillMutation = useMutation({
    mutationFn: async () => userService.addSkill(Number(selectedSkillId)),
    onSuccess: async () => {
      toast.success(t('profile.skill_added'));
      setSelectedSkillId('');
      await queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const removeSkillMutation = useMutation({
    mutationFn: async (skillId: number) => userService.removeSkill(skillId),
    onSuccess: async () => {
      toast.success(t('profile.skill_removed'));
      await queryClient.invalidateQueries({ queryKey: ['profile', 'skills'] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  if (profileQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (profileQuery.isError || !profileQuery.data) {
    return <Card className="text-red-500">{t('common.error')}</Card>;
  }

  const profile = profileQuery.data;
  const assignedSkillIds = new Set((mySkillsQuery.data ?? []).map((skill) => skill.skillId));
  const availableSkills = (allSkillsQuery.data ?? []).filter((skill) => !assignedSkillIds.has(skill.id));
  const activeLanguage = i18n.resolvedLanguage?.startsWith('hi') ? 'hi' : 'en';

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">{t('profile.title')}</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('profile.subtitle')}</p>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        <div className="space-y-6">
          <Card className="space-y-5">
            <div className="flex flex-col gap-5 md:flex-row md:items-start">
              <div className="flex items-center gap-4">
                {profile.avatarUrl ? (
                  <img
                    src={profile.avatarUrl}
                    alt={profile.name}
                    className="size-24 rounded-[2rem] object-cover"
                  />
                ) : (
                  <div className="flex size-24 items-center justify-center rounded-[2rem] bg-slate-950 text-2xl font-semibold text-white dark:bg-slate-100 dark:text-slate-950">
                    {getInitials(profile.name)}
                  </div>
                )}
                <label className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-blue-300 hover:text-[var(--color-primary)] dark:border-slate-700 dark:text-slate-200 dark:hover:border-slate-500">
                  <UploadCloud className="size-4" />
                  {t('profile.upload_avatar')}
                  <input
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) {
                        uploadAvatarMutation.mutate(file);
                      }
                    }}
                  />
                </label>
              </div>

              <div className="flex-1 space-y-2">
                <h2 className="text-xl font-semibold text-slate-950 dark:text-white">{profile.name}</h2>
                <p className="text-sm text-slate-500 dark:text-slate-400">{profile.email}</p>
                <div className="flex flex-wrap gap-2">
                  {profile.roles.map((role) => (
                    <Badge key={role} variant="info" className="normal-case tracking-normal">
                      {role}
                    </Badge>
                  ))}
                </div>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {format(new Date(profile.createdAt), 'dd MMM yyyy')}
                </p>
              </div>

              <Button variant="outline" onClick={() => setIsEditing((current) => !current)}>
                {isEditing ? t('common.cancel') : t('profile.edit_profile')}
              </Button>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <Input
                label={t('auth.name')}
                value={draft.name}
                onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
                disabled={!isEditing}
              />
              <Input label={t('auth.email')} value={profile.email} disabled />
              <Input
                label={t('profile.phone')}
                value={draft.phone}
                onChange={(event) => setDraft((current) => ({ ...current, phone: event.target.value }))}
                disabled={!isEditing}
              />
            </div>

            <label className="block space-y-2">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                {t('profile.bio')}
              </span>
              <textarea
                rows={5}
                value={draft.bio}
                onChange={(event) => setDraft((current) => ({ ...current, bio: event.target.value }))}
                disabled={!isEditing}
                className="w-full rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-50 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:disabled:bg-slate-900 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
              />
            </label>

            {isEditing ? (
              <Button isLoading={updateProfileMutation.isPending} onClick={() => updateProfileMutation.mutate()}>
                {t('profile.save_profile')}
              </Button>
            ) : null}
          </Card>

          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">{t('profile.skills')}</h2>
            <div className="flex flex-wrap gap-2">
              {mySkillsQuery.data?.length ? (
                mySkillsQuery.data.map((skill) => (
                  <Badge
                    key={skill.skillId}
                    variant="info"
                    className="flex items-center gap-2 normal-case tracking-normal"
                  >
                    <span>{skill.skillName}</span>
                    <button
                      type="button"
                      onClick={() => removeSkillMutation.mutate(skill.skillId)}
                      className="rounded-full bg-white/60 px-2 py-0.5 text-[10px] font-semibold text-slate-700 dark:bg-slate-700 dark:text-slate-100"
                    >
                      {t('common.remove')}
                    </button>
                  </Badge>
                ))
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">{t('profile.no_skills')}</p>
              )}
            </div>

            <div className="flex flex-col gap-3 md:flex-row">
              <select
                value={selectedSkillId}
                onChange={(event) => setSelectedSkillId(event.target.value)}
                className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
              >
                <option value="">{t('profile.add_skill')}</option>
                {availableSkills.map((skill) => (
                  <option key={skill.id} value={skill.id}>
                    {skill.name}
                  </option>
                ))}
              </select>
              <Button
                onClick={() => {
                  if (!selectedSkillId) {
                    toast.error(t('errors.required'));
                    return;
                  }
                  addSkillMutation.mutate();
                }}
                isLoading={addSkillMutation.isPending}
              >
                {t('profile.add_skill')}
              </Button>
            </div>
          </Card>
        </div>

        <div className="space-y-6">
          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">{t('profile.badges')}</h2>
            {badgesQuery.data?.length ? (
              <div className="space-y-3">
                {badgesQuery.data.map((badge) => (
                  <div
                    key={badge.badgeId}
                    className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <p className="font-semibold text-slate-950 dark:text-white">{badge.badgeName}</p>
                      <Badge variant="success" className="normal-case tracking-normal">
                        {badge.badgeIcon}
                      </Badge>
                    </div>
                    <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
                      {badge.badgeDescription}
                    </p>
                    <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
                      {format(new Date(badge.earnedAt), 'dd MMM yyyy')}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('profile.no_badges')}</p>
            )}
          </Card>

          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
              {t('profile.preferences')}
            </h2>
            <div className="flex items-center justify-between rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
              <div className="flex items-center gap-3">
                <MoonStar className="size-5 text-[var(--color-primary)]" />
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {t('profile.theme')}
                </span>
              </div>
              <Button variant="outline" onClick={toggleTheme}>
                {isDark ? t('profile.theme_dark') : t('profile.theme_light')}
              </Button>
            </div>
            <div className="flex items-center justify-between rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
              <div className="flex items-center gap-3">
                <Palette className="size-5 text-[var(--color-primary)]" />
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {t('common.language')}
                </span>
              </div>
              <select
                value={activeLanguage}
                onChange={(event) => i18n.changeLanguage(event.target.value)}
                className="rounded-2xl border border-slate-200 bg-white/90 px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
              >
                <option value="en">EN</option>
                <option value="hi">HI</option>
              </select>
            </div>
          </Card>

          <Card className="space-y-4">
            <h2 className="text-xl font-semibold text-slate-950 dark:text-white">{t('profile.referral')}</h2>
            <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('profile.referral_code')}</p>
              <div className="mt-3 flex items-center justify-between gap-3">
                <code className="rounded-2xl bg-slate-100 px-4 py-2 text-sm font-semibold text-slate-900 dark:bg-slate-900 dark:text-slate-100">
                  {referralQuery.data?.referralCode ?? profile.referralCode}
                </code>
                <Button
                  variant="outline"
                  onClick={async () => {
                    const code = referralQuery.data?.referralCode ?? profile.referralCode ?? '';
                    await navigator.clipboard.writeText(code);
                    toast.success(t('common.copied'));
                  }}
                >
                  <Copy className="size-4" />
                  {t('profile.copy_code')}
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
