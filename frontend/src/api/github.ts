import apiClient from './client'

export interface GitHubConnection {
  connected: boolean
  login: string | null
  repoCount: number
  commitCount: number
  lastSyncedAt: string | null
}

export interface GitHubAuthorization {
  authorizationUrl: string
}

export interface SyncResult {
  reposSynced: number
  commitsSynced: number
  syncedAt: string
}

export const githubApi = {
  getConnection: async (): Promise<GitHubConnection> => {
    const response = await apiClient.get<GitHubConnection>('/integrations/github')
    return response.data
  },

  authorize: async (): Promise<GitHubAuthorization> => {
    const response = await apiClient.post<GitHubAuthorization>('/integrations/github/authorize')
    return response.data
  },

  sync: async (): Promise<SyncResult> => {
    const response = await apiClient.post<SyncResult>('/integrations/github/sync')
    return response.data
  },
}
