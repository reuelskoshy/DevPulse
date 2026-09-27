import { useMemo } from 'react'
import type { DailyCommits } from '../../api/team'
import { formatCount, formatUtcDayLong, pluralize } from '../../lib/format'

interface ContributionHeatmapProps {
  /** Ascending, zero-filled UTC days. */
  daily: DailyCommits[]
  /** Accessible summary; the grid itself is decorative for assistive tech. */
  label: string
}

interface Cell {
  date: string
  commits: number
  level: number
}

const LEVEL_FILL = [
  'rgba(255,255,255,0.04)',
  'rgba(22,168,119,0.28)',
  'rgba(22,168,119,0.5)',
  'rgba(22,168,119,0.75)',
  'var(--accent)',
]
const WEEKDAY_LABELS = ['Mon', '', 'Wed', '', 'Fri', '', '']
const MONTH = new Intl.DateTimeFormat(undefined, { month: 'short', timeZone: 'UTC' })

/** 0 for no commits, otherwise 1–4 by quarter of the busiest day, so one outlier doesn't wash out the rest. */
function levelOf(commits: number, max: number): number {
  if (commits <= 0 || max <= 0) return 0
  return Math.min(4, Math.max(1, Math.ceil((commits / max) * 4)))
}

/** Monday-first weekday index of a "YYYY-MM-DD" UTC day. */
function weekdayOf(date: string): number {
  return (new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7
}

/**
 * GitHub-style grid: one column per week (Monday on top), one square per UTC
 * day. Hovering a square shows its date and count through a native title.
 */
export function ContributionHeatmap({ daily, label }: ContributionHeatmapProps) {
  const { weeks, months } = useMemo(() => {
    const max = daily.reduce((highest, day) => Math.max(highest, day.commits), 0)
    const columns: (Cell | null)[][] = []
    let column: (Cell | null)[] = daily.length > 0 ? Array(weekdayOf(daily[0].date)).fill(null) : []
    for (const day of daily) {
      column.push({ date: day.date, commits: day.commits, level: levelOf(day.commits, max) })
      if (column.length === 7) {
        columns.push(column)
        column = []
      }
    }
    if (column.length > 0) columns.push([...column, ...Array(7 - column.length).fill(null)])

    // A column is labelled with the month whose 1st falls in it. The first column also gets its month,
    // unless the next label is too close to fit beside it.
    const monthName = (date: string) => MONTH.format(new Date(`${date}T00:00:00Z`))
    const monthLabels = columns.map((week) => {
      const firstOfMonth = week.find((cell) => cell?.date.endsWith('-01'))
      return firstOfMonth ? monthName(firstOfMonth.date) : ''
    })
    const nextLabel = monthLabels.findIndex(Boolean)
    if (daily.length > 0 && (nextLabel === -1 || nextLabel >= 3)) monthLabels[0] = monthName(daily[0].date)
    return { weeks: columns, months: monthLabels }
  }, [daily])

  const template = { gridTemplateColumns: `2rem repeat(${weeks.length}, minmax(0, 1fr))` }

  return (
    <div role="img" aria-label={label} className="w-full">
      <div aria-hidden="true" className="grid gap-[3px] sm:gap-1" style={template}>
        <span />
        {months.map((month, index) => (
          <span key={index} className="h-4 overflow-visible whitespace-nowrap text-[10px] text-[var(--text-muted)]">
            {month}
          </span>
        ))}
        {WEEKDAY_LABELS.map((weekday, row) => (
          <Row key={row} weekday={weekday} cells={weeks.map((week) => week[row])} />
        ))}
      </div>
      <div aria-hidden="true" className="mt-4 flex items-center justify-end gap-1.5 text-[10px] text-[var(--text-muted)]">
        <span className="mr-1">Less</span>
        {LEVEL_FILL.map((fill) => (
          <span key={fill} className="h-2.5 w-2.5 rounded-[3px]" style={{ background: fill }} />
        ))}
        <span className="ml-1">More</span>
      </div>
    </div>
  )
}

function Row({ weekday, cells }: { weekday: string; cells: (Cell | null)[] }) {
  return (
    <>
      <span className="flex items-center text-[10px] leading-none text-[var(--text-muted)]">{weekday}</span>
      {cells.map((cell, index) =>
        cell ? (
          <span
            key={cell.date}
            title={`${formatCount(cell.commits)} ${pluralize(cell.commits, 'commit')} on ${formatUtcDayLong(cell.date)}`}
            className="aspect-square w-full rounded-[3px] transition-transform duration-300 ease-fluid hover:scale-110 sm:rounded-[4px]"
            style={{ background: LEVEL_FILL[cell.level] }}
          />
        ) : (
          <span key={`empty-${index}`} className="aspect-square w-full" />
        ),
      )}
    </>
  )
}
