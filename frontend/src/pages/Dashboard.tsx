import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/useAuth'
import { apiErrorMessage } from '../api/client'
import { githubApi } from '../api/github'
import type { GitHubConnection } from '../api/github'
import { insightsApi } from '../api/insights'
import type { Insight, InsightFacts } from '../api/insights'
import { teamApi } from '../api/team'
import type { TeamActivity, TeamMemberActivity, TeamRangeDays } from '../api/team'
import { canViewTeam } from '../types/auth'
import { AppHeader } from '../components/AppHeader'
import { Eyebrow, RangeControl, SkeletonBar, StatTile } from '../components/activity'
import type { StatTileProps } from '../components/activity'
import { DEFAULT_DAYS, parseDays } from '../lib/activity'
import { DoubleBezel } from '../components/DoubleBezel'
import { IslandButton } from '../components/IslandButton'
import { Notice } from '../components/Notice'
import type { NoticeState } from '../components/Notice'
import { Reveal } from '../components/Reveal'
import { ContributionHeatmap } from '../components/charts/ContributionHeatmap'
import { TeamActivityChart } from '../components/charts/TeamActivityChart'
import {
  formatCount,
  formatHours,
  formatInstant,
  formatRelativeTime,
  formatUtcDayLong,
  formatUtcDayShort,
  formatUtcRange,
  pluralize,
} from '../lib/format'
import { findPeakDay } from '../lib/team'
import {
  ArrowClockwise,
  CalendarCheck,
  CheckCircle,
  Circle,
  ClockCounterClockwise,
  GitBranch,
  GitCommit,
  GitPullRequest,
  GithubLogo,
  Lightbulb,
  LockSimple,
  Pulse,
  Sparkle,
  SquaresFour,
  UsersThree,
} from '@phosphor-icons/react'
import '../styles/theme.css'

type DashboardAction = 'connect' | 'sync' | 'insight'

const HEATMAP_DAYS = 90

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

/* ------------------------------------------------------------------ totals */

function PersonalBento({ self, connection, days, now }: {
  self: TeamMemberActivity
  connection: GitHubConnection
  days: number
  now: number
}) {
  const tiles: StatTileProps[] = [
    {
      label: 'Commits',
      value: formatCount(self.commits),
      caption: `in the last ${days} ${pluralize(days, 'day')}`,
      icon: GitCommit,
      tone: 'accent',
      hero: true,
    },
    {
      label: 'Active days',
      value: formatCount(self.activeDays),
      of: days,
      caption: 'with at least one commit',
      icon: CalendarCheck,
      tone: 'accent-2',
      ratio: days > 0 ? self.activeDays / days : 0,
    },
    {
      label: 'Repos synced',
      value: formatCount(connection.repoCount),
      caption: connection.login ? `from @${connection.login}` : 'from GitHub',
      icon: GitBranch,
      tone: 'accent-2',
    },
    {
      label: 'Last synced',
      value: formatRelativeTime(connection.lastSyncedAt, now) ?? 'Never',
      caption: connection.lastSyncedAt ? 'GitHub activity' : 'sync to pull your commits',
      icon: ClockCounterClockwise,
      tone: 'accent',
      title: formatInstant(connection.lastSyncedAt),
    },
  ]

  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
      {tiles.map((tile, index) => (
        <Reveal key={tile.label} delay={0.05 * index}>
          <StatTile {...tile} />
        </Reveal>
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ activity chart */

function PersonalActivityCard({ self, data }: { self: TeamMemberActivity; data: TeamActivity }) {
  const peak = useMemo(() => findPeakDay(self.daily), [self.daily])
  const average = data.days > 0 ? self.commits / data.days : 0
  const chartLabel = `Your commits per day, ${formatUtcRange(data.from, data.to)} (UTC)`
  const chartSummary = peak
    ? `${formatCount(self.commits)} ${pluralize(self.commits, 'commit')} in total. Peak of ${formatCount(peak.commits)} on ${formatUtcDayLong(peak.date)}; daily average ${average.toFixed(1)}.`
    : 'No commits in this range yet.'

  return (
    <DoubleBezel innerClassName="p-6 sm:p-8">
      <div className="flex flex-wrap items-end justify-between gap-6">
        <div>
          <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Your activity</span>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-white">Commits per day</h2>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            {self.commits === 0 ? 'No commits in this range yet. Sync to pull the latest.' : 'Across every synced repo. UTC days.'}
          </p>
        </div>
        <dl className="flex gap-8">
          <div>
            <dt className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Peak day</dt>
            <dd className="mt-1 text-sm font-semibold text-white">
              {peak ? (
                <>
                  {formatCount(peak.commits)}
                  <span className="ml-1.5 font-normal text-white/50">{formatUtcDayShort(peak.date)}</span>
                </>
              ) : (
                '–'
              )}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Daily average</dt>
            <dd className="mt-1 text-sm font-semibold text-white">{average.toFixed(1)}</dd>
          </div>
        </dl>
      </div>
      <div className="mt-6">
        <TeamActivityChart daily={self.daily} label={chartLabel} summary={chartSummary} />
      </div>
    </DoubleBezel>
  )
}

/* ------------------------------------------------------------------ heatmap + pull requests */

function HeatmapCard({ self, isLoading }: { self: TeamMemberActivity | undefined; isLoading: boolean }) {
  const activeDays = self?.activeDays ?? 0
  const commits = self?.commits ?? 0
  const label = self
    ? `Contribution heatmap for the last ${self.daily.length} days: ${formatCount(commits)} ${pluralize(commits, 'commit')} on ${formatCount(activeDays)} ${pluralize(activeDays, 'day')}.`
    : 'Contribution heatmap loading.'

  return (
    <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-5 p-6 sm:p-8">
      <div className="flex items-start justify-between gap-3">
        <div>
          <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Last 90 days</span>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-white">Contribution heatmap</h2>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            {self
              ? `${formatCount(commits)} ${pluralize(commits, 'commit')} on ${formatCount(activeDays)} of ${self.daily.length} days.`
              : 'Loading your history…'}
          </p>
        </div>
        <span
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
          style={{ background: 'var(--accent-soft)' }}
        >
          <SquaresFour weight="light" className="h-4 w-4" style={{ color: 'var(--accent)' }} />
        </span>
      </div>
      {self ? (
        <ContributionHeatmap daily={self.daily} label={label} />
      ) : (
        <div
          aria-hidden="true"
          className={`h-40 rounded-2xl bg-white/[0.03] ${isLoading ? 'animate-pulse motion-reduce:animate-none' : ''}`}
        />
      )}
    </DoubleBezel>
  )
}

function PullRequestStat({ label, value, caption }: { label: string; value: string; caption?: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">{label}</dt>
      <dd className="mt-1.5 text-2xl font-semibold tabular-nums text-white">
        {value}
        {caption && <span className="ml-1.5 text-xs font-normal text-[var(--text-muted)]">{caption}</span>}
      </dd>
    </div>
  )
}

function PullRequestCard({ self, days }: { self: TeamMemberActivity; days: number }) {
  const stats = self.pullRequests
  const empty = stats.opened + stats.merged + stats.open + stats.reviews === 0

  return (
    <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-6 p-6 sm:p-8">
      <div className="flex items-start justify-between gap-3">
        <div>
          <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Pull requests</span>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-white">Review flow</h2>
          <p className="mt-1 text-xs text-[var(--text-muted)]">Last {days} {pluralize(days, 'day')}.</p>
        </div>
        <span
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
          style={{ background: 'var(--accent-2-soft)' }}
        >
          <GitPullRequest weight="light" className="h-4 w-4" style={{ color: 'var(--accent-2)' }} />
        </span>
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-5">
        <PullRequestStat label="Merged" value={formatCount(stats.merged)} caption={`of ${formatCount(stats.opened)} opened`} />
        <PullRequestStat label="Reviews given" value={formatCount(stats.reviews)} />
        <PullRequestStat label="Time to merge" value={formatHours(stats.medianHoursToMerge)} caption="median" />
        <PullRequestStat label="Open now" value={formatCount(stats.open)} />
      </dl>

      <p className="mt-auto text-xs text-[var(--text-muted)]">
        {empty
          ? 'No pull requests in this range yet. They sync alongside your commits.'
          : 'Time to merge runs from opening a PR to merging it.'}
      </p>
    </DoubleBezel>
  )
}

/* ------------------------------------------------------------------ repos + insight */

function TopReposCard({ self, now }: { self: TeamMemberActivity; now: number }) {
  const lastCommit = formatRelativeTime(self.lastCommitAt, now)

  return (
    <DoubleBezel size="md" innerClassName="flex flex-col gap-5 p-6 sm:p-8">
      <div className="flex items-center justify-between gap-3">
        <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Where you&rsquo;re shipping</span>
        <span
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
          style={{ background: 'var(--accent-soft)' }}
        >
          <GithubLogo weight="light" className="h-4 w-4" style={{ color: 'var(--accent)' }} />
        </span>
      </div>

      {self.topRepos.length > 0 ? (
        <ul aria-label="Top repositories" className="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-3">
          {self.topRepos.map((repo) => (
            <li key={repo.fullName} className="min-w-0">
              <div className="flex items-baseline justify-between gap-3">
                <span className="truncate font-mono text-xs text-white/80">{repo.fullName}</span>
                <span className="shrink-0 text-sm font-semibold tabular-nums text-white">
                  {formatCount(repo.commits)}
                </span>
              </div>
              <div aria-hidden="true" className="mt-2 h-1 w-full overflow-hidden rounded-full" style={{ background: 'var(--accent-soft)' }}>
                <div
                  className="h-full rounded-full transition-[width] duration-700 ease-fluid"
                  style={{
                    width: `${self.commits > 0 ? Math.round((repo.commits / self.commits) * 100) : 0}%`,
                    background: 'var(--chart-1)',
                  }}
                />
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-[var(--text-muted)]">No commits in this range yet.</p>
      )}

      <p className="text-xs text-[var(--text-muted)]" title={formatInstant(self.lastCommitAt) || undefined}>
        {lastCommit ? `Last commit ${lastCommit}.` : 'No commits in this range.'}
      </p>
    </DoubleBezel>
  )
}

function factChips(facts: InsightFacts): string[] {
  const chips = [
    `${formatCount(facts.commits)} ${pluralize(facts.commits, 'commit')}`,
    `${facts.activeDays} of ${facts.windowDays} days active`,
  ]
  if (facts.longestStreak > 1) chips.push(`${facts.longestStreak}-day streak`)
  if (facts.busiestWeekday) chips.push(`Busiest on ${facts.busiestWeekday}s`)
  if (facts.pullRequestsMerged > 0) {
    chips.push(`${formatCount(facts.pullRequestsMerged)} ${pluralize(facts.pullRequestsMerged, 'PR')} merged`)
  }
  if (facts.medianHoursToMerge !== null) chips.push(`${formatHours(facts.medianHoursToMerge)} median to merge`)
  if (facts.pullRequestsOpen > 0) chips.push(`${formatCount(facts.pullRequestsOpen)} open`)
  chips.push(`${formatCount(facts.reviews)} ${pluralize(facts.reviews, 'review')} given`)
  return chips
}

function InsightList({ label, icon, items }: { label: string; icon: ReactNode; items: string[] }) {
  if (items.length === 0) return null
  return (
    <div className="flex flex-col gap-3">
      <span className="flex items-center gap-2 text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">
        {icon}
        {label}
      </span>
      <ul className="flex flex-col gap-2.5">
        {items.map((item) => (
          <li key={item} className="flex gap-2.5 text-sm leading-relaxed text-white/70">
            <span aria-hidden="true" className="mt-2 h-1 w-1 shrink-0 rounded-full" style={{ background: 'var(--accent-2)' }} />
            <span className="min-w-0">{item}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function InsightCard({ insight, connected }: { insight: Insight | null | undefined; connected: boolean }) {
  const details = insight?.details ?? null
  return (
    <DoubleBezel size="md" innerClassName="flex flex-col gap-4 p-6 sm:p-8">
      <div className="flex items-center justify-between gap-3">
        <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Latest AI insight</span>
        <span
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
          style={{ background: 'var(--accent-2-soft)' }}
        >
          <Sparkle weight="light" className="h-4 w-4" style={{ color: 'var(--accent-2)' }} />
        </span>
      </div>
      {insight && details ? (
        <>
          <div className="flex max-w-3xl flex-col gap-3">
            {details.headline && (
              <h3 className="text-xl font-semibold leading-snug tracking-tight text-white sm:text-2xl">
                {details.headline}
              </h3>
            )}
            <p className="text-sm leading-relaxed text-white/70 sm:text-[15px]">{insight.summary}</p>
          </div>

          <ul aria-label="Insight facts" className="flex flex-wrap gap-2">
            {factChips(details.facts).map((chip) => (
              <li
                key={chip}
                className="rounded-full px-3 py-1 text-xs tabular-nums text-white/75 ring-1 ring-white/10"
                style={{ background: 'var(--accent-2-soft)' }}
              >
                {chip}
              </li>
            ))}
          </ul>

          <div className="mt-2 grid grid-cols-1 gap-8 md:grid-cols-5">
            {details.highlights.length > 0 && (
              <div className="flex flex-col gap-3 md:col-span-3">
                <span className="flex items-center gap-2 text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">
                  <Sparkle weight="light" className="h-3.5 w-3.5" aria-hidden="true" />
                  Highlights
                </span>
                <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {details.highlights.map((highlight) => (
                    <li
                      key={highlight.title}
                      className="flex flex-col gap-1.5 rounded-2xl p-4 ring-1 ring-white/10"
                      style={{ background: 'rgba(255,255,255,0.02)' }}
                    >
                      <span className="text-sm font-semibold text-white">{highlight.title}</span>
                      <span className="text-sm leading-relaxed text-white/65">{highlight.detail}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <div className={`flex flex-col gap-6 ${details.highlights.length > 0 ? 'md:col-span-2' : 'md:col-span-5'}`}>
              <InsightList
                label="How you worked"
                icon={<Pulse weight="light" className="h-3.5 w-3.5" aria-hidden="true" />}
                items={details.patterns}
              />
              <InsightList
                label="Try next"
                icon={<Lightbulb weight="light" className="h-3.5 w-3.5" aria-hidden="true" />}
                items={details.suggestions}
              />
            </div>
          </div>

          <p className="text-xs text-[var(--text-muted)]">
            Generated {formatInstant(insight.generatedAt)} from the last {details.facts.windowDays} days:{' '}
            {formatCount(insight.commitCount)} {pluralize(insight.commitCount, 'commit')} across{' '}
            {formatCount(insight.repoCount)} {pluralize(insight.repoCount, 'repo')}, plus pull requests and reviews.
            Days are counted in UTC.
          </p>
        </>
      ) : insight ? (
        <>
          <p className="text-sm leading-relaxed text-white/70">{insight.summary}</p>
          <p className="mt-auto text-xs text-[var(--text-muted)]">
            Generated {formatInstant(insight.generatedAt)} from {formatCount(insight.commitCount)}{' '}
            {pluralize(insight.commitCount, 'commit')} across {formatCount(insight.repoCount)}{' '}
            {pluralize(insight.repoCount, 'repo')}.
          </p>
        </>
      ) : (
        <p className="text-sm text-[var(--text-muted)]">
          {connected
            ? 'No insight yet. Generate one above once you’ve synced some activity.'
            : 'Connect GitHub and sync first, then generate your first insight.'}
        </p>
      )}
    </DoubleBezel>
  )
}

function TeamTeaser({ data }: { data: TeamActivity }) {
  const others = data.members.length - 1
  return (
    <DoubleBezel size="md" innerClassName="flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center sm:justify-between sm:p-8">
      <div className="flex items-center gap-4">
        <span
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
          style={{ background: 'var(--accent-2-soft)' }}
        >
          <UsersThree weight="light" className="h-5 w-5" style={{ color: 'var(--accent-2)' }} />
        </span>
        <div>
          <h3 className="text-base font-semibold text-white">
            Your team shipped {formatCount(data.totals.commits)} {pluralize(data.totals.commits, 'commit')}
            {data.totals.pullRequests.merged > 0 &&
              ` and merged ${formatCount(data.totals.pullRequests.merged)} ${pluralize(data.totals.pullRequests.merged, 'PR')}`}
          </h3>
          <p className="mt-1 text-sm text-white/60">
            {formatCount(data.totals.activeMembers)} of {formatCount(data.totals.members)} active, you plus {formatCount(others)}{' '}
            {pluralize(others, 'other')}, over the last {data.days} days.
          </p>
        </div>
      </div>
      <IslandButton variant="ghost" to={`/app/team${data.days === DEFAULT_DAYS ? '' : `?days=${data.days}`}`}>
        Open team view
      </IslandButton>
    </DoubleBezel>
  )
}

/* ------------------------------------------------------------------ not connected */

function ConnectCard({ onConnect, disabled, label }: { onConnect: () => void; disabled: boolean; label: string }) {
  return (
    <DoubleBezel innerClassName="relative overflow-hidden p-8 sm:p-12">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full blur-3xl"
        style={{ background: 'var(--accent-2-soft)' }}
      />
      <div className="relative flex flex-col items-start gap-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-4">
          <span
            className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full"
            style={{ background: 'var(--accent-soft)' }}
          >
            <GithubLogo weight="light" className="h-6 w-6" style={{ color: 'var(--accent)' }} />
          </span>
          <div>
            <h2 className="text-xl font-semibold tracking-tight text-white sm:text-2xl">Connect GitHub to light this up</h2>
            <p className="mt-2 max-w-lg text-sm text-white/60">
              DevPulse syncs your commit history, charts it day by day and writes you a plain-language read on where
              your time is going.
            </p>
          </div>
        </div>
        <IslandButton type="button" disabled={disabled} onClick={onConnect} className="shrink-0">
          {label}
        </IslandButton>
      </div>
    </DoubleBezel>
  )
}

/* ------------------------------------------------------------------ loading */

function DashboardSkeleton() {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">Loading your activity…</span>
      <div aria-hidden="true" className="animate-pulse motion-reduce:animate-none">
        <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
          {[0, 1, 2, 3].map((index) => (
            <DoubleBezel key={index} size="md" innerClassName="flex h-40 flex-col justify-between p-6">
              <SkeletonBar className="h-2 w-24" />
              <div>
                <div className="h-9 w-20 rounded-xl bg-white/[0.06]" />
                <SkeletonBar className="mt-3 h-2 w-28" />
              </div>
            </DoubleBezel>
          ))}
        </div>
        <DoubleBezel className="mt-4" innerClassName="p-6 sm:p-8">
          <SkeletonBar className="h-2 w-24" />
          <SkeletonBar className="mt-4 h-4 w-40" />
          <div className="mt-8 h-[260px] rounded-2xl bg-white/[0.03]" />
        </DoubleBezel>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ page */

export default function Dashboard() {
  const { user } = useAuth()
  // Demo sessions are read-only: the backend 403s every write, so writes are disabled up front.
  const isDemo = user?.demo === true
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const days = parseDays(searchParams.get('days'))
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const [lastAction, setLastAction] = useState<DashboardAction | null>(null)

  const { data: connection, isPending: connectionPending } = useQuery({
    queryKey: ['github-connection'],
    queryFn: githubApi.getConnection,
  })
  const connected = connection?.connected === true

  const { data: insight } = useQuery({
    queryKey: ['latest-insight'],
    queryFn: insightsApi.getLatest,
    enabled: connected,
  })

  const {
    data: activity,
    error: activityError,
    isError: activityIsError,
    isFetching: activityFetching,
    isPlaceholderData,
    refetch: refetchActivity,
    dataUpdatedAt,
  } = useQuery({
    queryKey: ['team-activity', days],
    queryFn: () => teamApi.getActivity(days),
    // Runs before GitHub is connected too: the caller's own row carries their display name.
    // Switching ranges holds the previous render (dimmed) instead of flashing skeletons.
    placeholderData: keepPreviousData,
  })
  const self = activity?.members.find((member) => member.self)

  // The heatmap always covers 90 days, whatever range the rest of the page shows.
  const { data: history, isPending: historyPending } = useQuery({
    queryKey: ['team-activity', HEATMAP_DAYS],
    queryFn: () => teamApi.getActivity(HEATMAP_DAYS),
    enabled: connected,
  })
  const selfHistory = history?.members.find((member) => member.self)

  // Relative times measure from the last real fetch, not from placeholder data (dataUpdatedAt 0).
  const lastFetchedAtRef = useRef(0)
  if (!isPlaceholderData && dataUpdatedAt > 0) lastFetchedAtRef.current = dataUpdatedAt
  const relativeNow = lastFetchedAtRef.current || Date.now()

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

  const setDays = (next: TeamRangeDays) => {
    const params = new URLSearchParams(searchParams)
    if (next === DEFAULT_DAYS) params.delete('days')
    else params.set('days', String(next))
    setSearchParams(params, { replace: true })
  }

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
      queryClient.invalidateQueries({ queryKey: ['team-activity'] })
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
    if (isDemo) return
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

  const displayName = self?.name.trim() || user?.email?.split('@')[0]
  const firstName = displayName?.split(/\s+/)[0]

  // onSuccess navigates away to GitHub, so treat success as still in flight.
  const connecting = connectMutation.isPending || connectMutation.isSuccess
  const syncLabel = syncMutation.isPending ? 'Syncing…' : 'Sync now'
  const connectLabel = connecting ? 'Connecting…' : 'Connect GitHub'

  const steps = [
    { num: '01', title: 'Account created', body: 'Your DevPulse account is active and ready.', done: true },
    { num: '02', title: 'Connect GitHub', body: 'Link your GitHub account to start syncing your data.', done: connected },
    {
      num: '03',
      title: 'Explore insights',
      body: 'Discover AI-powered insights about your development activity.',
      done: Boolean(insight),
    },
  ]
  const onboardingDone = steps.every((step) => step.done)
  const showTeamTeaser = Boolean(activity && canViewTeam(user?.role) && activity.members.length > 1)

  let subtitle: string
  if (!connected) {
    subtitle = 'Connect your GitHub account to start syncing commit activity and generating plain-language insights.'
  } else if (activity && !isPlaceholderData) {
    subtitle = `Your commits from ${formatUtcRange(activity.from, activity.to)} (UTC), with the latest AI read on top.`
  } else {
    subtitle = `Your commits over the last ${days} days, with the latest AI read on top.`
  }

  return (
    // isolate: makes the root a stacking context so the -z-10 glow paints above its background.
    // overflow-x-clip (not hidden) keeps the root from becoming a scroller, so the header can stick.
    <div className="dp-theme relative isolate min-h-[100dvh] overflow-x-clip">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[640px]"
        style={{
          backgroundImage:
            'radial-gradient(40% 35% at 10% 0%, rgba(52,211,153,0.14), transparent), radial-gradient(35% 30% at 100% 10%, rgba(139,92,246,0.14), transparent)',
        }}
      />

      <AppHeader />

      <main className="mx-auto max-w-5xl px-4">
        {notice && (
          <Notice
            tone={notice.tone}
            message={notice.message}
            onDismiss={() => setNotice(null)}
            className="mt-8"
          />
        )}

        <section className="pt-12 sm:pt-16">
          <Reveal>
            <Eyebrow>Your command center</Eyebrow>
            <h1 className="mt-5 text-3xl font-semibold tracking-tight text-white sm:text-4xl lg:text-5xl">
              Welcome back{firstName ? `, ${firstName}` : ''}.
            </h1>
            <p className="mt-4 max-w-2xl text-base text-white/60 sm:text-lg">{subtitle}</p>
          </Reveal>

          {connected && (
            <Reveal delay={0.05} className="mt-8 flex flex-wrap items-center gap-3">
              <RangeControl value={days} onChange={setDays} thumbId="dashboard-range-thumb" />
              <IslandButton
                type="button"
                disabled={isDemo || connecting || syncMutation.isPending}
                onClick={() => startAction('sync')}
                icon={
                  syncMutation.isPending ? (
                    <ArrowClockwise weight="light" className="h-4 w-4 animate-spin motion-reduce:animate-none" />
                  ) : undefined
                }
              >
                {syncLabel}
              </IslandButton>
              <IslandButton
                variant="ghost"
                type="button"
                disabled={isDemo || generateInsightMutation.isPending}
                onClick={() => startAction('insight')}
                icon={<Sparkle weight="light" className="h-4 w-4" />}
              >
                {generateInsightMutation.isPending ? 'Generating…' : 'Generate insight'}
              </IslandButton>
              <IslandButton
                variant="ghost"
                type="button"
                icon={null}
                disabled={isDemo || connecting || syncMutation.isPending}
                onClick={() => startAction('connect')}
              >
                {connecting ? 'Reconnecting…' : 'Reconnect GitHub'}
              </IslandButton>
              <span aria-live="polite" className="inline-flex items-center gap-1.5 text-xs text-[var(--text-muted)]">
                {activityFetching && activity && !syncMutation.isPending && (
                  <>
                    <ArrowClockwise weight="light" className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />
                    Updating…
                  </>
                )}
              </span>
              {isDemo && (
                <span className="inline-flex items-center gap-1.5 text-xs text-white/50">
                  <LockSimple weight="light" className="h-4 w-4" style={{ color: 'var(--accent-2)' }} />
                  Read-only in the demo.
                </span>
              )}
            </Reveal>
          )}

          {actionFeedback && (
            <Notice
              tone={actionFeedback.tone}
              message={actionFeedback.message}
              onDismiss={() => setLastAction(null)}
              className="mt-4 max-w-2xl"
            />
          )}
        </section>

        <div className="pb-20 pt-10 sm:pb-28">
          {connectionPending && <DashboardSkeleton />}

          {connection && !connected && (
            <Reveal>
              <ConnectCard
                onConnect={() => startAction('connect')}
                disabled={isDemo || connecting}
                label={connectLabel}
              />
            </Reveal>
          )}

          {connected && activityIsError && (
            <Notice
              tone="error"
              message={apiErrorMessage(activityError, "Couldn't load your activity. Please try again.")}
              className="mb-6 max-w-2xl"
              action={
                <button
                  type="button"
                  onClick={() => void refetchActivity()}
                  className="-my-1 shrink-0 rounded-full px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15 transition-all duration-700 ease-fluid hover:bg-white/10 hover:text-white"
                >
                  Try again
                </button>
              }
            />
          )}

          {connected && !activity && !activityIsError && <DashboardSkeleton />}

          {connection && connected && activity && self && (
            <div
              aria-busy={isPlaceholderData}
              className={`transition-opacity duration-700 ease-fluid ${isPlaceholderData ? 'opacity-50' : 'opacity-100'}`}
            >
              <PersonalBento self={self} connection={connection} days={activity.days} now={relativeNow} />
              <Reveal delay={0.1} className="mt-4">
                <PersonalActivityCard self={self} data={activity} />
              </Reveal>
              <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-5">
                <Reveal className="h-full md:col-span-3">
                  <HeatmapCard self={selfHistory} isLoading={historyPending} />
                </Reveal>
                <Reveal delay={0.05} className="h-full md:col-span-2">
                  <PullRequestCard self={self} days={activity.days} />
                </Reveal>
              </div>
              <Reveal className="mt-4">
                <InsightCard insight={insight} connected={connected} />
              </Reveal>
              <Reveal delay={0.05} className="mt-4">
                <TopReposCard self={self} now={relativeNow} />
              </Reveal>
              {showTeamTeaser && activity && (
                <Reveal className="mt-4">
                  <TeamTeaser data={activity} />
                </Reveal>
              )}
            </div>
          )}

          {connection && !onboardingDone && (
            <section className="pt-16 sm:pt-24">
              <Reveal>
                <Eyebrow tone="accent-2">Getting started</Eyebrow>
                <h2 className="mt-4 text-2xl font-semibold tracking-tight text-white sm:text-3xl">
                  Three steps to your first insight.
                </h2>
              </Reveal>

              <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3">
                {steps.map((step, index) => (
                  <Reveal key={step.num} delay={0.05 * index} className="h-full">
                    <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-3 p-6">
                      <div className="flex items-center justify-between">
                        <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">{step.num}</span>
                        {step.done ? (
                          <CheckCircle weight="light" className="h-5 w-5" style={{ color: 'var(--accent)' }} />
                        ) : (
                          <Circle weight="light" className="h-5 w-5 text-white/20" />
                        )}
                      </div>
                      <h3 className="text-lg font-semibold text-white">{step.title}</h3>
                      <p className="text-sm text-white/60">{step.body}</p>
                      <span className="mt-auto text-[10px] font-medium uppercase tracking-[0.2em] text-[var(--text-muted)]">
                        {step.done ? 'done' : 'not yet'}
                      </span>
                    </DoubleBezel>
                  </Reveal>
                ))}
              </div>
            </section>
          )}
        </div>

        <footer className="flex flex-col items-center gap-1 pb-16 text-center text-xs text-[var(--text-muted)] sm:flex-row sm:justify-between sm:text-left">
          <span>DevPulse</span>
          <span>Developer intelligence for engineering teams</span>
        </footer>
      </main>
    </div>
  )
}
