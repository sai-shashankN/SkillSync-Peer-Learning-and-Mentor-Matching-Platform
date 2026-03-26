import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';
import en from './locales/en/common.json';
import adminEn from './locales/en/admin';
import learnerEn from './locales/en/learner';
import mentorEn from './locales/en/mentor';
import hi from './locales/hi/common.json';
import adminHi from './locales/hi/admin';
import learnerHi from './locales/hi/learner';
import mentorHi from './locales/hi/mentor';

type LocaleObject = Record<string, unknown>;

function isLocaleObject(value: unknown): value is LocaleObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function mergeLocales<T extends LocaleObject, U extends LocaleObject>(base: T, extra: U): T & U {
  const merged: LocaleObject = { ...base };

  Object.entries(extra).forEach(([key, value]) => {
    const currentValue = merged[key];

    if (isLocaleObject(currentValue) && isLocaleObject(value)) {
      merged[key] = mergeLocales(currentValue, value);
      return;
    }

    merged[key] = value;
  });

  return merged as T & U;
}

i18n.use(LanguageDetector).use(initReactI18next).init({
  resources: {
    en: { translation: mergeLocales(mergeLocales(mergeLocales(en, learnerEn), mentorEn), adminEn) },
    hi: { translation: mergeLocales(mergeLocales(mergeLocales(hi, learnerHi), mentorHi), adminHi) },
  },
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

export default i18n;
