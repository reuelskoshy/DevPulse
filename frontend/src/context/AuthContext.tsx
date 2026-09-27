import { useState, useEffect } from 'react'
import type { ReactNode } from 'react'
import { normalizeUser } from '../types/auth'
import type { AuthResponse, User } from '../types/auth'
import { authApi } from '../api/auth'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Check if user is logged in on mount
    const token = localStorage.getItem('accessToken')
    const savedUser = localStorage.getItem('user')

    if (token && savedUser) {
      try {
        const parsed = normalizeUser(JSON.parse(savedUser))
        if (parsed) {
          setUser(parsed)
        } else {
          localStorage.removeItem('user')
          localStorage.removeItem('accessToken')
        }
      } catch (error) {
        console.error('Failed to parse saved user:', error)
        localStorage.removeItem('user')
        localStorage.removeItem('accessToken')
      }
    }

    setLoading(false)
  }, [])

  /** Stores the session exactly the same way for every sign-in path. */
  const startSession = (response: AuthResponse) => {
    const sessionUser = normalizeUser(response.user)
    if (!sessionUser) {
      throw new Error('The server returned an unexpected sign-in response.')
    }
    localStorage.setItem('accessToken', response.accessToken)
    localStorage.setItem('user', JSON.stringify(sessionUser))
    setUser(sessionUser)
  }

  const login = async (email: string, password: string) => {
    startSession(await authApi.login({ email, password }))
  }

  const register = async (name: string, email: string, password: string) => {
    startSession(await authApi.register({ name, email, password }))
  }

  const loginDemo = async () => {
    startSession(await authApi.demo())
  }

  const logoutTo = (path: string) => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('user')
    // No setUser(null) here: the full page load resets all in-memory state anyway, and
    // clearing the user first lets ProtectedRoute flash /login before `path` arrives.
    window.location.assign(path)
  }

  const logout = () => logoutTo('/login')

  return (
    <AuthContext.Provider value={{ user, loading, login, register, loginDemo, logout, logoutTo }}>
      {children}
    </AuthContext.Provider>
  )
}
