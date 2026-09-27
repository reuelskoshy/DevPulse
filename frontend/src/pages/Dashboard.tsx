import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/useAuth'
import { apiErrorMessage } from '../api/client'
import { githubApi } from '../api/github'
import { insightsApi } from '../api/insights'
import { DoubleBezel } from '../components/DoubleBezel'
import { IslandButton } from '../components/IslandButton'
import { Reveal } from '../components/Reveal'
import {
  GithubLogo,
  GitCommit,
  ClockCounterClockwise,
  Sparkle,
  CheckCircle,
  Circle,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import '../styles/theme.css'

function formatDate(value: string | null | undefined): string {
  if (!value) return 'Never'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

type NoticeTone = 'success' | 'error'

interface NoticeState {
  tone: NoticeTone
  message: string
}

type DashboardAction = 'connect' | 'sync' | 'insight'

/**
 * Maps the OAuth callback's ?github_error reason to fixed copy. The raw param
 * is never rendered — anything unrecognised gets the generic 'failed' message.
 */
function githubErrorMessage(reason: string): string {
  switch (reason) {
    case 'denied':
      return 'GitHub connection was cancelled.'
    case 'expired':
      return 'That GitHub connection request expired. Please try connecting again.'
    case 'already_linked':
      return 'That GitHub account is already connected to a different DevPulse account. Sign in with that account to use it.'
    default:
      return "Couldn't connect GitHub. Please try again."
  }
}

interface NoticeProps extends NoticeState {
  onDismiss?: () => void
  className?: string
}

/** Soft tinted status pill-card (same shape as Login's error box). */
function Notice({ tone, message, onDismiss, className = '' }: NoticeProps) {
  const isError = tone === 'error'
  const Icon = isError ? WarningCircle : CheckCircle

  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={`flex items-start gap-2 rounded-2xl px-4 py-3 text-sm ring-1 transition-all duration-700 ease-fluid starting:translate-y-1 starting:opacity-0 ${
        isError
          ? 'bg-[var(--danger-soft)] text-[var(--danger)] ring-[var(--danger)]/30'
          : 'bg-[var(--accent-soft)] text-[var(--accent)] ring-[var(--accent)]/30'
      } ${className}`}
    >
      <Icon weight="light" className="mt-0.5 h-4 w-4 shrink-0" />
      <span className="min-w-0 flex-1">{message}</span>
      {onDismiss && (
        <button
          type="button"
          aria-label="Dismiss"
          onClick={onDismiss}
          className="-my-1 -mr-2 flex h-7 w-7 shrink-0 items-center justify-center rounded-full opacity-70 transition-all duration-700 ease-fluid hover:bg-white/10 hover:opacity-100"
        >
          <X weight="light" className="h-4 w-4" />
        </button>
      )}
    </div>
  )
}

export default function Dashboard() {
  const { user, logout } = useAuth()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [lastAction, setLastAction] = useState<DashboardAction | null>(null)

  const { data: connection } = useQuery({
    queryKey: ['github-connection'],
    queryFn: githubApi.getConnection,
  })

  const { data: insight } = useQuery({
    queryKey: ['latest-insight'],
    queryFn: insightsApi.getLatest,
    enabled: connection?.connected === true,
  })

  useEffect(() => {
    const connectedGithub = searchParams.get('connected') === 'github'
    const githubError = searchParams.get('github_error')
    if (!connectedGithub && githubError === null) return

    if (connectedGithub) {
      queryClient.invalidateQueries({ queryKey: ['github-connection'] })
    }
    setNotice(
      githubError !== null
        ? { tone: 'error', message: githubErrorMessage(githubError) }
        : { tone: 'success', message: 'GitHub connected.' }
    )

    const next = new URLSearchParams(searchParams)
    next.delete('connected')
    next.delete('github_error')
    setSearchParams(next, { replace: true })
  }, [searchParams, queryClient, setSearchParams])

  const connectMutation = useMutation({
    mutationFn: githubApi.authorize,
    onSuccess: (data) => {
      window.location.href = data.authorizationUrl
    },
  })

  const syncMutation = useMutation({
    mutationFn: githubApi.sync,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['github-connection'] })
    },
  })

  const generateInsightMutation = useMutation({
    mutationFn: insightsApi.generate,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['latest-insight'] })
    },
  })

  // Only the most recently started action's outcome is shown. Starting any action
  // swaps `lastAction`, and that mutation is pending, so the previous message clears.
  const startAction = (action: DashboardAction) => {
    setLastAction(action)
    if (action === 'connect') connectMutation.mutate()
    else if (action === 'sync') syncMutation.mutate()
    else generateInsightMutation.mutate()
  }

  let actionFeedback: NoticeState | null = null
  if (lastAction === 'connect' && connectMutation.isError) {
    actionFeedback = {
      tone: 'error',
      message: apiErrorMessage(connectMutation.error, "Couldn't start the GitHub connection. Please try again."),
    }
  } else if (lastAction === 'sync' && syncMutation.isError) {
    actionFeedback = {
      tone: 'error',
      message: apiErrorMessage(syncMutation.error, 'Sync failed. Please try again.'),
    }
  } else if (lastAction === 'sync' && syncMutation.isSuccess) {
    actionFeedback = {
      tone: 'success',
      message: `Synced ${syncMutation.data.reposSynced} repos · ${syncMutation.data.commitsSynced} new commits.`,
    }
  } else if (lastAction === 'insight' && generateInsightMutation.isError) {
    actionFeedback = {
      tone: 'error',
      message: apiErrorMessage(generateInsightMutation.error, "Couldn't generate an insight. Please try again."),
    }
  }

  const connected = connection?.connected === true
  const name = user?.email?.split('@')[0]

  // onSuccess navigates away to GitHub, so treat success as still in flight.
  const connecting = connectMutation.isPending || connectMutation.isSuccess
  const syncLabel = syncMutation.isPending ? 'Syncing…' : 'Sync now'
  const connectLabel = connecting ? 'Connecting…' : 'Connect GitHub'

  const steps = [
    {
      num: '01',
      title: 'Account created',
      body: 'Your DevPulse account is active and ready.',
      done: true,
    },
    {
      num: '02',
      title: 'Connect GitHub',
      body: 'Link your GitHub account to start syncing your data.',
      done: connected,
    },
    {
      num: '03',
      title: 'Explore insights',
      body: 'Discover AI-powered insights about your development activity.',
      done: Boolean(insight),
    },
  ]

  return (
    <div className="dp-theme relative min-h-[100dvh] overflow-x-hidden">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[640px]"
        style={{
          backgroundImage:
            'radial-gradient(40% 35% at 10% 0%, rgba(52,211,153,0.14), transparent), radial-gradient(35% 30% at 100% 10%, rgba(139,92,246,0.14), transparent)',
        }}
      />

      <header className="sticky top-0 z-40 px-4 pt-6">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-4 rounded-full bg-white/[0.04] px-4 py-3 ring-1 ring-white/10 backdrop-blur-2xl">
          <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-white">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="shrink-0">
              <path
                d="M2 12H7L9.5 5L14.5 19L17 12H22"
                stroke="var(--accent)"
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            <span className="truncate">DevPulse</span>
          </div>

          <div className="flex min-w-0 items-center gap-3 sm:gap-4">
            <span className="hidden truncate text-sm text-white/60 sm:inline">{user?.email}</span>
            <IslandButton variant="ghost" icon={null} onClick={logout}>
              Sign out
            </IslandButton>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4">
        {notice && (
          <Notice
            tone={notice.tone}
            message={notice.message}
            onDismiss={() => setNotice(null)}
            className="mt-8"
          />
        )}

        <Reveal>
          <section className="pt-12 pb-20 sm:pt-16 sm:pb-28">
            <span
              className="inline-flex rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em]"
              style={{ background: 'var(--accent-soft)', color: 'var(--accent)' }}
            >
              Your command center
            </span>
            <h1 className="mt-5 text-3xl font-semibold tracking-tight text-white sm:text-4xl lg:text-5xl">
              Welcome back{name ? `, ${name}` : ''}.
            </h1>
            <p className="mt-4 max-w-2xl text-base text-white/60 sm:text-lg">
              {connected
                ? 'Your GitHub account is connected. Sync whenever you want a fresh read, and generate an insight when you\'re ready.'
                : 'Connect your GitHub account to start syncing commit activity and generating plain-language insights.'}
            </p>

            <div className="mt-12 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-6">
              <Reveal delay={0.05 * 0} className="lg:col-span-4">
                <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-6 p-6">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] uppercase tracking-[0.2em] text-white/40">Commits</span>
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full"
                      style={{ background: 'var(--accent-soft)' }}
                    >
                      <GitCommit weight="light" className="h-4 w-4" style={{ color: 'var(--accent)' }} />
                    </span>
                  </div>
                  <div className="mt-auto">
                    <div className="text-4xl font-semibold text-white lg:text-5xl">
                      {connected ? connection.commitCount : '–'}
                    </div>
                    <div className="mt-1 text-xs text-white/40">last 14 days</div>
                  </div>
                </DoubleBezel>
              </Reveal>

              <Reveal delay={0.05 * 1} className="lg:col-span-2">
                <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-6 p-6">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] uppercase tracking-[0.2em] text-white/40">Last synced</span>
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full"
                      style={{ background: 'var(--accent-2-soft)' }}
                    >
                      <ClockCounterClockwise weight="light" className="h-4 w-4" style={{ color: 'var(--accent-2)' }} />
                    </span>
                  </div>
                  <div className="mt-auto">
                    <div className="text-lg font-semibold text-white">
                      {connected ? formatDate(connection.lastSyncedAt) : '–'}
                    </div>
                    <div className="mt-1 text-xs text-white/40">GitHub activity</div>
                  </div>
                </DoubleBezel>
              </Reveal>

              <Reveal delay={0.05 * 2} className="lg:col-span-3">
                <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-6 p-6">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] uppercase tracking-[0.2em] text-white/40">AI insight</span>
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full"
                      style={{ background: 'var(--accent-2-soft)' }}
                    >
                      <Sparkle weight="light" className="h-4 w-4" style={{ color: 'var(--accent-2)' }} />
                    </span>
                  </div>
                  <div className="mt-auto">
                    <div className="text-lg font-semibold text-white">{insight ? 'Generated' : '–'}</div>
                    <div className="mt-1 text-xs text-white/40">
                      {insight ? formatDate(insight.generatedAt) : 'not generated yet'}
                    </div>
                  </div>
                </DoubleBezel>
              </Reveal>

              <Reveal delay={0.05 * 3} className="lg:col-span-3">
                <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-6 p-6">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] uppercase tracking-[0.2em] text-white/40">GitHub repos</span>
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full"
                      style={{ background: 'var(--accent-soft)' }}
                    >
                      <GithubLogo weight="light" className="h-4 w-4" style={{ color: 'var(--accent)' }} />
                    </span>
                  </div>
                  <div className="mt-auto">
                    <div className="text-4xl font-semibold text-white">{connected ? connection.repoCount : '–'}</div>
                    <div className="mt-1 text-xs text-white/40">{connected ? 'synced' : 'connect to sync'}</div>
                  </div>
                </DoubleBezel>
              </Reveal>
            </div>

            <Reveal delay={0.1} className="mt-10 flex flex-wrap items-center gap-4">
              <IslandButton
                type="button"
                disabled={connecting || syncMutation.isPending}
                onClick={() => startAction(connected ? 'sync' : 'connect')}
              >
                {connected ? syncLabel : connectLabel}
              </IslandButton>
              {connected && (
                <IslandButton
                  variant="ghost"
                  type="button"
                  disabled={generateInsightMutation.isPending}
                  onClick={() => startAction('insight')}
                >
                  {generateInsightMutation.isPending ? 'Generating…' : 'Generate insight'}
                </IslandButton>
              )}
              {connected && (
                <IslandButton
                  variant="ghost"
                  type="button"
                  icon={null}
                  disabled={connecting || syncMutation.isPending}
                  onClick={() => startAction('connect')}
                >
                  {connecting ? 'Reconnecting…' : 'Reconnect GitHub'}
                </IslandButton>
              )}
            </Reveal>

            {actionFeedback && (
              <Notice
                tone={actionFeedback.tone}
                message={actionFeedback.message}
                onDismiss={() => setLastAction(null)}
                className="mt-4 max-w-2xl"
              />
            )}
          </section>
        </Reveal>

        <Reveal>
          <section className="pb-20 sm:pb-28">
            <span
              className="inline-flex rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em]"
              style={{ background: 'var(--accent-2-soft)', color: 'var(--accent-2)' }}
            >
              Latest insight
            </span>
            <h2 className="mt-5 text-2xl font-semibold tracking-tight text-white sm:text-3xl">
              What DevPulse is seeing right now.
            </h2>

            {insight ? (
              <DoubleBezel className="mt-8 max-w-2xl" innerClassName="flex flex-col gap-3 p-8">
                <span
                  className="inline-flex w-fit rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em]"
                  style={{ background: 'var(--accent-soft)', color: 'var(--accent)' }}
                >
                  Generated insight
                </span>
                <h3 className="text-lg font-semibold text-white">{formatDate(insight.generatedAt)}</h3>
                <p className="text-sm leading-relaxed text-white/60">{insight.summary}</p>
              </DoubleBezel>
            ) : (
              <p className="mt-8 max-w-xl text-sm text-white/40">
                {connected
                  ? 'No insight yet — generate one above once you\'ve synced some activity.'
                  : 'Connect GitHub and sync first, then generate your first insight.'}
              </p>
            )}
          </section>
        </Reveal>

        <Reveal>
          <section className="pb-20 sm:pb-28">
            <span
              className="inline-flex rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em]"
              style={{ background: 'var(--accent-soft)', color: 'var(--accent)' }}
            >
              Getting started
            </span>
            <h2 className="mt-5 text-2xl font-semibold tracking-tight text-white sm:text-3xl">
              Three steps to your first insight.
            </h2>

            <div className="mt-10 grid grid-cols-1 gap-4 md:grid-cols-3">
              {steps.map((step, index) => (
                <Reveal key={step.num} delay={0.05 * index}>
                  <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-3 p-6">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] uppercase tracking-[0.2em] text-white/40">{step.num}</span>
                      {step.done ? (
                        <CheckCircle weight="light" className="h-5 w-5" style={{ color: 'var(--accent)' }} />
                      ) : (
                        <Circle weight="light" className="h-5 w-5 text-white/20" />
                      )}
                    </div>
                    <h3 className="text-lg font-semibold text-white">{step.title}</h3>
                    <p className="text-sm text-white/60">{step.body}</p>
                    <span className="mt-auto text-[10px] font-medium uppercase tracking-[0.2em] text-white/40">
                      {step.done ? 'done' : 'not yet'}
                    </span>
                  </DoubleBezel>
                </Reveal>
              ))}
            </div>
          </section>
        </Reveal>

        <footer className="flex flex-col items-center gap-1 pb-16 text-center text-xs text-white/30 sm:flex-row sm:justify-between sm:text-left">
          <span>DevPulse</span>
          <span>Developer intelligence for engineering teams</span>
        </footer>
      </main>
    </div>
  )
}
