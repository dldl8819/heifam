/** Copies text to the clipboard, falling back to a hidden textarea where the Clipboard API is missing. */
export function copyTextWithFallback(text: string): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(text)
  }

  if (typeof document === 'undefined') {
    return Promise.reject(new Error('Clipboard is unavailable.'))
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()

  try {
    const copied = document.execCommand('copy')
    if (!copied) {
      throw new Error('Copy command failed.')
    }
    return Promise.resolve()
  } catch (error) {
    return Promise.reject(error)
  } finally {
    document.body.removeChild(textarea)
  }
}
