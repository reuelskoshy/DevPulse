import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/useAuth'
import { apiErrorMessage } from '../api/client'
import { digestApi } from '../api/digest'
import type { DigestPreferences, WeeklyDigest } from '../api/digest'
import { usersApi } from '../api/users'
import type { Profile } from '../api/users'
import { AppHeader } from '../components/AppHeader'
import { Eyebrow, SkeletonBar } from '../components/activity'
import { DoubleBezel } from '../components/DoubleBezel'
import { IslandButton } from '../components/IslandButton'
import { Notice } from '../components/Notice'
import type { NoticeState } from '../components/Notice'
import { Reveal } from '../components/Reveal'
import { formatCount, formatHours, formatInstant, formatUtcRange, pluralize } from '../lib/format'
import { EnvelopeSimple, FloppyDisk, LockSimple, UserCircle } from '@phosphor-icons/react'
import '../styles/theme.css'

const INPUT_CLASS =
  'w-full rounded-xl bg-white/[0.04] px-3.5 py-2.5 text-sm text-white ring-1 ring-white/10 placeholder:text-white/35 focus:outline-none focus-visible:ring-[var(--accent)]/60 disabled:cursor-not-allowed disabled:opacity-60'

const NAME_MAX = 255
const PHONE_MAX = 20
const LOCATION_MAX = 255

/* ------------------------------------------------------------------ profile */

interface ProfileFormProps {
  profile: Profile
  readOnly: boolean
  onSaved: (message: NoticeState) => void
}

function ProfileForm({ profile, readOnly, onSaved }: ProfileFormProps) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(profile.name)
  const [phoneNumber, setPhoneNumber] = useState(profile.phoneNumber ?? '')
  const [location, setLocation] = useState(profile.location ?? '')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      usersApi.updateProfile(profile.id, {
        name: name.trim(),
        phoneNumber: phoneNumber.trim(),
        location: location.trim(),
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['profile', profile.id], saved)
      // Names show up across the dashboard, team view and digest.
      void queryClient.invalidateQueries({ queryKey: ['team-activity'] })
      void queryClient.invalidateQueries({ queryKey: ['digest', 'weekly'] })
      onSaved({ tone: 'success', message: 'Profile saved.' })
    },
    onError: (failure) => onSaved({ tone: 'error', message: apiErrorMessage(failure, "Couldn't save your profile.") }),
  })

  const dirty =
    name.trim() !== profile.name ||
    phoneNumber.trim() !== (profile.phoneNumber ?? '') ||
    location.trim() !== (profile.location ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setError('Your name can’t be empty.')
      return
    }
    setError(null)
    save.mutate()
  }

  return (
    <form onSubmit={submit} noValidate className="flex h-full flex-col gap-5">
      <label className="block">
        <span className="mb-1.5 block text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Name</span>
        <input
          className={INPUT_CLASS}
          value={name}
          maxLength={NAME_MAX}
          autoComplete="name"
          disabled={readOnly}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? 'profile-name-error' : undefined}
          onChange={(event) => setName(event.target.value)}
        />
        {error && (
          <span id="profile-name-error" className="mt-1.5 block text-xs text-red-300">
            {error}
          </span>
        )}
      </label>
      <label className="block">
        <span className="mb-1.5 block text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Email</span>
        <input className={INPUT_CLASS} value={profile.email} disabled readOnly />
        <span className="mt-1.5 block text-xs text-[var(--text-muted)]">You sign in with this, so it can’t be changed here.</span>
      </label>
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <label className="block">
          <span className="mb-1.5 block text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Phone</span>
          <input
            className={INPUT_CLASS}
            value={phoneNumber}
            maxLength={PHONE_MAX}
            inputMode="tel"
            autoComplete="tel"
            placeholder="Optional"
            disabled={readOnly}
            onChange={(event) => setPhoneNumber(event.target.value)}
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Location</span>
          <input
            className={INPUT_CLASS}
            value={location}
            maxLength={LOCATION_MAX}
            autoComplete="address-level2"
            placeholder="Optional"
            disabled={readOnly}
            onChange={(event) => setLocation(event.target.value)}
          />
        </label>
      </div>
      <div className="mt-auto flex flex-wrap items-center gap-3 pt-2">
        <IslandButton
          type="submit"
          disabled={readOnly || !dirty || save.isPending}
          icon={<FloppyDisk weight="light" className="h-4 w-4" />}
        >
          {save.isPending ? 'Saving…' : 'Save profile'}
        </IslandButton>
        {readOnly && <ReadOnlyHint />}
      </div>
    </form>
  )
}

/* ------------------------------------------------------------------ digest */

interface DigestToggleProps {
  preferences: DigestPreferences
  readOnly: boolean
  onSaved: (message: NoticeState) => void
}

function DigestToggle({ preferences, readOnly, onSaved }: DigestToggleProps) {
  const queryClient = useQueryClient()
  const toggle = useMutation({
    mutationFn: (enabled: boolean) => digestApi.updatePreferences(enabled),
    onSuccess: (saved) => {
      queryClient.setQueryData(['digest', 'preferences'], saved)
      onSaved({
        tone: 'success',
        message: saved.weeklyDigestEnabled ? 'Weekly digest turned on.' : 'Weekly digest turned off.',
      })
    },
    onError: (failure) => onSaved({ tone: 'error', message: apiErrorMessage(failure, "Couldn't update the digest.") }),
  })
  const enabled = toggle.isPending ? toggle.variables : preferences.weeklyDigestEnabled

  return (
    <div className="flex h-full flex-col gap-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p id="digest-toggle-label" className="text-sm font-semibold text-white">
            Email me a weekly digest
          </p>
          <p className="mt-1 text-xs text-[var(--text-muted)]">
            Once a week: your commits and PRs, and your team’s if you manage one.
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          aria-labelledby="digest-toggle-label"
          disabled={readOnly || toggle.isPending}
          onClick={() => toggle.mutate(!preferences.weeklyDigestEnabled)}
          className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full ring-1 transition-colors duration-500 ease-fluid focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] disabled:cursor-not-allowed disabled:opacity-60 ${
            enabled ? 'bg-[var(--accent)] ring-[var(--accent)]' : 'bg-white/[0.06] ring-white/10'
          }`}
        >
          <span
            aria-hidden="true"
            className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform duration-500 ease-fluid ${
              enabled ? 'translate-x-6' : 'translate-x-1'
            }`}
          />
        </button>
      </div>

      <ul className="space-y-2 text-xs text-[var(--text-muted)]">
        {!preferences.emailDelivery && (
          <li>
            Email isn’t set up on this server yet, so digests aren’t being sent. Your choice is saved for when it is.
          </li>
        )}
        <li>
          {preferences.lastSentAt
            ? `Last sent ${formatInstant(preferences.lastSentAt)}.`
            : 'No digest has been sent to you yet.'}
        </li>
        <li>Weeks with nothing to report are skipped.</li>
      </ul>
      {readOnly && (
        <div className="mt-auto">
          <ReadOnlyHint />
        </div>
      )}
    </div>
  )
}

function ChangeBadge({ current, previous }: { current: number; previous: number }) {
  if (previous === 0) {
    return current > 0 ? <span className="text-xs text-[var(--text-muted)]">new this week</span> : null
  }
  const percent = Math.round(((current - previous) / previous) * 100)
  if (percent === 0) return <span className="text-xs text-[var(--text-muted)]">same as last week</span>
  const up = percent > 0
  return (
    <span
      className="rounded-full px-2 py-0.5 text-[11px] font-medium tabular-nums"
      style={{
        background: up ? 'var(--accent-soft)' : 'rgba(248,113,113,0.12)',
        color: up ? 'var(--accent)' : 'rgb(252,165,165)',
      }}
    >
      {up ? '+' : '−'}
      {Math.abs(percent)}% vs last week
    </span>
  )
}

function DigestLine({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">{label}</dt>
      <dd className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-white">{children}</dd>
    </div>
  )
}

function DigestPreview({ digest }: { digest: WeeklyDigest }) {
  const { you, team } = digest
  return (
    <div className="flex flex-col gap-8">
      <div>
        <h3 className="text-[10px] uppercase tracking-[0.2em] text-[var(--accent)]">You</h3>
        {you.connected ? (
          <dl className="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-3">
            <DigestLine label="Commits">
              <span className="text-2xl font-semibold tabular-nums">{formatCount(you.commits)}</span>
              <ChangeBadge current={you.commits} previous={you.previousCommits} />
            </DigestLine>
            <DigestLine label="Pull requests">
              <span className="tabular-nums">
                {formatCount(you.pullRequestsMerged)} merged · {formatCount(you.reviews)}{' '}
                {pluralize(you.reviews, 'review')}
              </span>
            </DigestLine>
            <DigestLine label="Most active in">
              <span className="min-w-0 truncate font-mono text-xs">{you.topRepo ?? '—'}</span>
            </DigestLine>
          </dl>
        ) : (
          <p className="mt-3 text-sm text-white/70">
            GitHub isn’t connected yet, so there’s nothing of yours to count. Connect it on your dashboard.
          </p>
        )}
      </div>

      {team && (
        <div>
          <h3 className="text-[10px] uppercase tracking-[0.2em] text-[var(--accent-2)]">Your team</h3>
          <dl className="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-3">
            <DigestLine label="Commits">
              <span className="text-2xl font-semibold tabular-nums">{formatCount(team.commits)}</span>
              <ChangeBadge current={team.commits} previous={team.previousCommits} />
            </DigestLine>
            <DigestLine label="Active">
              <span className="tabular-nums">
                {formatCount(team.activeMembers)} of {formatCount(team.members)}{' '}
                {pluralize(team.members, 'person', 'people')}
              </span>
            </DigestLine>
            <DigestLine label="Merged">
              <span className="tabular-nums">
                {formatCount(team.pullRequestsMerged)} {pluralize(team.pullRequestsMerged, 'PR')}
                {team.medianHoursToMerge !== null && ` · median ${formatHours(team.medianHoursToMerge)}`}
              </span>
            </DigestLine>
          </dl>
          {team.topContributors.length > 0 && (
            <p className="mt-5 text-sm text-white/70">
              <span className="text-[var(--text-muted)]">Most commits: </span>
              {team.topContributors.map((contributor) => `${contributor.name} (${contributor.commits})`).join(', ')}
            </p>
          )}
          {team.quietMembers.length > 0 && (
            <p className="mt-2 text-sm text-white/70">
              <span className="text-[var(--text-muted)]">No commits this week: </span>
              {team.quietMembers.join(', ')}. Worth a check-in?
            </p>
          )}
        </div>
      )}
    </div>
  )
}

function ReadOnlyHint() {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-[var(--text-muted)]">
      <LockSimple weight="light" className="h-3.5 w-3.5" />
      Read-only in the demo.
    </span>
  )
}

function CardHeading({ eyebrow, title, icon }: { eyebrow: string; title: string; icon: React.ReactNode }) {
  return (
    <div className="mb-6 flex items-start justify-between gap-3">
      <div>
        <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">{eyebrow}</span>
        <h2 className="mt-2 text-xl font-semibold tracking-tight text-white">{title}</h2>
      </div>
      <span
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
        style={{ background: 'var(--accent-soft)' }}
      >
        {icon}
      </span>
    </div>
  )
}

function CardSkeleton({ rows }: { rows: number }) {
  return (
    <div aria-hidden="true" className="animate-pulse space-y-5 motion-reduce:animate-none">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index}>
          <SkeletonBar className="h-2 w-16" />
          <div className="mt-2 h-10 rounded-xl bg-white/[0.04]" />
        </div>
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ page */

export default function Settings() {
  const { user } = useAuth()
  const readOnly = user?.demo === true
  const [notice, setNotice] = useState<NoticeState | null>(null)

  const profile = useQuery({
    queryKey: ['profile', user?.id],
    queryFn: () => usersApi.profile(user!.id),
    enabled: Boolean(user?.id),
  })
  const preferences = useQuery({ queryKey: ['digest', 'preferences'], queryFn: digestApi.preferences })
  const weekly = useQuery({ queryKey: ['digest', 'weekly'], queryFn: digestApi.weekly })

  return (
    <div className="dp-theme relative isolate min-h-[100dvh] overflow-x-clip">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[640px]"
        style={{
          backgroundImage:
            'radial-gradient(40% 35% at 10% 0%, rgba(52,211,153,0.14), transparent), radial-gradient(35% 30% at 100% 10%, rgba(139,92,246,0.12), transparent)',
        }}
      />

      <AppHeader />

      <main className="mx-auto max-w-5xl px-4">
        <section className="pt-12 sm:pt-16">
          <Reveal>
            <Eyebrow>Settings</Eyebrow>
            <h1 className="mt-5 text-3xl font-semibold tracking-tight text-white sm:text-4xl lg:text-5xl">
              Your account.
            </h1>
            <p className="mt-4 max-w-2xl text-base text-white/60 sm:text-lg">
              How you appear to your team, and what DevPulse emails you.
            </p>
          </Reveal>
        </section>

        <div className="pb-20 pt-10 sm:pb-28">
          <div aria-live="polite">
            {notice && (
              <Notice
                tone={notice.tone}
                message={notice.message}
                onDismiss={() => setNotice(null)}
                className="mb-6 max-w-2xl"
              />
            )}
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-5">
            <Reveal className="h-full md:col-span-3">
              <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col p-6 sm:p-8">
                <CardHeading
                  eyebrow="Profile"
                  title="About you"
                  icon={<UserCircle weight="light" className="h-4 w-4" style={{ color: 'var(--accent)' }} />}
                />
                {profile.isError ? (
                  <Notice tone="error" message={apiErrorMessage(profile.error, "Couldn't load your profile.")} />
                ) : profile.data ? (
                  <ProfileForm
                    key={profile.data.id + profile.dataUpdatedAt}
                    profile={profile.data}
                    readOnly={readOnly}
                    onSaved={setNotice}
                  />
                ) : (
                  <CardSkeleton rows={3} />
                )}
              </DoubleBezel>
            </Reveal>

            <Reveal delay={0.05} className="h-full md:col-span-2">
              <DoubleBezel size="md" className="h-full" innerClassName="flex h-full flex-col p-6 sm:p-8">
                <CardHeading
                  eyebrow="Email"
                  title="Weekly digest"
                  icon={<EnvelopeSimple weight="light" className="h-4 w-4" style={{ color: 'var(--accent)' }} />}
                />
                {preferences.isError ? (
                  <Notice
                    tone="error"
                    message={apiErrorMessage(preferences.error, "Couldn't load your email settings.")}
                  />
                ) : preferences.data ? (
                  <DigestToggle preferences={preferences.data} readOnly={readOnly} onSaved={setNotice} />
                ) : (
                  <CardSkeleton rows={2} />
                )}
              </DoubleBezel>
            </Reveal>
          </div>

          <Reveal delay={0.1} className="mt-4">
            <DoubleBezel size="md" innerClassName="p-6 sm:p-8">
              <div className="mb-6">
                <span className="text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)]">Preview</span>
                <h2 className="mt-2 text-xl font-semibold tracking-tight text-white">This week’s digest</h2>
                <p className="mt-1 text-xs text-[var(--text-muted)]">
                  {weekly.data
                    ? `${formatUtcRange(weekly.data.from, weekly.data.to)} (UTC), compared with the 7 days before.`
                    : 'The last 7 days, compared with the 7 before.'}
                </p>
              </div>
              {weekly.isError ? (
                <Notice tone="error" message={apiErrorMessage(weekly.error, "Couldn't build this week's digest.")} />
              ) : weekly.data ? (
                weekly.data.empty ? (
                  <p className="text-sm text-white/70">
                    Nothing to report for the last two weeks, so this week’s email would be skipped.
                  </p>
                ) : (
                  <DigestPreview digest={weekly.data} />
                )
              ) : (
                <CardSkeleton rows={2} />
              )}
            </DoubleBezel>
          </Reveal>
        </div>

        <footer className="flex flex-col items-center gap-1 pb-16 text-center text-xs text-[var(--text-muted)] sm:flex-row sm:justify-between sm:text-left">
          <span>DevPulse</span>
          <span>Developer intelligence for engineering teams</span>
        </footer>
      </main>
    </div>
  )
}
