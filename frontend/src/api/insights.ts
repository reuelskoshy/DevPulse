import apiClient from './client'

export interface InsightHighlight {
  title: string
  detail: string
}

/** Measured server-side from synced activity; the AI only writes the prose around them. */
export interface InsightFacts {
  windowDays: number
  commits: number
  activeDays: number
  longestStreak: number
  busiestWeekday: string | null
  weekendCommits: number
  pullRequestsOpened: number
  pullRequestsMerged: number
  pullRequestsOpen: number
  reviews: number
  medianHoursToMerge: number | null
  topRepos: { name: string; commits: number }[]
}

export interface InsightDetails {
  headline: string | null
  highlights: InsightHighlight[]
  patterns: string[]
  suggestions: string[]
  facts: InsightFacts
}

export interface Insight {
  id: string
  /** The overview paragraph; for insights made before details existed, the whole insight. */
  summary: string
  commitCount: number
  repoCount: number
  generatedAt: string
  /** Null for older insights and for the "no activity" placeholder. */
  details: InsightDetails | null
}

export const insightsApi = {
  getLatest: async (): Promise<Insight | null> => {
    try {
      const response = await apiClient.get<Insight>('/insights/latest')
      return response.data
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status
      if (status === 404) {
        return null
      }
      throw err
    }
  },

  generate: async (): Promise<Insight> => {
    const response = await apiClient.post<Insight>('/insights/generate')
    return response.data
  },
}
