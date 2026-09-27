import apiClient from './client'
import type { UserRole } from '../types/auth'

/** A dp_user row as the People screen sees it (GET /team-members). */
export interface TeamMember {
  id: string
  name: string
  email: string
  role: UserRole
  /** The user's manager, or null. */
  parentId: string | null
  activeStatus: boolean
  activeStatusReason: string | null
  /** ISO instant. */
  accountCreatedDatetime: string | null
}

export interface UpdateRolePayload {
  role: UserRole
  parentId: string | null
}

export interface UpdateStatusPayload {
  activeStatus: boolean
  activeStatusReason: string | null
}

export const usersApi = {
  list: async (): Promise<TeamMember[]> => {
    const response = await apiClient.get<TeamMember[]>('/team-members')
    return response.data
  },
  updateRole: async (id: string, payload: UpdateRolePayload): Promise<TeamMember> => {
    const response = await apiClient.patch<TeamMember>(`/team-members/${id}/role`, payload)
    return response.data
  },
  updateStatus: async (id: string, payload: UpdateStatusPayload): Promise<TeamMember> => {
    const response = await apiClient.patch<TeamMember>(`/team-members/${id}/status`, payload)
    return response.data
  },
}
