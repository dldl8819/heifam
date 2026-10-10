import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'

/** What the points pages (a member's points, the monthly ranking, the prize events) share. */

export const POINT_CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'

export function PointErrorAlert({ message }: { message: string }) {
  return (
    <Alert variant="destructive" appearance="light">
      <AlertIcon icon="destructive">!</AlertIcon>
      <AlertContent>
        <AlertDescription>{message}</AlertDescription>
      </AlertContent>
    </Alert>
  )
}

export function PointPageHeader({ title, description }: { title: string; description: string }) {
  return (
    <div className="space-y-1">
      <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{title}</h1>
      <p className="text-sm text-slate-500 dark:text-slate-400">{description}</p>
    </div>
  )
}
