'use client'

import * as React from 'react'
import { cn } from '@/lib/utils'

type ButtonSize = 'sm' | 'md'
type ButtonVariant = 'default' | 'inverse'
type ButtonMode = 'default' | 'icon'

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  size?: ButtonSize
  variant?: ButtonVariant
  mode?: ButtonMode
}

const sizeClassMap: Record<ButtonSize, string> = {
  sm: 'h-7 px-2.5 text-xs',
  md: 'h-9 px-3 text-sm',
}

const variantClassMap: Record<ButtonVariant, string> = {
  default: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
  inverse: 'border border-slate-200/70 bg-transparent text-current hover:bg-black/5',
}

const modeClassMap: Record<ButtonMode, string> = {
  default: '',
  icon: 'size-6 p-0',
}

export function Button({
  className,
  size = 'md',
  variant = 'default',
  mode = 'default',
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        'inline-flex items-center justify-center rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        sizeClassMap[size],
        variantClassMap[variant],
        modeClassMap[mode],
        className
      )}
      {...props}
    />
  )
}

