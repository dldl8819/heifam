'use client'

import * as React from 'react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button-1'

type AlertVariant = 'secondary' | 'primary' | 'destructive' | 'success' | 'info' | 'mono' | 'warning'
type AlertAppearance = 'solid' | 'outline' | 'light' | 'stroke'
type AlertSize = 'lg' | 'md' | 'sm'
type AlertIconTone = 'primary' | 'destructive' | 'success' | 'info' | 'warning'

const baseClass =
  'flex w-full items-stretch gap-2 rounded-lg border text-sm'

const sizeClassMap: Record<AlertSize, string> = {
  lg: 'p-4 gap-3 text-base',
  md: 'p-3.5 gap-2.5 text-sm',
  sm: 'px-3 py-2.5 gap-2 text-xs',
}

const appearanceClassMap: Record<AlertAppearance, string> = {
  solid: 'border-transparent',
  outline: 'border-slate-200 bg-white text-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100',
  light: 'border-slate-200 bg-slate-50 text-slate-900',
  stroke: 'border-slate-300 bg-transparent text-slate-900 dark:border-slate-600 dark:text-slate-100',
}

const solidVariantClassMap: Record<AlertVariant, string> = {
  secondary: 'bg-slate-100 text-slate-900 dark:bg-slate-700 dark:text-slate-100',
  primary: 'bg-indigo-600 text-white',
  destructive: 'bg-rose-600 text-white',
  success: 'bg-emerald-600 text-white',
  info: 'bg-sky-600 text-white',
  warning: 'bg-amber-500 text-slate-900',
  mono: 'bg-slate-950 text-white dark:bg-slate-800',
}

const lightVariantClassMap: Record<AlertVariant, string> = {
  secondary: 'bg-slate-50 border-slate-200 text-slate-900 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100',
  primary: 'bg-indigo-50 border-indigo-200 text-slate-900 dark:border-indigo-800 dark:bg-indigo-950/50 dark:text-indigo-200',
  destructive: 'bg-rose-50 border-rose-200 text-slate-900 dark:border-rose-800 dark:bg-rose-950/50 dark:text-rose-200',
  success: 'bg-emerald-50 border-emerald-200 text-slate-900 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-200',
  info: 'bg-sky-50 border-sky-200 text-slate-900 dark:border-sky-800 dark:bg-sky-950/50 dark:text-sky-200',
  warning: 'bg-amber-50 border-amber-200 text-slate-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-200',
  mono: 'bg-slate-100 border-slate-200 text-slate-900 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100',
}

const outlineToneClassMap: Record<AlertVariant, string> = {
  secondary: 'text-slate-900 dark:text-slate-100',
  primary: 'text-indigo-700 dark:text-indigo-300',
  destructive: 'text-rose-700 dark:text-rose-300',
  success: 'text-emerald-700 dark:text-emerald-300',
  info: 'text-sky-700 dark:text-sky-300',
  warning: 'text-amber-700 dark:text-amber-300',
  mono: 'text-slate-900 dark:text-slate-100',
}

const iconToneClassMap: Record<AlertIconTone, string> = {
  primary: 'text-indigo-600 dark:text-indigo-300',
  destructive: 'text-rose-600 dark:text-rose-300',
  success: 'text-emerald-600 dark:text-emerald-300',
  info: 'text-sky-600 dark:text-sky-300',
  warning: 'text-amber-600 dark:text-amber-300',
}

function getAlertToneClass(variant: AlertVariant, appearance: AlertAppearance): string {
  if (appearance === 'solid') {
    return solidVariantClassMap[variant]
  }

  if (appearance === 'light') {
    return lightVariantClassMap[variant]
  }

  if (appearance === 'outline' || appearance === 'stroke') {
    return outlineToneClassMap[variant]
  }

  return ''
}

interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: AlertVariant
  icon?: AlertIconTone
  appearance?: AlertAppearance
  size?: AlertSize
  close?: boolean
  onClose?: () => void
}

interface AlertIconProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: AlertVariant
  icon?: AlertIconTone
  appearance?: AlertAppearance
  size?: AlertSize
}

function Alert({
  className,
  variant = 'secondary',
  size = 'md',
  icon,
  appearance = 'solid',
  close = false,
  onClose,
  children,
  ...props
}: AlertProps) {
  return (
    <div
      data-slot="alert"
      role="alert"
      className={cn(baseClass, sizeClassMap[size], appearanceClassMap[appearance], getAlertToneClass(variant, appearance), className)}
      {...props}
    >
      {children}
      {close && (
        <Button
          size="sm"
          variant="inverse"
          mode="icon"
          onClick={onClose}
          aria-label="Dismiss"
          data-slot="alert-close"
          className="shrink-0"
        >
          <span aria-hidden>×</span>
        </Button>
      )}
    </div>
  )
}

function AlertTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <div data-slot="alert-title" className={cn('grow tracking-tight font-semibold', className)} {...props} />
}

function AlertIcon({ children, className, icon, ...props }: AlertIconProps) {
  return (
    <div
      data-slot="alert-icon"
      className={cn('shrink-0 mt-0.5', icon ? iconToneClassMap[icon] : '')}
      {...props}
    >
      <span className={className}>{children}</span>
    </div>
  )
}

function AlertToolbar({ children, className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div data-slot="alert-toolbar" className={cn(className)} {...props}>
      {children}
    </div>
  )
}

function AlertDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <div
      data-slot="alert-description"
      className={cn('text-sm [&_p]:mb-2 [&_p]:leading-relaxed', className)}
      {...props}
    />
  )
}

function AlertContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      data-slot="alert-content"
      className={cn('space-y-2 [&_[data-slot=alert-title]]:font-semibold', className)}
      {...props}
    />
  )
}

export { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle, AlertToolbar }

