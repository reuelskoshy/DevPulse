import apiClient from './client'

export interface DigestContributor {
  name: string
  commits: number
}

/** The last 7 UTC days, compared with the 7 before; `team` is null for someone who only sees themselves. */
export interface WeeklyDigest {
  from: string
  to: string
  recipientName: string
  you: {
    connected: boolean
    commits: number
    previousCommits: number
    activeDays: number
    pullRequestsMerged: number
    reviews: number
    topRepo: string | null
  }
  team: {
    members: number
    activeMembers: number
    commits: number
    previousCommits: number
    pullRequestsMerged: number
    pullRequestsOpen: number
    medianHoursToMerge: number | null
    topContributors: DigestContributor[]
    quietMembers: string[]
  } | null
  /** Nothing to report, so no email goes out this week. */
  empty: boolean
}

export interface DigestPreferences {
  weeklyDigestEnabled: boolean
  /** ISO instant of the last digest sent, or null. */
  lastSentAt: string | null
  /** Whether this server can send email at all. */
  emailDelivery: boolean
}

export const digestApi = {
  weekly: async (): Promise<WeeklyDigest> => {
    const response = await apiClient.get<WeeklyDigest>('/digest/weekly')
    return response.data
  },
  preferences: async (): Promise<DigestPreferences> => {
    const response = await apiClient.get<DigestPreferences>('/digest/preferences')
    return response.data
  },
  updatePreferences: async (weeklyDigestEnabled: boolean): Promise<DigestPreferences> => {
    const response = await apiClient.put<DigestPreferences>('/digest/preferences', { weeklyDigestEnabled })
    return response.data
  },
}
