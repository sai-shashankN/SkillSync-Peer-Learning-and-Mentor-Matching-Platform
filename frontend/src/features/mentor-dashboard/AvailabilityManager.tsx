import { useEffect, useMemo, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Badge, Button, Card, Input } from '../../components/ui';
import { getApiErrorMessage } from '../../lib/utils';
import type { AvailabilitySlot, SetAvailabilitySlot } from '../../services/mentorService';
import { mentorService } from '../../services/mentorService';

const dayKeys = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

const dayLabelKeys: Record<(typeof dayKeys)[number], string> = {
  MONDAY: 'mentor.monday',
  TUESDAY: 'mentor.tuesday',
  WEDNESDAY: 'mentor.wednesday',
  THURSDAY: 'mentor.thursday',
  FRIDAY: 'mentor.friday',
  SATURDAY: 'mentor.saturday',
  SUNDAY: 'mentor.sunday',
};

interface EditableSlot extends SetAvailabilitySlot {
  localId: string;
}

interface SlotDraft {
  startTime: string;
  endTime: string;
}

function createLocalId() {
  return globalThis.crypto?.randomUUID?.() ?? `slot-${Date.now()}-${Math.random()}`;
}

function toEditableSlots(slots: AvailabilitySlot[] | SetAvailabilitySlot[]): EditableSlot[] {
  return slots.map((slot) => ({
    dayOfWeek: slot.dayOfWeek,
    startTime: slot.startTime,
    endTime: slot.endTime,
    localId: createLocalId(),
  }));
}

export default function AvailabilityManager() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [slots, setSlots] = useState<EditableSlot[]>([]);
  const [drafts, setDrafts] = useState<Record<string, SlotDraft | null>>({});

  const profileQuery = useQuery({
    queryKey: ['mentor', 'me'],
    queryFn: async () => (await mentorService.getMyProfile()).data.data,
  });

  const availabilityQuery = useQuery({
    queryKey: ['mentor', 'availability', profileQuery.data?.id],
    enabled: Boolean(profileQuery.data?.id),
    queryFn: async () => (await mentorService.getAvailability(profileQuery.data!.id)).data.data,
  });

  useEffect(() => {
    if (!availabilityQuery.data) {
      return;
    }

    setSlots(toEditableSlots(availabilityQuery.data));
  }, [availabilityQuery.data]);

  const saveAvailabilityMutation = useMutation({
    mutationFn: (nextSlots: EditableSlot[]) =>
      mentorService.setAvailability(
        profileQuery.data!.id,
        nextSlots.map(({ dayOfWeek, startTime, endTime }) => ({ dayOfWeek, startTime, endTime })),
      ),
    onSuccess: async (response) => {
      toast.success(t('mentor.availability_saved'));
      setSlots(toEditableSlots(response.data.data));
      await queryClient.invalidateQueries({ queryKey: ['mentor', 'availability', profileQuery.data?.id] });
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const groupedSlots = useMemo(
    () =>
      dayKeys.reduce<Record<string, EditableSlot[]>>((accumulator, day) => {
        accumulator[day] = slots.filter((slot) => slot.dayOfWeek.toUpperCase() === day);
        return accumulator;
      }, {}),
    [slots],
  );

  if (profileQuery.isLoading || availabilityQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (profileQuery.isError || availabilityQuery.isError || !profileQuery.data) {
    return (
      <Card className="text-red-500">
        {getApiErrorMessage(profileQuery.error ?? availabilityQuery.error, t('common.error'))}
      </Card>
    );
  }

  const updateSlots = (nextSlots: EditableSlot[]) => {
    setSlots(nextSlots);
    saveAvailabilityMutation.mutate(nextSlots);
  };

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('mentor.availability_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {t('mentor.availability_subtitle')}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {dayKeys.map((day) => {
          const daySlots = groupedSlots[day];
          const draft = drafts[day];

          return (
            <Card key={day} className="space-y-4">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white">
                  {t(dayLabelKeys[day])}
                </h2>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setDrafts((current) => ({
                      ...current,
                      [day]: { startTime: '', endTime: '' },
                    }))
                  }
                >
                  {t('mentor.add_slot')}
                </Button>
              </div>

              <div className="flex flex-wrap gap-2">
                {daySlots.length ? (
                  daySlots.map((slot) => (
                    <Badge
                      key={slot.localId}
                      variant="info"
                      className="flex items-center gap-2 normal-case tracking-normal"
                    >
                      <span>
                        {slot.startTime} - {slot.endTime}
                      </span>
                      <button
                        type="button"
                        className="rounded-full bg-white/70 p-1 text-slate-700 dark:bg-slate-800 dark:text-slate-100"
                        aria-label={t('mentor.remove_slot')}
                        onClick={() => updateSlots(slots.filter((currentSlot) => currentSlot.localId !== slot.localId))}
                      >
                        <Trash2 className="size-3" />
                      </button>
                    </Badge>
                  ))
                ) : (
                  <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.no_results')}</p>
                )}
              </div>

              {draft ? (
                <div className="space-y-3 rounded-3xl bg-slate-50 p-4 dark:bg-slate-950/60">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Input
                      type="time"
                      label="Start Time"
                      value={draft.startTime}
                      onChange={(event) =>
                        setDrafts((current) => ({
                          ...current,
                          [day]: { ...draft, startTime: event.target.value },
                        }))
                      }
                    />
                    <Input
                      type="time"
                      label="End Time"
                      value={draft.endTime}
                      onChange={(event) =>
                        setDrafts((current) => ({
                          ...current,
                          [day]: { ...draft, endTime: event.target.value },
                        }))
                      }
                    />
                  </div>
                  <div className="flex flex-wrap gap-3">
                    <Button
                      size="sm"
                      isLoading={saveAvailabilityMutation.isPending}
                      onClick={() => {
                        if (!draft.startTime || !draft.endTime || draft.startTime >= draft.endTime) {
                          toast.error(t('common.error'));
                          return;
                        }

                        updateSlots([
                          ...slots,
                          {
                            dayOfWeek: day,
                            startTime: draft.startTime,
                            endTime: draft.endTime,
                            localId: createLocalId(),
                          },
                        ]);
                        setDrafts((current) => ({ ...current, [day]: null }));
                      }}
                    >
                      {t('mentor.save_availability')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setDrafts((current) => ({ ...current, [day]: null }))}
                    >
                      {t('common.cancel')}
                    </Button>
                  </div>
                </div>
              ) : null}
            </Card>
          );
        })}
      </div>
    </div>
  );
}
