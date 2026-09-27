import { useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MagnifyingGlass } from '@phosphor-icons/react'
import { useAuth } from '../context/useAuth'
import { apiErrorMessage } from '../api/client'
import { usersApi } from '../api/users'
import type { TeamMember, UpdateRolePayload } from '../api/users'
import { canManagePeople } from '../types/auth'
import type { UserRole } from '../types/auth'
import { AppHeader } from '../components/AppHeader'
import { Eyebrow, SkeletonBar } from '../components/activity'
import { DoubleBezel } from '../components/DoubleBezel'
import { Notice } from '../components/Notice'
import type { NoticeState } from '../components/Notice'
import { Reveal } from '../components/Reveal'
import { formatCount, pluralize } from '../lib/format'
import { initialsOf, roleLabel } from '../lib/team'
import '../styles/theme.css'

const ROLES: UserRole[] = ['MEMBER', 'MANAGER', 'ADMIN']
const PEOPLE_KEY = ['team-members'] as const

const SELECT_CLASS =
  'w-full rounded-full bg-white/[0.04] px-3.5 py-2 text-xs text-white ring-1 ring-white/10 transition-all duration-700 ease-fluid hover:bg-white/[0.07] focus:outline-none focus-visible:ring-[var(--accent)]/60 disabled:cursor-not-allowed disabled:opacity-50 [&>option]:bg-[#0b0d10]'

function displayName(member: TeamMember): string {
  return member.name.trim() || member.email
}

/* ------------------------------------------------------------------ row */

interface PersonRowProps {
  member: TeamMember
  self: boolean
  managers: TeamMember[]
  busy: boolean
  onRoleChange: (member: TeamMember, payload: UpdateRolePayload) => void
  onStatusChange: (member: TeamMember, active: boolean) => void
}

function PersonRow({ member, self, managers, busy, onRoleChange, onStatusChange }: PersonRowProps) {
  const name = displayName(member)
  // A user can never report to themselves; the server also rejects reporting cycles.
  const managerOptions = managers.filter((manager) => manager.id !== member.id)

  return (
    <DoubleBezel
      size="md"
      innerClassName={`grid grid-cols-1 gap-4 p-4 transition-opacity duration-700 ease-fluid sm:p-5 md:grid-cols-[minmax(0,1fr)_9rem_12rem_auto] md:items-center ${busy ? 'opacity-60' : ''}`}
    >
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden="true"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold ring-1 ring-white/10"
          style={
            self
              ? { background: 'var(--accent-soft)', color: 'var(--accent)' }
              : { background: 'var(--surface-3)', color: 'var(--text-muted)' }
          }
        >
          {initialsOf(name)}
        </span>
        <div className="min-w-0">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="min-w-0 truncate text-sm font-semibold text-white">{name}</span>
            {self && <Pill tone="accent">You</Pill>}
            {!member.activeStatus && <Pill tone="neutral">Inactive</Pill>}
          </div>
          <p className="mt-0.5 truncate text-xs text-white/50">{member.email}</p>
        </div>
      </div>

      <label className="min-w-0">
        <span className="mb-1 block text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)] md:sr-only">Role</span>
        <select
          aria-label={`Role for ${name}`}
          className={SELECT_CLASS}
          value={member.role}
          disabled={busy || self}
          title={self ? 'You cannot change your own role.' : undefined}
          onChange={(event) =>
            onRoleChange(member, { role: event.target.value as UserRole, parentId: member.parentId })
          }
        >
          {ROLES.map((role) => (
            <option key={role} value={role}>
              {roleLabel(role)}
            </option>
          ))}
        </select>
      </label>

      <label className="min-w-0">
        <span className="mb-1 block text-[10px] uppercase tracking-[0.2em] text-[var(--text-muted)] md:sr-only">
          Reports to
        </span>
        <select
          aria-label={`Manager for ${name}`}
          className={SELECT_CLASS}
          value={member.parentId ?? ''}
          disabled={busy}
          onChange={(event) => onRoleChange(member, { role: member.role, parentId: event.target.value || null })}
        >
          <option value="">No manager</option>
          {managerOptions.map((manager) => (
            <option key={manager.id} value={manager.id}>
              {displayName(manager)}
            </option>
          ))}
        </select>
      </label>

      <button
        type="button"
        disabled={busy || (self && member.activeStatus)}
        title={self && member.activeStatus ? 'You cannot deactivate your own account.' : undefined}
        onClick={() => onStatusChange(member, !member.activeStatus)}
        className={`justify-self-start rounded-full px-3.5 py-2 text-xs font-medium ring-1 transition-all duration-700 ease-fluid disabled:cursor-not-allowed disabled:opacity-50 md:justify-self-end ${
          member.activeStatus
            ? 'text-white/70 ring-white/15 hover:bg-white/10 hover:text-white'
            : 'bg-[var(--accent-soft)] text-[var(--accent)] ring-[var(--accent)]/30 hover:brightness-110'
        }`}
      >
        {member.activeStatus ? 'Deactivate' : 'Reactivate'}
      </button>
    </DoubleBezel>
  )
}

function Pill({ children, tone }: { children: string; tone: 'accent' | 'neutral' }) {
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

function PeopleSkeleton() {
  return (
    <div className="grid gap-3" aria-hidden="true">
      {[0, 1, 2, 3].map((index) => (
        <DoubleBezel key={index} size="md" innerClassName="flex items-center gap-3 p-5">
          <SkeletonBar className="h-10 w-10 rounded-full" />
          <div className="flex-1 space-y-2">
            <SkeletonBar className="h-3 w-40" />
            <SkeletonBar className="h-3 w-56" />
          </div>
        </DoubleBezel>
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ page */

export default function People() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [notice, setNotice] = useState<NoticeState | null>(null)
  const isAdmin = canManagePeople(user)

  const { data, error, isPending, isError, refetch } = useQuery({
    queryKey: PEOPLE_KEY,
    queryFn: usersApi.list,
    enabled: isAdmin,
  })

  const onSaved = (updated: TeamMember, message: string) => {
    queryClient.setQueryData<TeamMember[]>(PEOPLE_KEY, (current) =>
      current?.map((member) => (member.id === updated.id ? updated : member)),
    )
    // Roles and reporting lines decide who appears on the Team page.
    void queryClient.invalidateQueries({ queryKey: ['team-activity'] })
    setNotice({ tone: 'success', message })
  }
  const onFailed = (mutationError: unknown) =>
    setNotice({ tone: 'error', message: apiErrorMessage(mutationError, "Couldn't save that change. Please try again.") })

  const roleMutation = useMutation({
    mutationFn: ({ member, payload }: { member: TeamMember; payload: UpdateRolePayload }) =>
      usersApi.updateRole(member.id, payload),
    onSuccess: (updated) =>
      onSaved(updated, `Saved ${displayName(updated)}. A new role applies from their next sign-in.`),
    onError: onFailed,
  })

  const statusMutation = useMutation({
    mutationFn: ({ member, active }: { member: TeamMember; active: boolean }) =>
      usersApi.updateStatus(member.id, {
        activeStatus: active,
        activeStatusReason: active ? null : 'Deactivated by an admin',
      }),
    onSuccess: (updated) =>
      onSaved(updated, `${displayName(updated)} is now ${updated.activeStatus ? 'active' : 'inactive'}.`),
    onError: onFailed,
  })

  const busyId =
    (roleMutation.isPending ? roleMutation.variables?.member.id : undefined) ??
    (statusMutation.isPending ? statusMutation.variables?.member.id : undefined)

  const managers = useMemo(
    () => (data ?? []).filter((member) => member.role !== 'MEMBER' && member.activeStatus),
    [data],
  )

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase()
    const sorted = [...(data ?? [])].sort((a, b) => displayName(a).localeCompare(displayName(b)))
    if (!needle) return sorted
    return sorted.filter(
      (member) => member.name.toLowerCase().includes(needle) || member.email.toLowerCase().includes(needle),
    )
  }, [data, query])

  if (!isAdmin) {
    return <Navigate to="/app/dashboard" replace />
  }

  const total = data?.length ?? 0
  const inactive = data?.filter((member) => !member.activeStatus).length ?? 0

  return (
    <div className="dp-theme relative isolate min-h-[100dvh] overflow-x-clip">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[640px]"
        style={{
          backgroundImage:
            'radial-gradient(40% 35% at 10% 0%, rgba(139,92,246,0.14), transparent), radial-gradient(35% 30% at 100% 10%, rgba(52,211,153,0.12), transparent)',
        }}
      />

      <AppHeader />

      <main className="mx-auto max-w-5xl px-4">
        <section className="pt-12 sm:pt-16">
          <Reveal>
            <Eyebrow tone="accent-2">People</Eyebrow>
            <h1 className="mt-5 text-3xl font-semibold tracking-tight text-white sm:text-4xl lg:text-5xl">
              Roles and reporting lines.
            </h1>
            <p className="mt-4 max-w-2xl text-base text-white/60 sm:text-lg">
              Choose who manages whom and who can see the whole organization. Changes save immediately; a new role
              applies from that person&rsquo;s next sign-in.
            </p>
          </Reveal>

          <Reveal delay={0.05} className="mt-8 flex flex-wrap items-center gap-4">
            <label className="relative w-full max-w-xs">
              <span className="sr-only">Search people</span>
              <MagnifyingGlass
                weight="light"
                className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-white/40"
              />
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search by name or email"
                className="w-full rounded-full bg-white/[0.04] py-2 pl-10 pr-4 text-sm text-white ring-1 ring-white/10 placeholder:text-white/35 focus:outline-none focus-visible:ring-[var(--accent)]/60"
              />
            </label>
            {data && (
              <span className="text-xs text-[var(--text-muted)]">
                {formatCount(total)} {pluralize(total, 'person', 'people')}
                {inactive > 0 && ` · ${formatCount(inactive)} inactive`}
              </span>
            )}
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

          {isError && (
            <Notice
              tone="error"
              message={apiErrorMessage(error, "Couldn't load people. Please try again.")}
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

          {isPending && <PeopleSkeleton />}

          {data && (
            <div className="grid gap-3">
              {visible.map((member) => (
                <PersonRow
                  key={member.id}
                  member={member}
                  self={member.id === user?.id}
                  managers={managers}
                  busy={busyId === member.id}
                  onRoleChange={(target, payload) => roleMutation.mutate({ member: target, payload })}
                  onStatusChange={(target, active) => statusMutation.mutate({ member: target, active })}
                />
              ))}
              {visible.length === 0 && (
                <p className="py-10 text-center text-sm text-[var(--text-muted)]">No one matches “{query.trim()}”.</p>
              )}
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
