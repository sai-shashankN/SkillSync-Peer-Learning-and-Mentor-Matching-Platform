import { useTranslation } from 'react-i18next';
import { Badge, Card } from '../components/ui';

interface RoutePlaceholderPageProps {
  titleKey: string;
}

export default function RoutePlaceholderPage({ titleKey }: RoutePlaceholderPageProps) {
  const { t } = useTranslation();

  return (
    <Card className="space-y-4">
      <Badge variant="default">Preview</Badge>
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold text-slate-950 dark:text-white">{t(titleKey)}</h1>
        <p className="max-w-2xl text-slate-500 dark:text-slate-400">
          {t('common.coming_soon')}
        </p>
      </div>
    </Card>
  );
}
