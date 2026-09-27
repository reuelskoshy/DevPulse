import { Component } from 'react'
import type { ReactNode } from 'react'
import { ArrowClockwise, SquaresFour, WarningCircle } from '@phosphor-icons/react'
import { DoubleBezel } from './DoubleBezel'
import { IslandButton } from './IslandButton'

/** sessionStorage key holding the time of the last automatic reload (the loop guard). */
const AUTO_RELOAD_KEY = 'devpulse:chunk-reload-at'
/** A chunk failure within this long of an automatic reload shows the card instead of reloading again. */
const AUTO_RELOAD_GUARD_MS = 30_000

/**
 * True for the errors a stale lazy chunk produces after a redeploy: the old hashed
 * URL is rewritten to index.html, so import() rejects (the wording differs per
 * browser) or Vite's preload helper fails first.
 */
function isChunkLoadError(error: unknown): boolean {
  if (!(error instanceof Error)) return false
  return /dynamically imported module|importing a module script failed|unable to preload css|chunkloaderror|loading (css )?chunk .+ failed/i.test(
    `${error.name} ${error.message}`,
  )
}

/** False when storage is unavailable too: without the guard nothing could stop a reload loop. */
function canAutoReload(): boolean {
  try {
    const last = Number(window.sessionStorage.getItem(AUTO_RELOAD_KEY))
    return !Number.isFinite(last) || Date.now() - last > AUTO_RELOAD_GUARD_MS
  } catch {
    return false
  }
}

function markAutoReload(): boolean {
  try {
    window.sessionStorage.setItem(AUTO_RELOAD_KEY, String(Date.now()))
    return true
  } catch {
    return false
  }
}

interface RouteErrorBoundaryProps {
  children: ReactNode
  /** Rendered while an automatic reload is under way, so the error card never flashes. */
  reloadingFallback?: ReactNode
}

interface RouteErrorBoundaryState {
  hasError: boolean
  staleChunk: boolean
  autoReloading: boolean
}

/**
 * Error boundary for lazily loaded routes. A stale chunk after a redeploy reloads
 * the page once on its own (guarded against loops via sessionStorage); any other
 * error, or a repeat failure, shows a glass card with a Reload action instead of
 * unmounting the whole app to a blank screen.
 */
export class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { hasError: false, staleChunk: false, autoReloading: false }

  static getDerivedStateFromError(error: unknown): RouteErrorBoundaryState {
    const staleChunk = isChunkLoadError(error)
    return { hasError: true, staleChunk, autoReloading: staleChunk && canAutoReload() }
  }

  componentDidCatch() {
    if (!this.state.autoReloading) return
    if (markAutoReload()) {
      window.location.reload()
    } else {
      // The guard couldn't be recorded, so a reload might loop: show the card instead.
      this.setState({ autoReloading: false })
    }
  }

  private handleReload = () => {
    window.location.reload()
  }

  render() {
    const { hasError, staleChunk, autoReloading } = this.state
    if (!hasError) return this.props.children
    if (autoReloading) return this.props.reloadingFallback ?? null

    return (
      <div className="dp-theme flex min-h-[100dvh] items-center justify-center px-4 py-16">
        <DoubleBezel className="w-full max-w-md" innerClassName="flex flex-col items-start gap-6 p-8 sm:p-10">
          <span
            className="flex h-11 w-11 items-center justify-center rounded-full"
            style={{ background: 'var(--accent-2-soft)' }}
          >
            <WarningCircle weight="light" className="h-5 w-5" style={{ color: 'var(--accent-2)' }} />
          </span>
          <div role="alert">
            <h1 className="text-xl font-semibold tracking-tight text-white">This page didn&rsquo;t load</h1>
            <p className="mt-2 text-sm leading-relaxed text-[var(--text-muted)]">
              {staleChunk
                ? 'DevPulse was updated after this tab was opened. Reload to get the latest version.'
                : 'Something went wrong while showing this page. Reloading usually fixes it.'}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <IslandButton onClick={this.handleReload} icon={<ArrowClockwise weight="light" className="h-4 w-4" />}>
              Reload
            </IslandButton>
            <IslandButton
              to="/app/dashboard"
              variant="ghost"
              icon={<SquaresFour weight="light" className="h-4 w-4" />}
            >
              Back to overview
            </IslandButton>
          </div>
        </DoubleBezel>
      </div>
    )
  }
}
