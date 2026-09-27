import { createContext } from 'react'
import type { User } from '../types/auth'

export interface AuthContextType {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  /** Starts a read-only live-demo session (the demo manager and their sample team). */
  loginDemo: () => Promise<void>
  /** Clears the session and hard-navigates to /login. */
  logout: () => void
  /**
   * Clears the session and hard-navigates to `path` (e.g. '/register' to leave the
   * demo for a real account). The full reload also drops every cached query.
   */
  logoutTo: (path: string) => void
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined)
