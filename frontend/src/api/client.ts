import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor: Add auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

const AUTH_ENDPOINT = /^\/?auth(?:[/?#]|$)/

/** True when the request targets an /auth/* endpoint (login, register, ...). */
function isAuthEndpoint(config: InternalAxiosRequestConfig): boolean {
  const url = config.url ?? ''
  const baseURL = config.baseURL ?? ''
  const path = baseURL && url.startsWith(baseURL) ? url.slice(baseURL.length) : url
  return AUTH_ENDPOINT.test(path)
}

// On non-auth endpoints the backend only answers 401 when the DevPulse session is
// missing or invalid, so that always means "sign in again". A 401 from /auth/*
// (e.g. a wrong password) propagates so the page can show its message.
apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      error.config &&
      !isAuthEndpoint(error.config)
    ) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

/**
 * User-facing message for a failed API call: the body's `message` (ApiError
 * shape) when present, otherwise `fallback` (e.g. Spring's default error body,
 * network errors, non-axios errors).
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<{ message?: unknown }>(error)) {
    const message = error.response?.data?.message
    if (typeof message === 'string' && message.trim().length > 0) {
      return message
    }
  }
  return fallback
}

export default apiClient
