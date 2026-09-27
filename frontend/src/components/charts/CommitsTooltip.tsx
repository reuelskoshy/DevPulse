import type { TooltipContentProps } from 'recharts'
import type { DailyCommits } from '../../api/team'
import { formatCount, formatUtcDayLong, pluralize } from '../../lib/format'

function isDailyCommits(value: unknown): value is DailyCommits {
  if (typeof value !== 'object' || value === null) return false
  const row = value as Record<string, unknown>
  return typeof row.date === 'string' && typeof row.commits === 'number'
}

/**
 * Dark glass tooltip for commits-per-day series: the value leads (strong, high
 * contrast), the UTC day follows, keyed by a short stroke of the series color.
 * Text is rendered by React (escaped), never injected as HTML.
 */
export function CommitsTooltip({ active, payload }: TooltipContentProps) {
  const row: unknown = payload?.[0]?.payload
  if (!active || !isDailyCommits(row)) return null

  return (
    <div
      className="pointer-events-none min-w-[8.5rem] rounded-xl px-3 py-2 ring-1 ring-white/10 backdrop-blur-xl"
      style={{ background: 'color-mix(in srgb, var(--surface-2) 92%, transparent)' }}
    >
      <div className="flex items-center gap-2">
        <span aria-hidden="true" className="h-0.5 w-3 shrink-0 rounded-full" style={{ background: 'var(--chart-1)' }} />
        <span className="text-sm font-semibold tabular-nums" style={{ color: 'var(--text)' }}>
          {formatCount(row.commits)} {pluralize(row.commits, 'commit')}
        </span>
      </div>
      <div className="mt-0.5 pl-5 text-[11px]" style={{ color: 'var(--text-muted)' }}>
        {formatUtcDayLong(row.date)}
      </div>
    </div>
  )
}
