import { useTranslation } from 'react-i18next';
import { Badge } from '../../components/ui';

type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'default';

interface SessionStatusBadgeProps {
  status: string;
}

function toStatusLabel(status: string) {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => `${part.slice(0, 1).toUpperCase()}${part.slice(1)}`)
    .join(' ');
}

function getVariant(status: string): BadgeVariant {
  switch (status.toUpperCase()) {
    case 'COMPLETED':
      return 'success';
    case 'CANCELLED':
      return 'danger';
    case 'PENDING':
    case 'PAYMENT_PENDING':
      return 'warning';
    case 'UPCOMING':
    case 'CONFIRMED':
    case 'BOOKED':
    case 'SCHEDULED':
      return 'info';
    default:
      return 'default';
  }
}

export default function SessionStatusBadge({ status }: SessionStatusBadgeProps) {
  const { t } = useTranslation();

  return (
    <Badge variant={getVariant(status)} className="normal-case tracking-normal">
      {t(`sessions.status_values.${status.toLowerCase()}`, {
        defaultValue: toStatusLabel(status),
      })}
    </Badge>
  );
}
