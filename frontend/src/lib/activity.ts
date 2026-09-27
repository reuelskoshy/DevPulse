import { TEAM_RANGE_OPTIONS } from '../api/team'
import type { TeamRangeDays } from '../api/team'

export const DEFAULT_DAYS: TeamRangeDays = 14

export type Tone = 'accent' | 'accent-2'

export const TONE_STYLES: Record<Tone, { background: string; color: string }> = {
  accent: { background: 'var(--accent-soft)', color: 'var(--accent)' },
  'accent-2': { background: 'var(--accent-2-soft)', color: 'var(--accent-2)' },
}

/** `?days=` from the URL when it's one of the presets, otherwise the 14-day default. */
export function parseDays(value: string | null): TeamRangeDays {
  const requested = Number(value)
  return TEAM_RANGE_OPTIONS.find((option) => option === requested) ?? DEFAULT_DAYS
}
