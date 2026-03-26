import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Input, Modal } from '../../components/ui';
import type { CreateGroupRequest } from '../../services/groupService';
import type { Skill } from '../../services/skillService';

interface CreateGroupModalProps {
  isOpen: boolean;
  isSubmitting: boolean;
  skills: Skill[];
  onClose: () => void;
  onSubmit: (values: CreateGroupRequest) => Promise<void>;
}

const initialValues: CreateGroupRequest = {
  name: '',
  description: '',
  maxMembers: 6,
  skillIds: [],
};

export default function CreateGroupModal({
  isOpen,
  isSubmitting,
  skills,
  onClose,
  onSubmit,
}: CreateGroupModalProps) {
  const { t } = useTranslation();
  const [values, setValues] = useState<CreateGroupRequest>(initialValues);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) {
      setValues(initialValues);
      setError('');
    }
  }, [isOpen]);

  const toggleSkill = (skillId: number) => {
    setValues((current) => ({
      ...current,
      skillIds: current.skillIds.includes(skillId)
        ? current.skillIds.filter((item) => item !== skillId)
        : [...current.skillIds, skillId],
    }));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!values.name.trim() || !values.description.trim() || values.skillIds.length === 0) {
      setError(t('errors.required'));
      return;
    }

    setError('');
    await onSubmit({
      ...values,
      name: values.name.trim(),
      description: values.description.trim(),
    });
  };

  return (
    <Modal isOpen={isOpen} title={t('groups.create_group')} onClose={onClose}>
      <form className="space-y-4" onSubmit={handleSubmit}>
        <Input
          label={t('groups.group_name')}
          value={values.name}
          onChange={(event) => setValues((current) => ({ ...current, name: event.target.value }))}
        />

        <label className="block space-y-2">
          <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
            {t('groups.description')}
          </span>
          <textarea
            rows={4}
            value={values.description}
            onChange={(event) =>
              setValues((current) => ({ ...current, description: event.target.value }))
            }
            className="w-full rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
          />
        </label>

        <Input
          type="number"
          min={2}
          max={50}
          label={t('groups.max_members')}
          value={values.maxMembers}
          onChange={(event) =>
            setValues((current) => ({ ...current, maxMembers: Number(event.target.value) }))
          }
        />

        <div className="space-y-2">
          <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
            {t('groups.select_skills')}
          </p>
          <div className="grid max-h-48 gap-2 overflow-y-auto rounded-3xl border border-slate-200/70 p-3 dark:border-slate-800">
            {skills.map((skill) => (
              <label
                key={skill.id}
                className="flex items-center gap-3 rounded-2xl px-3 py-2 hover:bg-slate-50 dark:hover:bg-slate-900"
              >
                <input
                  type="checkbox"
                  checked={values.skillIds.includes(skill.id)}
                  onChange={() => toggleSkill(skill.id)}
                  className="accent-[var(--color-primary)]"
                />
                <span className="text-sm text-slate-700 dark:text-slate-200">{skill.name}</span>
              </label>
            ))}
          </div>
        </div>

        {error ? <p className="text-sm text-red-500">{error}</p> : null}

        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={onClose} type="button">
            {t('common.cancel')}
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            {t('groups.create_group')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
