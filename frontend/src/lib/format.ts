/**
 * Formatting helpers. API calendar dates ("YYYY-MM-DD") are UTC days, so they
 * are parsed at UTC midnight and always formatted with timeZone 'UTC' — never
 * in the viewer's zone, where they could shift by a day.
 */

const SHORT_DAY = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
const LONG_DAY = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  timeZone: 'UTC',
})
const RANGE = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })
const INSTANT = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const NUMBER = new Intl.NumberFormat()

function parseUtcDay(day: string): Date | null {
  const date = new Date(`${day}T00:00:00Z`)
  return Number.isNaN(date.getTime()) ? null : date
}

/** "Sep 14" */
export function formatUtcDayShort(day: string): string {
  const date = parseUtcDay(day)
  return date ? SHORT_DAY.format(date) : day
}

/** "Mon, Sep 14" */
export function formatUtcDayLong(day: string): string {
  const date = parseUtcDay(day)
  return date ? LONG_DAY.format(date) : day
}

/** "Sep 14 – 27, 2026" (locale-aware range; a single day when from === to). */
export function formatUtcRange(from: string, to: string): string {
  const start = parseUtcDay(from)
  const end = parseUtcDay(to)
  if (!start || !end) return `${from} – ${to}`
  return RANGE.formatRange(start, end)
}

/** Local date + time for an ISO instant, e.g. for a title attribute. */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : INSTANT.format(date)
}

/** "just now", "12m ago", "5h ago", "2d ago" — or null when there is no timestamp. */
export function formatRelativeTime(iso: string | null | undefined, now: number): string | null {
  if (!iso) return null
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return null
  const diff = Math.max(0, now - time)
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return 'just now'
  if (diff < hour) return `${Math.floor(diff / minute)}m ago`
  if (diff < day) return `${Math.floor(diff / hour)}h ago`
  return `${Math.floor(diff / day)}d ago`
}

/** Thousands-separated integer. */
export function formatCount(value: number): string {
  return NUMBER.format(value)
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return count === 1 ? singular : plural
}
