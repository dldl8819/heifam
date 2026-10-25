import { useEffect, useRef, type RefObject } from 'react'

// What the keyboard can stop on inside a dialog.
const FOCUSABLE = [
  'a[href]',
  'button:not(:disabled)',
  'input:not(:disabled):not([type="hidden"])',
  'select:not(:disabled)',
  'textarea:not(:disabled)',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

/**
 * Keeps the keyboard with an open dialog, as a dialog that covers the page should (WCAG 2.4.3):
 * on opening the focus moves to its first control (or to the dialog itself, which then needs
 * tabIndex={-1}); Tab and Shift+Tab go round inside it; Escape closes it when it can be closed;
 * and on closing the focus goes back to what had it, the button that opened it.
 */
export function useDialogFocus(dialogRef: RefObject<HTMLElement | null>, open: boolean, onClose?: () => void) {
  // The latest close, without opening the dialog over again each time the caller makes a new one.
  const closeRef = useRef(onClose)
  useEffect(() => {
    closeRef.current = onClose
  })

  useEffect(() => {
    const dialog = dialogRef.current
    if (!open || !dialog) {
      return
    }

    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const stops = () =>
      Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => element.getClientRects().length > 0)

    const start = stops()[0] ?? dialog
    start.focus()

    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && closeRef.current) {
        event.preventDefault()
        closeRef.current()
        return
      }
      if (event.key !== 'Tab') {
        return
      }
      const items = stops()
      if (items.length === 0) {
        event.preventDefault()
        dialog.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (event.shiftKey && (active === first || !dialog.contains(active))) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('keydown', handleKey)
      if (opener?.isConnected) {
        opener.focus()
      }
    }
  }, [dialogRef, open])
}
