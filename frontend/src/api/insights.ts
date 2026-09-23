import apiClient from './client'

export interface Insight {
  id: string
  summary: string
  commitCount: number
  repoCount: number
  generatedAt: string
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
