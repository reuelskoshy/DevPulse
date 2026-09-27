import type { ReactNode } from 'react'
import { motion, useReducedMotion } from 'framer-motion'
import type { Icon } from '@phosphor-icons/react'
import { TEAM_RANGE_OPTIONS } from '../api/team'
import { TONE_STYLES } from '../lib/activity'
import type { Tone } from '../lib/activity'
import type { TeamRangeDays } from '../api/team'
import { formatCount } from '../lib/format'
import { DoubleBezel } from './DoubleBezel'

/** Shared building blocks for the activity pages (Overview and Team). */

const EASE_FLUID = [0.32, 0.72, 0, 1] as const

export function Eyebrow({ children, tone = 'accent' }: { children: ReactNode; tone?: Tone }) {
  return (
    <span
      className="inline-flex w-fit items-center rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em]"
      style={TONE_STYLES[tone]}
    >
      {children}
    </span>
  )
}

/* ------------------------------------------------------------------ range control */

interface RangeControlProps {
  value: TeamRangeDays
  onChange: (days: TeamRangeDays) => void
  /** framer-motion layoutId for the gliding thumb; unique per page. */
  thumbId: string
}

/** Segmented pill for the date-range presets; the active thumb glides between options. */
export function RangeControl({ value, onChange, thumbId }: RangeControlProps) {
  const reduceMotion = useReducedMotion()

  return (
    <div
      role="group"
      aria-label="Date range"
      className="inline-flex items-center gap-1 rounded-full bg-white/[0.04] p-1 ring-1 ring-white/10 backdrop-blur-xl"
    >
      {TEAM_RANGE_OPTIONS.map((option) => {
        const selected = option === value
        return (
          <button
            key={option}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option)}
            className={`relative rounded-full px-3.5 py-1.5 text-xs font-medium transition-colors duration-700 ease-fluid sm:px-4 ${
              selected ? 'text-black' : 'text-white/60 hover:text-white'
            }`}
          >
            {selected &&
              (reduceMotion ? (
                <span aria-hidden="true" className="absolute inset-0 rounded-full bg-white" />
              ) : (
                <motion.span
                  aria-hidden="true"
                  layoutId={thumbId}
                  className="absolute inset-0 rounded-full bg-white"
                  transition={{ duration: 0.7, ease: EASE_FLUID }}
                />
              ))}
            <span className="relative">{option} days</span>
          </button>
        )
      })}
    </div>
  )
}

/* ------------------------------------------------------------------ stat tile */

export interface StatTileProps {
  label: string
  value: string
  of?: number
  caption: string
  icon: Icon
  tone: Tone
  /** Renders a thin proportion bar for "x of y" tiles. */
  ratio?: number
  hero?: boolean
  /** Shown on hover, e.g. the exact timestamp behind a relative time. */
  title?: string
}

export function StatTile({ label, value, of, caption, icon: TileIcon, tone, ratio, hero = false, title }: StatTileProps) {
  return (
    <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-6 p-5 sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">{label}</span>
        <span
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
          style={{ background: TONE_STYLES[tone].background }}
        >
          <TileIcon weight="light" className="h-4 w-4" style={{ color: TONE_STYLES[tone].color }} />
        </span>
      </div>
      <div className="mt-auto">
        <div className={`font-semibold text-white ${hero ? 'text-3xl sm:text-4xl lg:text-5xl' : 'text-3xl sm:text-4xl'}`} title={title || undefined}>
          {value}
          {of !== undefined && (
            <span className="ml-1.5 text-base font-medium text-[var(--text-muted)]">of {formatCount(of)}</span>
          )}
        </div>
        <div className="mt-1 text-xs text-[var(--text-muted)]">{caption}</div>
        {/* Tiles without a ratio keep the track's space so values line up across the row. */}
        <div
          aria-hidden="true"
          className="mt-4 h-1 w-full overflow-hidden rounded-full"
          style={{ background: ratio === undefined ? 'transparent' : 'var(--accent-soft)' }}
        >
          {ratio !== undefined && (
            <div
              className="h-full rounded-full transition-[width] duration-700 ease-fluid"
              style={{ width: `${Math.round(Math.min(1, Math.max(0, ratio)) * 100)}%`, background: 'var(--chart-1)' }}
            />
          )}
        </div>
      </div>
    </DoubleBezel>
  )
}

/* ------------------------------------------------------------------ loading */

export function SkeletonBar({ className }: { className: string }) {
  return <div className={`rounded-full bg-white/[0.06] ${className}`} />
}
