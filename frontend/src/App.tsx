import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './context/useAuth'
import { BrandMark } from './components/BrandMark'
import { RouteErrorBoundary } from './components/RouteErrorBoundary'
import Landing from './pages/Landing'
import Login from './pages/Login'
import Register from './pages/Register'
import './styles/theme.css'

// Dashboard and Team pull in recharts; loading them on demand keeps the public pages light.
// After a redeploy their old chunk URLs are gone, so each route sits inside RouteErrorBoundary.
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Team = lazy(() => import('./pages/Team'))
const People = lazy(() => import('./pages/People'))

/** Full-viewport session check: the pulse mark breathing inside a glass bezel. */
function FullScreenLoader() {
  return (
    <div className="dp-theme flex min-h-[100dvh] items-center justify-center px-4">
      <div role="status" className="flex flex-col items-center gap-5">
        <div className="rounded-full bg-white/[0.03] p-1.5 ring-1 ring-white/10">
          <div
            className="flex h-14 w-14 items-center justify-center rounded-full shadow-[inset_0_1px_1px_rgba(255,255,255,0.08)]"
            style={{ background: 'var(--surface)' }}
          >
            <BrandMark size={24} className="animate-pulse motion-reduce:animate-none" />
          </div>
        </div>
        <p className="text-[10px] font-medium uppercase tracking-[0.2em]" style={{ color: 'var(--text-muted)' }}>
          Loading…
        </p>
      </div>
    </div>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()

  if (loading) {
    return <FullScreenLoader />
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()

  if (loading) {
    return <FullScreenLoader />
  }

  if (user) {
    // The live demo is all about the sample team, so demo sessions land there.
    return <Navigate to={user.demo ? '/app/team' : '/app/dashboard'} replace />
  }

  return <>{children}</>
}

function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route
        path="/"
        element={
          <PublicRoute>
            <Landing />
          </PublicRoute>
        }
      />
      <Route
        path="/login"
        element={
          <PublicRoute>
            <Login />
          </PublicRoute>
        }
      />
      <Route
        path="/register"
        element={
          <PublicRoute>
            <Register />
          </PublicRoute>
        }
      />

      {/* Protected routes */}
      <Route
        path="/app/dashboard"
        element={
          <ProtectedRoute>
            <RouteErrorBoundary reloadingFallback={<FullScreenLoader />}>
              <Suspense fallback={<FullScreenLoader />}>
                <Dashboard />
              </Suspense>
            </RouteErrorBoundary>
          </ProtectedRoute>
        }
      />
      <Route
        path="/app/team"
        element={
          <ProtectedRoute>
            <RouteErrorBoundary reloadingFallback={<FullScreenLoader />}>
              <Suspense fallback={<FullScreenLoader />}>
                <Team />
              </Suspense>
            </RouteErrorBoundary>
          </ProtectedRoute>
        }
      />

      <Route
        path="/app/people"
        element={
          <ProtectedRoute>
            <RouteErrorBoundary reloadingFallback={<FullScreenLoader />}>
              <Suspense fallback={<FullScreenLoader />}>
                <People />
              </Suspense>
            </RouteErrorBoundary>
          </ProtectedRoute>
        }
      />

      {/* Catch all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
