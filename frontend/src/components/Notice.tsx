import type { ReactNode } from 'react'
import { CheckCircle, WarningCircle, X } from '@phosphor-icons/react'

export type NoticeTone = 'success' | 'error'

export interface NoticeState {
  tone: NoticeTone
  message: string
}

interface NoticeProps extends NoticeState {
  onDismiss?: () => void
  /** Optional trailing action (e.g. a "Try again" button). */
  action?: ReactNode
  className?: string
}

/** Soft tinted status pill-card (same shape as Login's error box). */
export function Notice({ tone, message, onDismiss, action, className = '' }: NoticeProps) {
  const isError = tone === 'error'
  const Icon = isError ? WarningCircle : CheckCircle

  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={`flex items-start gap-2 rounded-2xl px-4 py-3 text-sm ring-1 transition-all duration-700 ease-fluid starting:translate-y-1 starting:opacity-0 ${
        isError
          ? 'bg-[var(--danger-soft)] text-[var(--danger)] ring-[var(--danger)]/30'
          : 'bg-[var(--accent-soft)] text-[var(--accent)] ring-[var(--accent)]/30'
      } ${className}`}
    >
      <Icon weight="light" className="mt-0.5 h-4 w-4 shrink-0" />
      <span className="min-w-0 flex-1">{message}</span>
      {action}
      {onDismiss && (
        <button
          type="button"
          aria-label="Dismiss"
          onClick={onDismiss}
          className="-my-1 -mr-2 flex h-7 w-7 shrink-0 items-center justify-center rounded-full opacity-70 transition-all duration-700 ease-fluid hover:bg-white/10 hover:opacity-100"
        >
          <X weight="light" className="h-4 w-4" />
        </button>
      )}
    </div>
  )
}
