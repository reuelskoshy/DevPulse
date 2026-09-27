import type { ReactNode } from 'react'

const OUTER_RADIUS = {
  lg: 'rounded-[2rem]',
  md: 'rounded-[1.5rem]',
}

const INNER_RADIUS = {
  lg: 'rounded-[calc(2rem-0.375rem)]',
  md: 'rounded-[calc(1.5rem-0.375rem)]',
}

interface DoubleBezelProps {
  children: ReactNode
  size?: 'lg' | 'md'
  className?: string
  innerClassName?: string
}

/**
 * "Double-bezel" nested card: an outer shell (hairline ring, faint tint) holding
 * an inner core (solid surface + inset highlight) — never a flat card.
 */
export function DoubleBezel({ children, size = 'lg', className = '', innerClassName = '' }: DoubleBezelProps) {
  return (
    <div className={`bg-white/[0.03] p-1.5 ring-1 ring-white/10 ${OUTER_RADIUS[size]} ${className}`}>
      <div
        className={`h-full shadow-[inset_0_1px_1px_rgba(255,255,255,0.08)] ${INNER_RADIUS[size]} ${innerClassName}`}
        style={{ background: 'var(--surface)' }}
      >
        {children}
      </div>
    </div>
  )
}
