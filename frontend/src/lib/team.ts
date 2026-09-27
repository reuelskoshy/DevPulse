import type { DailyCommits } from '../api/team'

/** The busiest day in a series (first one wins on ties), or null when every day is zero. */
export function findPeakDay(daily: DailyCommits[]): DailyCommits | null {
  return daily.reduce<DailyCommits | null>((best, day) => (day.commits > (best?.commits ?? 0) ? day : best), null)
}

/**
 * Clean integer y-axis ticks from 0 for a count series: a 1/2/2.5/5 × 10ⁿ step
 * (integers only) aiming for about `intervals` gaps. An all-zero series still
 * gets a 0–4 axis instead of a collapsed one.
 */
export function countAxisTicks(max: number, intervals = 4): number[] {
  const raw = Math.max(max, 1) / intervals
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step =
    [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw && Number.isInteger(s)) ??
    Math.max(1, Math.ceil(raw))
  const top = Math.ceil(max / step) * step || step * intervals
  const ticks: number[] = []
  for (let value = 0; value <= top; value += step) ticks.push(value)
  return ticks
}

/** Up to two initials from a display name ("Maya Chen" -> "MC"). */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0].charAt(0)
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : ''
  return `${first}${last}`.toUpperCase()
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Manager',
  MEMBER: 'Member',
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role.charAt(0).toUpperCase() + role.slice(1).toLowerCase()
}
