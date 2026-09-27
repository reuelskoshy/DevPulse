import { useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import {
  ArrowClockwise,
  GitBranch,
  GitCommit,
  GithubLogo,
  LinkBreak,
  PlugsConnected,
  UserPlus,
  UsersThree,
} from '@phosphor-icons/react'
import { useAuth } from '../context/useAuth'
import { apiErrorMessage } from '../api/client'
import { teamApi } from '../api/team'
import type { TeamActivity, TeamMemberActivity, TeamRangeDays } from '../api/team'
import { AppHeader } from '../components/AppHeader'
import { Eyebrow, RangeControl, SkeletonBar, StatTile } from '../components/activity'
import type { StatTileProps } from '../components/activity'
import { DEFAULT_DAYS, parseDays } from '../lib/activity'
import { DoubleBezel } from '../components/DoubleBezel'
import { Notice } from '../components/Notice'
import { Reveal } from '../components/Reveal'
import { Sparkline } from '../components/charts/Sparkline'
import { TeamActivityChart } from '../components/charts/TeamActivityChart'
import {
  formatCount,
  formatInstant,
  formatRelativeTime,
  formatUtcDayLong,
  formatUtcDayShort,
  formatUtcRange,
  pluralize,
} from '../lib/format'
import { findPeakDay, initialsOf, roleLabel } from '../lib/team'
import '../styles/theme.css'

function scopePhrase(role: string | undefined): string {
  if (role === 'ADMIN') return 'across everyone in your organization'
  if (role === 'MANAGER') return 'for you and your direct reports'
  return 'for your own account'
}

/* ------------------------------------------------------------------ totals */

function TotalsBento({ data }: { data: TeamActivity }) {
  const { totals } = data
  const members = Math.max(totals.members, 0)
  const tiles: StatTileProps[] = [
    {
      label: 'Commits',
      value: formatCount(totals.commits),
      caption: `in the last ${data.days} ${pluralize(data.days, 'day')}`,
      icon: GitCommit,
      tone: 'accent',
      hero: true,
    },
    {
      label: 'Active contributors',
      value: formatCount(totals.activeMembers),
      of: members,
      caption: 'committed at least once',
      icon: UsersThree,
      tone: 'accent-2',
      ratio: members > 0 ? totals.activeMembers / members : 0,
    },
    {
      label: 'Repos touched',
      value: formatCount(totals.reposTouched),
      caption: 'with commits in range',
      icon: GitBranch,
      tone: 'accent-2',
    },
    {
      label: 'GitHub connected',
      value: formatCount(totals.connectedMembers),
      of: members,
      caption: `${pluralize(totals.connectedMembers, 'account')} linked`,
      icon: PlugsConnected,
      tone: 'accent',
      ratio: members > 0 ? totals.connectedMembers / members : 0,
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

function ActivityCard({ data }: { data: TeamActivity }) {
  const peak = useMemo(() => findPeakDay(data.daily), [data.daily])
  const average = data.days > 0 ? data.totals.commits / data.days : 0
  const chartLabel = `Team commits per day, ${formatUtcRange(data.from, data.to)} (UTC)`
  const chartSummary = peak
    ? `${formatCount(data.totals.commits)} ${pluralize(data.totals.commits, 'commit')} in total. Peak of ${formatCount(peak.commits)} on ${formatUtcDayLong(peak.date)}; daily average ${average.toFixed(1)}.`
    : 'No commits in this range yet.'

  return (
    <DoubleBezel innerClassName="p-6 sm:p-8">
      <div className="flex flex-wrap items-end justify-between gap-6">
        <div>
          <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Team activity</span>
          <h2 className="mt-2 text-xl font-semibold tracking-tight text-white">Commits per day</h2>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            {data.totals.commits === 0 ? 'No commits in this range yet.' : 'Everyone in view, combined. UTC days.'}
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
        <TeamActivityChart daily={data.daily} label={chartLabel} summary={chartSummary} />
      </div>
    </DoubleBezel>
  )
}

/* ------------------------------------------------------------------ members */

function Avatar({ name, avatarUrl, self }: { name: string; avatarUrl: string | null; self: boolean }) {
  const [failed, setFailed] = useState(false)

  if (avatarUrl && !failed) {
    return (
      <img
        src={avatarUrl}
        alt=""
        loading="lazy"
        referrerPolicy="no-referrer"
        onError={() => setFailed(true)}
        className="h-11 w-11 shrink-0 rounded-full object-cover ring-1 ring-white/10"
      />
    )
  }

  return (
    <span
      aria-hidden="true"
      className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-semibold ring-1 ring-white/10"
      style={
        self
          ? { background: 'var(--accent-soft)', color: 'var(--accent)' }
          : { background: 'var(--surface-3)', color: 'var(--text-muted)' }
      }
    >
      {initialsOf(name)}
    </span>
  )
}

function Badge({ children, tone }: { children: ReactNode; tone: 'accent' | 'neutral' }) {
  return (
    <span
      className="inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-[0.2em]"
      style={
        tone === 'accent'
          ? { background: 'var(--accent-soft)', color: 'var(--accent)' }
          : { background: 'rgba(255,255,255,0.06)', color: 'var(--text-muted)' }
      }
    >
      {children}
    </span>
  )
}

interface MemberStatProps {
  label: string
  children: ReactNode
  title?: string
}

function MemberStat({ label, children, title }: MemberStatProps) {
  return (
    <div className="min-w-0">
      <dt className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">{label}</dt>
      <dd className="mt-1.5 text-sm font-semibold text-white" title={title || undefined}>
        {children}
      </dd>
    </div>
  )
}

interface MemberCardProps {
  member: TeamMemberActivity
  days: number
  sparkMax: number
  now: number
}

function MemberCard({ member, days, sparkMax, now }: MemberCardProps) {
  const displayName = member.name.trim() || member.email
  const lastCommit = formatRelativeTime(member.lastCommitAt, now)

  return (
    <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col gap-5 p-5 sm:p-6">
      <div className="flex items-start gap-3">
        <Avatar key={member.avatarUrl ?? 'initials'} name={displayName} avatarUrl={member.avatarUrl} self={member.self} />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
            <h3 className="min-w-0 truncate text-base font-semibold text-white">{displayName}</h3>
            {member.self && <Badge tone="accent">You</Badge>}
            {!member.active && <Badge tone="neutral">Inactive</Badge>}
          </div>
          <div className="mt-1.5 flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1.5 text-xs text-white/50">
            <span>{roleLabel(member.role)}</span>
            {member.connected ? (
              <span className="inline-flex min-w-0 items-center gap-1 text-white/70">
                <GithubLogo weight="light" className="h-3.5 w-3.5 shrink-0" />
                <span className="truncate">{member.githubLogin ?? 'Connected'}</span>
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 rounded-full bg-white/[0.04] px-2.5 py-0.5 text-[11px] text-[var(--text-muted)] ring-1 ring-white/[0.06]">
                <LinkBreak weight="light" className="h-3 w-3" />
                Not connected
              </span>
            )}
          </div>
        </div>
      </div>

      <dl className="grid grid-cols-3 gap-3">
        <MemberStat label="Commits">{formatCount(member.commits)}</MemberStat>
        <MemberStat label="Active days">
          {formatCount(member.activeDays)}
          <span className="ml-1 font-normal text-[var(--text-muted)]">/ {days}</span>
        </MemberStat>
        <MemberStat label="Last commit" title={formatInstant(member.lastCommitAt)}>
          {lastCommit ?? <span className="font-normal text-[var(--text-muted)]">No commits in range</span>}
        </MemberStat>
      </dl>

      {member.topRepos.length > 0 && (
        <ul aria-label="Top repositories" className="flex min-w-0 flex-wrap gap-1.5">
          {member.topRepos.map((repo) => (
            <li
              key={repo.fullName}
              className="inline-flex min-w-0 max-w-full items-center gap-2 rounded-full bg-white/[0.04] px-2.5 py-1 font-mono text-[11px] text-white/70 ring-1 ring-white/[0.06]"
            >
              <span className="truncate">{repo.fullName}</span>
              <span className="shrink-0 tabular-nums text-[var(--text-muted)]">{formatCount(repo.commits)}</span>
            </li>
          ))}
        </ul>
      )}

      {/* No label: commits and active days are printed above, so it stays aria-hidden. */}
      <Sparkline className="mt-auto" daily={member.daily} max={sparkMax} />
    </DoubleBezel>
  )
}

function EmptyReports() {
  return (
    <DoubleBezel size="md" innerClassName="flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center sm:p-8">
      <span
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
        style={{ background: 'var(--accent-2-soft)' }}
      >
        <UserPlus weight="light" className="h-5 w-5" style={{ color: 'var(--accent-2)' }} />
      </span>
      <div>
        <h3 className="text-base font-semibold text-white">It&rsquo;s just you for now</h3>
        <p className="mt-1 text-sm text-white/60">
          No direct reports yet — an admin can assign people to you.
        </p>
      </div>
    </DoubleBezel>
  )
}

function MembersSection({ data, now, showEmptyReports }: { data: TeamActivity; now: number; showEmptyReports: boolean }) {
  // One shared y-scale, so sparkline heights compare honestly across people.
  const sparkMax = useMemo(
    () => data.members.reduce((max, member) => Math.max(max, ...member.daily.map((day) => day.commits)), 0),
    [data.members],
  )
  const count = data.members.length

  return (
    <section className="pt-16 sm:pt-24">
      <Reveal>
        <Eyebrow tone="accent-2">People</Eyebrow>
        <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">
            {formatCount(count)} {pluralize(count, 'person', 'people')} in view
          </h2>
          <p className="text-xs text-[var(--text-muted)]">Sorted by commits. Sparklines share one scale.</p>
        </div>
      </Reveal>

      {showEmptyReports && (
        <Reveal className="mt-8">
          <EmptyReports />
        </Reveal>
      )}

      <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
        {data.members.map((member, index) => (
          <Reveal key={member.userId} delay={0.05 * Math.min(index, 5)} className="h-full">
            <MemberCard member={member} days={data.days} sparkMax={sparkMax} now={now} />
          </Reveal>
        ))}
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ loading */

function TeamSkeleton() {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">Loading team activity…</span>
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
        <div className="mt-16 grid grid-cols-1 gap-4 sm:mt-24 md:grid-cols-2">
          {[0, 1, 2, 3].map((index) => (
            <DoubleBezel key={index} size="md" innerClassName="flex h-60 flex-col gap-5 p-6">
              <div className="flex items-center gap-3">
                <div className="h-11 w-11 rounded-full bg-white/[0.06]" />
                <div className="flex-1">
                  <SkeletonBar className="h-3 w-32" />
                  <SkeletonBar className="mt-2 h-2 w-20" />
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3">
                {[0, 1, 2].map((cell) => (
                  <div key={cell}>
                    <SkeletonBar className="h-2 w-14" />
                    <SkeletonBar className="mt-2 h-3 w-10" />
                  </div>
                ))}
              </div>
              <div className="mt-auto h-11 rounded-xl bg-white/[0.03]" />
            </DoubleBezel>
          ))}
        </div>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ page */

export default function Team() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const days = parseDays(searchParams.get('days'))

  const { data, error, isPending, isError, isFetching, isPlaceholderData, refetch, dataUpdatedAt } = useQuery({
    queryKey: ['team-activity', days],
    queryFn: () => teamApi.getActivity(days),
    // Switching ranges holds the previous render (dimmed) instead of flashing skeletons.
    placeholderData: keepPreviousData,
  })

  // While a new range loads, the placeholder (previous range) comes with dataUpdatedAt 0, which
  // would read "just now" on every card. Keep measuring from the last real fetch instead.
  const lastFetchedAtRef = useRef(0)
  if (!isPlaceholderData && dataUpdatedAt > 0) lastFetchedAtRef.current = dataUpdatedAt
  const relativeNow = lastFetchedAtRef.current || Date.now()

  const setDays = (next: TeamRangeDays) => {
    const params = new URLSearchParams(searchParams)
    if (next === DEFAULT_DAYS) params.delete('days')
    else params.set('days', String(next))
    setSearchParams(params, { replace: true })
  }

  const showEmptyReports = Boolean(
    data && !isPlaceholderData && user?.role === 'MANAGER' && data.members.every((member) => member.self),
  )

  let subtitle: string
  if (data && !isPlaceholderData) {
    subtitle = `Commits from ${formatUtcRange(data.from, data.to)} (UTC), ${scopePhrase(user?.role)}.`
  } else {
    subtitle = `Commits over the last ${days} days, ${scopePhrase(user?.role)}.`
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
        <section className="pt-12 sm:pt-16">
          <Reveal>
            <Eyebrow>Team</Eyebrow>
            <h1 className="mt-5 text-3xl font-semibold tracking-tight text-white sm:text-4xl lg:text-5xl">
              Who&rsquo;s shipping, and where.
            </h1>
            <p className="mt-4 max-w-2xl text-base text-white/60 sm:text-lg">{subtitle}</p>
          </Reveal>

          <Reveal delay={0.05} className="mt-8 flex flex-wrap items-center gap-4">
            <RangeControl value={days} onChange={setDays} thumbId="team-range-thumb" />
            <span aria-live="polite" className="inline-flex items-center gap-1.5 text-xs text-[var(--text-muted)]">
              {isFetching && data && (
                <>
                  <ArrowClockwise weight="light" className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />
                  Updating…
                </>
              )}
            </span>
          </Reveal>
        </section>

        <div className="pb-20 pt-10 sm:pb-28">
          {isError && (
            <Notice
              tone="error"
              message={apiErrorMessage(error, "Couldn't load team activity. Please try again.")}
              className="mb-6 max-w-2xl"
              action={
                <button
                  type="button"
                  onClick={() => void refetch()}
                  className="-my-1 shrink-0 rounded-full px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15 transition-all duration-700 ease-fluid hover:bg-white/10 hover:text-white"
                >
                  Try again
                </button>
              }
            />
          )}

          {isPending && <TeamSkeleton />}

          {data && (
            <div
              aria-busy={isPlaceholderData}
              className={`transition-opacity duration-700 ease-fluid ${isPlaceholderData ? 'opacity-50' : 'opacity-100'}`}
            >
              <TotalsBento data={data} />
              <Reveal delay={0.1} className="mt-4">
                <ActivityCard data={data} />
              </Reveal>
              <MembersSection data={data} now={relativeNow} showEmptyReports={showEmptyReports} />
            </div>
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
