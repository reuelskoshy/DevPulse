import apiClient from './client'

/** One UTC calendar day ("YYYY-MM-DD") and its commit count. */
export interface DailyCommits {
  date: string
  commits: number
}

export interface RepoCommits {
  fullName: string
  commits: number
}

export interface TeamMemberActivity {
  userId: string
  name: string
  email: string
  role: string
  /** dp_user.active_status */
  active: boolean
  /** Whether this member is the caller. */
  self: boolean
  /** Has a connected GitHub account. */
  connected: boolean
  githubLogin: string | null
  avatarUrl: string | null
  /** ISO instant or null. */
  lastSyncedAt: string | null
  /** Commits in the window. */
  commits: number
  /** Distinct UTC days with at least one commit in the window. */
  activeDays: number
  /** Most recent commit in the window, ISO instant or null. */
  lastCommitAt: string | null
  /** Up to 3, commits desc then fullName asc. */
  topRepos: RepoCommits[]
  /** Exactly `days` entries, zero-filled, ascending. */
  daily: DailyCommits[]
}

export interface TeamTotals {
  members: number
  connectedMembers: number
  activeMembers: number
  commits: number
  reposTouched: number
}

export interface TeamActivity {
  days: number
  /** ISO date (UTC), inclusive. */
  from: string
  /** ISO date (UTC), inclusive — today in UTC. */
  to: string
  totals: TeamTotals
  /** Team total per day, exactly `days` entries, zero-filled, ascending. */
  daily: DailyCommits[]
  /** Sorted by commits desc, then name asc. */
  members: TeamMemberActivity[]
}

export const TEAM_RANGE_OPTIONS = [7, 14, 30, 90] as const
export type TeamRangeDays = (typeof TEAM_RANGE_OPTIONS)[number]

export const teamApi = {
  getActivity: async (days: number): Promise<TeamActivity> => {
    const response = await apiClient.get<TeamActivity>('/team/activity', { params: { days } })
    return response.data
  },
}
