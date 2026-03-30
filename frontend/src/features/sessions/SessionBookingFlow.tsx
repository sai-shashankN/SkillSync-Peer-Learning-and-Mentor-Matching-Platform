import { useEffect, useRef, useState } from 'react';
import { addDays, format, set } from 'date-fns';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { useNavigate, useParams } from 'react-router-dom';
import { Badge, Button, Card, Input } from '../../components/ui';
import { usePayPal } from '../../hooks';
import { getApiErrorMessage } from '../../lib/utils';
import type { AvailabilitySlot } from '../../services/mentorService';
import { mentorService } from '../../services/mentorService';
import { paymentService } from '../../services/paymentService';
import type { SessionSummary } from '../../services/sessionService';
import { sessionService } from '../../services/sessionService';
import { skillService } from '../../services/skillService';

const weekdayToIndex: Record<string, number> = {
  SUNDAY: 0,
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 3,
  THURSDAY: 4,
  FRIDAY: 5,
  SATURDAY: 6,
};

function applyTime(date: Date, time: string) {
  const [hours, minutes = '0', seconds = '0'] = time.split(':');
  return set(date, {
    hours: Number(hours),
    minutes: Number(minutes),
    seconds: Number(seconds),
    milliseconds: 0,
  });
}

function extractHoldId(payload: unknown) {
  if (typeof payload === 'number') {
    return payload;
  }

  if (typeof payload === 'string' && !Number.isNaN(Number(payload))) {
    return Number(payload);
  }

  if (typeof payload === 'object' && payload !== null) {
    if ('holdId' in payload && typeof payload.holdId === 'number') {
      return payload.holdId;
    }

    if ('id' in payload && typeof payload.id === 'number') {
      return payload.id;
    }
  }

  throw new Error('Unable to read hold identifier from the API response.');
}

function getDateOptions(slots: AvailabilitySlot[]) {
  const results = new Map<string, Date>();

  for (let offset = 0; offset < 14; offset += 1) {
    const date = addDays(new Date(), offset);
    const slotAvailable = slots.some((slot) => weekdayToIndex[slot.dayOfWeek.toUpperCase()] === date.getDay());

    if (slotAvailable) {
      results.set(format(date, 'yyyy-MM-dd'), date);
    }
  }

  return Array.from(results.entries()).map(([key, value]) => ({ key, value }));
}

function getSlotsForDate(slots: AvailabilitySlot[], selectedDate: Date | null) {
  if (!selectedDate) {
    return [];
  }

  return slots
    .filter((slot) => weekdayToIndex[slot.dayOfWeek.toUpperCase()] === selectedDate.getDay())
    .map((slot) => ({
      ...slot,
      startAt: applyTime(selectedDate, slot.startTime),
      endAt: applyTime(selectedDate, slot.endTime),
    }));
}

function createIdempotencyKey(prefix: string) {
  return globalThis.crypto?.randomUUID?.() ?? `${prefix}-${Date.now()}`;
}

export default function SessionBookingFlow() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { renderButtons } = usePayPal();
  const { id } = useParams();
  const mentorId = Number(id);
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedDateKey, setSelectedDateKey] = useState('');
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [selectedSkillId, setSelectedSkillId] = useState('');
  const [topic, setTopic] = useState('');
  const [notes, setNotes] = useState('');
  const [sessionSummary, setSessionSummary] = useState<SessionSummary | null>(null);
  const [paymentInitIdempotencyKey, setPaymentInitIdempotencyKey] = useState<string | null>(null);
  const paypalButtonsRef = useRef<HTMLDivElement | null>(null);

  const mentorQuery = useQuery({
    queryKey: ['mentor', mentorId],
    enabled: Number.isFinite(mentorId),
    queryFn: async () => (await mentorService.getById(mentorId)).data.data,
  });

  const availabilityQuery = useQuery({
    queryKey: ['mentor', mentorId, 'availability'],
    enabled: Number.isFinite(mentorId),
    queryFn: async () => (await mentorService.getAvailability(mentorId)).data.data,
  });

  const skillsQuery = useQuery({
    queryKey: ['skills', 'booking', mentorId],
    queryFn: async () => (await skillService.getAll()).data.data,
  });

  const dateOptions = getDateOptions(availabilityQuery.data ?? []);
  const selectedDate =
    dateOptions.find((item) => item.key === selectedDateKey)?.value ?? null;
  const slotOptions = getSlotsForDate(availabilityQuery.data ?? [], selectedDate);

  const selectedSlot = slotOptions.find((slot) => slot.id === selectedSlotId) ?? null;
  const mentorSkillOptions =
    skillsQuery.data?.filter((skill) => mentorQuery.data?.skillIds.includes(skill.id)) ?? [];
  const skillOptions = mentorSkillOptions.length ? mentorSkillOptions : skillsQuery.data ?? [];

  const prepareBookingMutation = useMutation({
    mutationFn: async () => {
      if (!selectedSlot || !selectedSkillId || !topic.trim()) {
        throw new Error(t('errors.required'));
      }

      const holdResponse = await sessionService.createHold(
        {
          mentorId,
          skillId: Number(selectedSkillId),
          startAt: selectedSlot.startAt.toISOString(),
          endAt: selectedSlot.endAt.toISOString(),
        },
        `hold-${crypto.randomUUID()}`,
      );

      const holdId = extractHoldId(holdResponse.data.data);
      const sessionResponse = await sessionService.createSession({
        holdId,
        topic: topic.trim(),
        notes: notes.trim() || undefined,
        learnerTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      });

      return sessionResponse.data.data;
    },
    onSuccess: (session) => {
      setSessionSummary(session);
      setPaymentInitIdempotencyKey(createIdempotencyKey('payment-init'));
      setCurrentStep(3);
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('common.error')));
    },
  });

  const paymentInitQuery = useQuery({
    queryKey: ['payment-init', sessionSummary?.id, paymentInitIdempotencyKey],
    enabled:
      currentStep === 3 && sessionSummary !== null && paymentInitIdempotencyKey !== null,
    retry: false,
    queryFn: async () => {
      if (!sessionSummary || !paymentInitIdempotencyKey) {
        throw new Error(t('common.error'));
      }

      return (
        await paymentService.initiate(
          { sessionId: sessionSummary.id },
          paymentInitIdempotencyKey,
        )
      ).data.data;
    },
  });

  const capturePaymentMutation = useMutation({
    mutationFn: async (orderId: string) => {
      if (!sessionSummary) {
        throw new Error(t('common.error'));
      }

      await paymentService.verify(
        {
          sessionId: sessionSummary.id,
          orderId,
        },
        createIdempotencyKey('payment-verify'),
      );
    },
    onSuccess: async () => {
      toast.success(t('sessions.confirmation_title'));
      setCurrentStep(4);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['sessions', 'history'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard', 'learner', 'upcoming-sessions'] }),
      ]);
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, t('sessions.payment_failed')));
    },
  });

  useEffect(() => {
    const container = paypalButtonsRef.current;
    if (!container || !sessionSummary || currentStep !== 3 || !paymentInitQuery.data) {
      return;
    }

    let isActive = true;
    let cleanup: (() => Promise<void>) | undefined;

    void renderButtons(container, {
      clientId: paymentInitQuery.data.clientId,
      currency: paymentInitQuery.data.currency,
      orderId: paymentInitQuery.data.orderId,
      onApprove: async (orderId) => {
        await capturePaymentMutation.mutateAsync(orderId);
      },
      onCancel: () => {
        if (isActive) {
          toast.error(t('sessions.payment_cancelled'));
        }
      },
      onError: (error) => {
        if (isActive) {
          toast.error(getApiErrorMessage(error, t('sessions.payment_failed')));
        }
      },
    })
      .then((dispose) => {
        cleanup = dispose;
      })
      .catch((error) => {
        if (isActive) {
          toast.error(getApiErrorMessage(error, t('sessions.payment_failed')));
        }
      });

    return () => {
      isActive = false;
      if (cleanup) {
        void cleanup();
      }
    };
  }, [
    currentStep,
    paymentInitQuery.data?.clientId,
    paymentInitQuery.data?.currency,
    paymentInitQuery.data?.orderId,
    renderButtons,
    sessionSummary?.id,
    t,
  ]);

  const stepLabels = [
    t('sessions.step_date'),
    t('sessions.step_time'),
    t('sessions.step_details'),
    t('sessions.step_payment'),
    t('sessions.step_confirmation'),
  ];

  if (mentorQuery.isLoading || availabilityQuery.isLoading || skillsQuery.isLoading) {
    return <Card>{t('common.loading')}</Card>;
  }

  if (mentorQuery.isError || !mentorQuery.data) {
    return <Card className="text-red-500">{t('common.error')}</Card>;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">
          {t('sessions.booking_title')}
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('sessions.booking_subtitle')}</p>
      </div>

      <Card className="space-y-6">
        <div className="grid gap-3 md:grid-cols-5">
          {stepLabels.map((label, index) => (
            <div
              key={label}
              className={`rounded-3xl border px-4 py-3 text-sm font-medium ${
                index === currentStep
                  ? 'border-blue-200 bg-blue-50 text-[var(--color-primary)] dark:border-blue-900 dark:bg-blue-950/40'
                  : index < currentStep
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-950/60 dark:bg-emerald-950/20 dark:text-emerald-300'
                    : 'border-slate-200 text-slate-500 dark:border-slate-800 dark:text-slate-400'
              }`}
            >
              {label}
            </div>
          ))}
        </div>

        <div className="rounded-3xl border border-slate-200/70 p-5 dark:border-slate-800">
          {currentStep === 0 ? (
            <div className="space-y-4">
              <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
                {t('sessions.choose_date')}
              </h2>
              {dateOptions.length ? (
                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  {dateOptions.map((option) => (
                    <button
                      key={option.key}
                      type="button"
                      onClick={() => {
                        setSelectedDateKey(option.key);
                        setSelectedSlotId(null);
                      }}
                      className={`rounded-3xl border px-4 py-4 text-left transition ${
                        selectedDateKey === option.key
                          ? 'border-blue-200 bg-blue-50 text-[var(--color-primary)] dark:border-blue-900 dark:bg-blue-950/40'
                          : 'border-slate-200 hover:border-blue-200 dark:border-slate-800 dark:hover:border-slate-700'
                      }`}
                    >
                      <p className="font-semibold">{format(option.value, 'EEE')}</p>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {format(option.value, 'dd MMM yyyy')}
                      </p>
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentors.no_availability')}</p>
              )}
            </div>
          ) : null}

          {currentStep === 1 ? (
            <div className="space-y-4">
              <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
                {t('sessions.choose_time')}
              </h2>
              {slotOptions.length ? (
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {slotOptions.map((slot) => (
                    <button
                      key={slot.id}
                      type="button"
                      onClick={() => setSelectedSlotId(slot.id)}
                      className={`rounded-3xl border px-4 py-4 text-left transition ${
                        selectedSlotId === slot.id
                          ? 'border-blue-200 bg-blue-50 text-[var(--color-primary)] dark:border-blue-900 dark:bg-blue-950/40'
                          : 'border-slate-200 hover:border-blue-200 dark:border-slate-800 dark:hover:border-slate-700'
                      }`}
                    >
                      <p className="font-semibold">{format(slot.startAt, 'hh:mm a')}</p>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {format(slot.endAt, 'hh:mm a')}
                      </p>
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">{t('mentors.no_availability')}</p>
              )}
            </div>
          ) : null}

          {currentStep === 2 ? (
            <div className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <label className="block space-y-2">
                  <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                    {t('sessions.select_skill')}
                  </span>
                  <select
                    value={selectedSkillId}
                    onChange={(event) => setSelectedSkillId(event.target.value)}
                    className="w-full rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
                  >
                    <option value="">{t('sessions.select_skill')}</option>
                    {skillOptions.map((skill) => (
                      <option key={skill.id} value={skill.id}>
                        {skill.name}
                      </option>
                    ))}
                  </select>
                </label>

                <Input
                  label={t('sessions.timezone_label')}
                  value={Intl.DateTimeFormat().resolvedOptions().timeZone}
                  disabled
                />
              </div>

              <Input
                label={t('sessions.topic_label')}
                value={topic}
                onChange={(event) => setTopic(event.target.value)}
              />

              <label className="block space-y-2">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                  {t('sessions.notes_label')}
                </span>
                <textarea
                  rows={5}
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  className="w-full rounded-3xl border border-slate-200 bg-white/90 px-4 py-3 text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-950/60"
                />
              </label>
            </div>
          ) : null}

          {currentStep === 3 && sessionSummary ? (
            <div className="space-y-4">
              <h2 className="text-xl font-semibold text-slate-950 dark:text-white">
                {t('sessions.payment_summary')}
              </h2>
              <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
                  <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.topic')}</p>
                  <p className="mt-1 font-semibold text-slate-950 dark:text-white">{sessionSummary.topic}</p>
                </div>
                <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
                  <p className="text-sm text-slate-500 dark:text-slate-400">{t('common.amount')}</p>
                  <p className="mt-1 font-semibold text-slate-950 dark:text-white">
                    {new Intl.NumberFormat('en-IN', {
                      style: 'currency',
                      currency: 'INR',
                      maximumFractionDigits: 0,
                    }).format(sessionSummary.amount)}
                  </p>
                </div>
              </div>
              <Badge variant="info" className="normal-case tracking-normal">
                {mentorQuery.data.name}
              </Badge>
              <div className="rounded-3xl border border-slate-200/70 p-4 dark:border-slate-800">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {t('sessions.payment_provider_note')}
                </p>
                {paymentInitQuery.isLoading ? (
                  <p className="mt-3 text-sm text-slate-600 dark:text-slate-300">
                    {t('sessions.payment_loading')}
                  </p>
                ) : null}
                {paymentInitQuery.isError ? (
                  <p className="mt-3 text-sm text-red-500">
                    {getApiErrorMessage(paymentInitQuery.error, t('sessions.payment_failed'))}
                  </p>
                ) : null}
                {capturePaymentMutation.isPending ? (
                  <p className="mt-3 text-sm text-slate-600 dark:text-slate-300">
                    {t('sessions.payment_verifying')}
                  </p>
                ) : null}
                <div ref={paypalButtonsRef} className="mt-4 min-h-12" />
              </div>
            </div>
          ) : null}

          {currentStep === 4 && sessionSummary ? (
            <div className="space-y-4">
              <h2 className="text-2xl font-semibold text-slate-950 dark:text-white">
                {t('sessions.confirmation_title')}
              </h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                {t('sessions.booking_reference')}: {sessionSummary.bookingReference}
              </p>
              <Button onClick={() => navigate(`/sessions/${sessionSummary.id}`)}>
                {t('sessions.go_to_session')}
              </Button>
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap justify-between gap-3">
          <Button
            variant="outline"
            onClick={() => setCurrentStep((current) => Math.max(0, current - 1))}
            disabled={currentStep === 0 || capturePaymentMutation.isPending}
          >
            {t('common.previous')}
          </Button>

          {currentStep < 2 ? (
            <Button
              onClick={() => {
                if (currentStep === 0 && !selectedDateKey) {
                  toast.error(t('errors.required'));
                  return;
                }

                if (currentStep === 1 && !selectedSlotId) {
                  toast.error(t('errors.required'));
                  return;
                }

                setCurrentStep((current) => current + 1);
              }}
            >
              {t('common.next')}
            </Button>
          ) : null}

          {currentStep === 2 ? (
            <Button isLoading={prepareBookingMutation.isPending} onClick={() => prepareBookingMutation.mutate()}>
              {t('sessions.continue_to_payment')}
            </Button>
          ) : null}

        </div>
      </Card>
    </div>
  );
}
