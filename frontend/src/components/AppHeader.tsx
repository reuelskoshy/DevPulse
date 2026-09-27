import { Link, NavLink } from 'react-router-dom'
import { Flask, GearSix, IdentificationCard, SignOut, SquaresFour, UsersThree } from '@phosphor-icons/react'
import type { Icon } from '@phosphor-icons/react'
import { useAuth } from '../context/useAuth'
import { canManagePeople, canViewTeam } from '../types/auth'
import { BrandMark } from './BrandMark'
import { IslandButton } from './IslandButton'

interface AppNavItem {
  to: string
  label: string
  icon: Icon
}

const NAV_LINK_BASE =
  'inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium transition-all duration-700 ease-fluid sm:px-3.5'

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return isActive
    ? `${NAV_LINK_BASE} bg-white/10 text-white shadow-[inset_0_1px_1px_rgba(255,255,255,0.08)]`
    : `${NAV_LINK_BASE} text-white/55 hover:bg-white/[0.04] hover:text-white`
}

/**
 * Floating glass-pill header for the signed-in app: brand, Overview / Team / People nav
 * (Team for managers and admins, People for real admins), the account email and Sign out. Demo
 * sessions get a slim read-only banner underneath.
 */
export function AppHeader() {
  const { user, logout, logoutTo } = useAuth()

  const items: AppNavItem[] = [
    { to: '/app/dashboard', label: 'Overview', icon: SquaresFour },
    ...(canViewTeam(user?.role) ? [{ to: '/app/team', label: 'Team', icon: UsersThree }] : []),
    ...(canManagePeople(user) ? [{ to: '/app/people', label: 'People', icon: IdentificationCard }] : []),
  ]

  return (
    <>
      <header className="sticky top-0 z-40 px-4 pt-6">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-2 rounded-full bg-white/[0.04] py-2 pl-4 pr-2 ring-1 ring-white/10 backdrop-blur-2xl sm:gap-4">
          <Link
            to="/app/dashboard"
            aria-label="DevPulse home"
            className="flex shrink-0 items-center gap-2 text-sm font-semibold text-white"
          >
            <BrandMark />
            <span className="hidden min-[420px]:inline">DevPulse</span>
          </Link>

          <nav
            aria-label="Primary"
            className="flex min-w-0 items-center gap-1 rounded-full bg-white/[0.03] p-1 ring-1 ring-white/[0.06]"
          >
            {items.map(({ to, label, icon: ItemIcon }) => (
              <NavLink key={to} to={to} end className={navLinkClass}>
                <ItemIcon weight="light" className="hidden h-4 w-4 sm:block" />
                {label}
              </NavLink>
            ))}
          </nav>

          <div className="flex min-w-0 shrink-0 items-center gap-3">
            <span className="hidden max-w-[14rem] truncate text-sm text-white/60 md:inline">{user?.email}</span>
            <NavLink
              to="/app/settings"
              aria-label="Settings"
              title="Settings"
              className={({ isActive }) =>
                `flex h-9 w-9 items-center justify-center rounded-full text-white ring-1 transition-all duration-700 ease-fluid hover:bg-white/10 active:scale-[0.98] ${
                  isActive ? 'bg-white/10 ring-white/20' : 'bg-white/5 ring-white/10'
                }`
              }
            >
              <GearSix weight="light" className="h-4 w-4" />
            </NavLink>
            <span className="hidden sm:block">
              <IslandButton variant="ghost" icon={<SignOut weight="light" className="h-4 w-4" />} onClick={logout}>
                Sign out
              </IslandButton>
            </span>
            <button
              type="button"
              onClick={logout}
              aria-label="Sign out"
              className="flex h-9 w-9 items-center justify-center rounded-full bg-white/5 text-white ring-1 ring-white/10 transition-all duration-700 ease-fluid hover:bg-white/10 active:scale-[0.98] sm:hidden"
            >
              <SignOut weight="light" className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>

      {user?.demo && (
        <div className="px-4 pt-3">
          <div
            role="note"
            className="mx-auto flex w-full max-w-5xl flex-col items-start gap-3 rounded-[1.25rem] px-4 py-3 ring-1 ring-[var(--accent-2)]/25 sm:flex-row sm:items-center sm:justify-between sm:rounded-full sm:py-1.5 sm:pl-2 sm:pr-1.5"
            style={{ background: 'var(--accent-2-soft)' }}
          >
            <p className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2 text-xs text-white/80">
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[10px] font-medium uppercase tracking-[0.2em]"
                style={{ background: 'var(--accent-2-soft)', color: 'var(--accent-2)' }}
              >
                <Flask weight="light" className="h-3.5 w-3.5" />
                Live demo
              </span>
              <span>You&rsquo;re exploring a sample team with generated data. Everything is read-only.</span>
            </p>
            <IslandButton className="shrink-0 py-1! pl-4! text-xs!" onClick={() => logoutTo('/register')}>
              Create your own account
            </IslandButton>
          </div>
        </div>
      )}
    </>
  )
}
