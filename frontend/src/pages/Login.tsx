import { useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import { Link, useNavigate } from 'react-router-dom'
import { WarningCircle } from '@phosphor-icons/react'
import { useAuth } from '../context/useAuth'
import { DoubleBezel } from '../components/DoubleBezel'
import { IslandButton } from '../components/IslandButton'
import { Reveal } from '../components/Reveal'
import '../styles/theme.css'

export default function Login() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      await login(email, password)
      navigate('/app/dashboard')
    } catch (err) {
      const message = axios.isAxiosError<{ message?: string }>(err) ? err.response?.data?.message : undefined
      setError(message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="dp-theme relative min-h-[100dvh] overflow-hidden">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage:
            'radial-gradient(40% 35% at 10% 0%, rgba(52,211,153,0.14), transparent), radial-gradient(35% 30% at 100% 10%, rgba(139,92,246,0.14), transparent)',
        }}
      />

      <div className="relative grid min-h-[100dvh] grid-cols-1 min-[900px]:grid-cols-2">
        <div className="hidden flex-col justify-center px-8 py-24 min-[900px]:flex min-[900px]:px-16 lg:px-24">
          <Reveal>
            <span className="inline-flex w-fit items-center rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em] text-[var(--accent)]" style={{ background: 'var(--accent-soft)' }}>
              DevPulse
            </span>
            <h1 className="mt-6 max-w-md text-4xl font-extrabold leading-[1.05] tracking-tight text-[var(--text)] lg:text-5xl">
              Your team's commits already tell the story.
            </h1>
            <p className="mt-6 max-w-sm text-base leading-relaxed text-[var(--text-muted)]">
              Sign in to sync your GitHub activity and read the plain-language pulse check waiting for you.
            </p>
          </Reveal>
        </div>

        <div className="flex w-full flex-col items-center justify-center px-4 py-24 min-[900px]:px-16">
          <Reveal delay={0.05} className="w-full max-w-md">
            <DoubleBezel innerClassName="flex flex-col p-8 sm:p-10">
              <h2 className="text-2xl font-bold tracking-tight text-[var(--text)]">Sign in</h2>
              <p className="mt-2 text-sm text-[var(--text-muted)]">Enter your credentials to continue.</p>

              <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-5">
                {error && (
                  <div className="flex items-start gap-2 rounded-2xl bg-[var(--danger-soft)] px-4 py-3 text-sm text-[var(--danger)] ring-1 ring-[var(--danger)]/30">
                    <WarningCircle weight="light" className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}

                <div className="flex flex-col gap-2">
                  <label htmlFor="login-email" className="text-xs font-medium uppercase tracking-[0.1em] text-[var(--text-dim)]">
                    Email
                  </label>
                  <input
                    id="login-email"
                    type="email"
                    placeholder="dev@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoFocus
                    autoComplete="email"
                    className="w-full rounded-xl bg-[var(--surface-2)] px-4 py-3 text-sm text-[var(--text)] ring-1 ring-[var(--hairline-soft)] transition-shadow duration-700 ease-fluid placeholder:text-[var(--text-dim)] focus:outline-none focus:ring-2 focus:ring-[var(--accent)]"
                  />
                </div>

                <div className="flex flex-col gap-2">
                  <label htmlFor="login-password" className="text-xs font-medium uppercase tracking-[0.1em] text-[var(--text-dim)]">
                    Password
                  </label>
                  <div className="relative">
                    <input
                      id="login-password"
                      type={showPassword ? 'text' : 'password'}
                      placeholder="••••••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                      autoComplete="current-password"
                      className="w-full rounded-xl bg-[var(--surface-2)] px-4 py-3 pr-16 text-sm text-[var(--text)] ring-1 ring-[var(--hairline-soft)] transition-shadow duration-700 ease-fluid placeholder:text-[var(--text-dim)] focus:outline-none focus:ring-2 focus:ring-[var(--accent)]"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((v) => !v)}
                      tabIndex={-1}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium uppercase tracking-wide text-[var(--text-dim)] transition-colors duration-700 ease-fluid hover:text-[var(--text)]"
                    >
                      {showPassword ? 'hide' : 'show'}
                    </button>
                  </div>
                </div>

                <IslandButton type="submit" disabled={loading} className="mt-2 w-full justify-center">
                  {loading ? 'Signing in…' : 'Sign in'}
                </IslandButton>
              </form>

              <div className="mt-6 text-center text-sm text-[var(--text-muted)]">
                Don't have an account?{' '}
                <Link
                  to="/register"
                  className="font-medium text-[var(--accent)] transition-colors duration-700 ease-fluid hover:text-[var(--text)]"
                >
                  Sign up
                </Link>
              </div>
            </DoubleBezel>
          </Reveal>
        </div>
      </div>
    </div>
  )
}
