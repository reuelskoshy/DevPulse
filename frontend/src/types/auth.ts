export type UserRole = 'ADMIN' | 'MANAGER' | 'MEMBER'

export interface User {
  id: string
  email: string
  role: string
  createdAt: string
  /** True for a live-demo session (read-only sample team). Missing on older stored users → false. */
  demo: boolean
}

export interface AuthResponse {
  accessToken: string
  user: User
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
}

/** Admins of a real (non-demo) session manage roles and reporting lines on the People screen. */
export function canManagePeople(user: Pick<User, 'role' | 'demo'> | null | undefined): boolean {
  return user?.role === 'ADMIN' && !user.demo
}

/** Roles that get the Team view (MEMBER only ever sees themselves). */
export function canViewTeam(role: string | null | undefined): boolean {
  return role === 'MANAGER' || role === 'ADMIN'
}

/**
 * Coerces a user from the API or localStorage into a `User`, treating a missing
 * `demo` flag as false (users stored before the flag existed). Returns null
 * when the value isn't a usable user.
 */
export function normalizeUser(value: unknown): User | null {
  if (typeof value !== 'object' || value === null) return null
  const raw = value as Record<string, unknown>
  if (typeof raw.id !== 'string' || typeof raw.email !== 'string' || typeof raw.role !== 'string') {
    return null
  }
  return {
    id: raw.id,
    email: raw.email,
    role: raw.role,
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : '',
    demo: raw.demo === true,
  }
}
