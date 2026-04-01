const ADMIN_KEY_STORAGE_KEY = 'heifam.adminApiKey'

function hasWindow(): boolean {
  return typeof window !== 'undefined'
}

export function getStoredAdminApiKey(): string {
  if (!hasWindow()) {
    return ''
  }

  try {
    return (window.localStorage.getItem(ADMIN_KEY_STORAGE_KEY) ?? '').trim()
  } catch {
    return ''
  }
}

export function setStoredAdminApiKey(value: string): void {
  if (!hasWindow()) {
    return
  }

  const normalized = value.trim()

  try {
    if (normalized.length === 0) {
      window.localStorage.removeItem(ADMIN_KEY_STORAGE_KEY)
      return
    }

    window.localStorage.setItem(ADMIN_KEY_STORAGE_KEY, normalized)
  } catch {
    return
  }
}

export function clearStoredAdminApiKey(): void {
  if (!hasWindow()) {
    return
  }

  try {
    window.localStorage.removeItem(ADMIN_KEY_STORAGE_KEY)
  } catch {
    return
  }
}

export { ADMIN_KEY_STORAGE_KEY }
