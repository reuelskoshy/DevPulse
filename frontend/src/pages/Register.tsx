import { useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import { Link, useNavigate } from 'react-router-dom'
import { Envelope, Eye, EyeSlash, LockKey, User, WarningCircle } from '@phosphor-icons/react'
import { useAuth } from '../context/useAuth'
import { DoubleBezel } from '../components/DoubleBezel'
import { IslandButton } from '../components/IslandButton'
import { Reveal } from '../components/Reveal'
import '../styles/theme.css'

export default function Register() {
  const navigate = useNavigate()
  const { register } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')

    if (password.length < 12) {
      setError('Password must be at least 12 characters')
      return
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setLoading(true)

    try {
      await register(name, email, password)
      navigate('/app/dashboard')
    } catch (err) {
      const message = axios.isAxiosError<{ message?: string }>(err) ? err.response?.data?.message : undefined
      setError(message || 'Registration failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="dp-theme relative min-h-screen overflow-hidden px-4 py-24 sm:py-32"
      style={{
        backgroundImage:
          'radial-gradient(40% 35% at 10% 0%, rgba(52,211,153,0.14), transparent), radial-gradient(35% 30% at 100% 10%, rgba(139,92,246,0.14), transparent)',
      }}
    >
      <div className="mx-auto grid w-full max-w-5xl items-center gap-12 lg:grid-cols-2 lg:gap-20">
        <Reveal className="order-2 lg:order-1">
          <div className="flex flex-col gap-6 text-center lg:text-left">
            <span
              className="inline-flex w-fit items-center justify-center self-center rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em] lg:self-start"
              style={{ color: 'var(--accent)', background: 'var(--accent-soft)' }}
            >
              DevPulse
            </span>
            <h1 className="text-3xl font-semibold leading-tight tracking-tight text-[var(--text)] sm:text-4xl">
              See what your team shipped, in plain language.
            </h1>
            <p className="mx-auto max-w-md text-base leading-relaxed text-[var(--text-muted)] lg:mx-0">
              Connect GitHub, sync on demand, and get an AI-generated read on delivery pace and risk
              &mdash; no black box dashboard to decode.
            </p>
          </div>
        </Reveal>

        <Reveal delay={0.1} className="order-1 lg:order-2">
          <DoubleBezel innerClassName="flex flex-col gap-8 p-8 sm:p-10">
            <div>
              <h2 className="text-2xl font-semibold text-[var(--text)]">Create account</h2>
              <p className="mt-1.5 text-sm text-[var(--text-muted)]">Get started with DevPulse today.</p>
            </div>

            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
              {error && (
                <div
                  className="flex items-center gap-2.5 rounded-2xl px-4 py-3 text-sm ring-1 ring-white/5"
                  style={{ background: 'var(--danger-soft)', color: 'var(--danger)' }}
                >
                  <WarningCircle weight="light" className="h-4 w-4 shrink-0" />
                  {error}
                </div>
              )}

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="register-name"
                  className="text-xs font-medium uppercase tracking-[0.08em] text-[var(--text-dim)]"
                >
                  Name
                </label>
                <div
                  className="relative flex items-center rounded-2xl ring-1 ring-white/10 transition-colors duration-700 ease-fluid focus-within:ring-white/25"
                  style={{ background: 'var(--surface-2)' }}
                >
                  <User weight="light" className="pointer-events-none absolute left-4 h-4 w-4 text-[var(--text-dim)]" />
                  <input
                    id="register-name"
                    type="text"
                    placeholder="Dev User"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                    autoFocus
                    autoComplete="name"
                    className="w-full rounded-2xl bg-transparent py-3 pl-11 pr-4 text-sm text-[var(--text)] placeholder:text-[var(--text-dim)] outline-none"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="register-email"
                  className="text-xs font-medium uppercase tracking-[0.08em] text-[var(--text-dim)]"
                >
                  Email
                </label>
                <div
                  className="relative flex items-center rounded-2xl ring-1 ring-white/10 transition-colors duration-700 ease-fluid focus-within:ring-white/25"
                  style={{ background: 'var(--surface-2)' }}
                >
                  <Envelope weight="light" className="pointer-events-none absolute left-4 h-4 w-4 text-[var(--text-dim)]" />
                  <input
                    id="register-email"
                    type="email"
                    placeholder="dev@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoComplete="email"
                    className="w-full rounded-2xl bg-transparent py-3 pl-11 pr-4 text-sm text-[var(--text)] placeholder:text-[var(--text-dim)] outline-none"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="register-password"
                  className="text-xs font-medium uppercase tracking-[0.08em] text-[var(--text-dim)]"
                >
                  Password
                </label>
                <div
                  className="relative flex items-center rounded-2xl ring-1 ring-white/10 transition-colors duration-700 ease-fluid focus-within:ring-white/25"
                  style={{ background: 'var(--surface-2)' }}
                >
                  <LockKey weight="light" className="pointer-events-none absolute left-4 h-4 w-4 text-[var(--text-dim)]" />
                  <input
                    id="register-password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="At least 12 characters"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    autoComplete="new-password"
                    className="w-full rounded-2xl bg-transparent py-3 pl-11 pr-11 text-sm text-[var(--text)] placeholder:text-[var(--text-dim)] outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    tabIndex={-1}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-4 flex h-4 w-4 items-center justify-center text-[var(--text-dim)] transition-colors duration-700 ease-fluid hover:text-[var(--text)]"
                  >
                    {showPassword ? <EyeSlash weight="light" className="h-4 w-4" /> : <Eye weight="light" className="h-4 w-4" />}
                  </button>
                </div>
                <span className="text-xs text-[var(--text-dim)]">Must be at least 12 characters long</span>
              </div>

              <div className="flex flex-col gap-2">
                <label
                  htmlFor="register-confirm"
                  className="text-xs font-medium uppercase tracking-[0.08em] text-[var(--text-dim)]"
                >
                  Confirm password
                </label>
                <div
                  className="relative flex items-center rounded-2xl ring-1 ring-white/10 transition-colors duration-700 ease-fluid focus-within:ring-white/25"
                  style={{ background: 'var(--surface-2)' }}
                >
                  <LockKey weight="light" className="pointer-events-none absolute left-4 h-4 w-4 text-[var(--text-dim)]" />
                  <input
                    id="register-confirm"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Re-enter your password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    autoComplete="new-password"
                    className="w-full rounded-2xl bg-transparent py-3 pl-11 pr-4 text-sm text-[var(--text)] placeholder:text-[var(--text-dim)] outline-none"
                  />
                </div>
              </div>

              <IslandButton type="submit" disabled={loading} className="mt-2 w-full justify-between">
                {loading ? 'Creating account…' : 'Create account'}
              </IslandButton>
            </form>

            <p className="text-center text-sm text-[var(--text-muted)]">
              Already have an account?{' '}
              <Link
                to="/login"
                className="font-medium text-[var(--text)] underline decoration-white/20 underline-offset-4 transition-colors duration-700 ease-fluid hover:decoration-white/50"
              >
                Sign in
              </Link>
            </p>
          </DoubleBezel>
        </Reveal>
      </div>
    </div>
  )
}
