import ko from '@/locales/ko.json'

type TranslationValue = string | TranslationMap
type TranslationMap = {
  [key: string]: TranslationValue
}

const translations = ko as TranslationMap

function resolvePath(path: string): string {
  const segments = path.split('.')
  let current: TranslationValue = translations

  for (const segment of segments) {
    if (typeof current !== 'object' || current === null || !(segment in current)) {
      return path
    }
    current = current[segment]
  }

  return typeof current === 'string' ? current : path
}

function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template
  }

  return template.replace(/\{(\w+)\}/g, (matched, key: string) => {
    const value = params[key]
    return value === undefined ? matched : String(value)
  })
}

export function t(path: string, params?: Record<string, string | number>): string {
  return interpolate(resolvePath(path), params)
}
