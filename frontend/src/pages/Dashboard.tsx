import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { githubApi } from '../api/github'
import { insightsApi } from '../api/insights'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'

function formatDate(value: string | null): string {
  if (!value) return 'Never'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export default function Dashboard() {
  const { user, logout } = useAuth()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

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
    if (searchParams.get('connected') === 'github') {
      queryClient.invalidateQueries({ queryKey: ['github-connection'] })
      setSearchParams({}, { replace: true })
    }
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

  const metrics = [
    {
      label: 'GitHub Repos',
      value: String(connection?.repoCount ?? 0),
      trend: connection?.connected ? 'Synced' : 'Connect GitHub to sync',
      icon: (
        <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
          <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10z" />
        </svg>
      ),
      color: 'from-emerald-400 to-teal-500',
    },
    {
      label: 'Total Commits',
      value: connection?.connected ? String(connection.commitCount) : '-',
      trend: 'Last 14 days',
      icon: (
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      ),
      color: 'from-cyan-400 to-blue-500',
    },
    {
      label: 'Last Synced',
      value: connection?.connected ? formatDate(connection.lastSyncedAt) : '-',
      trend: 'GitHub activity',
      icon: (
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
      ),
      color: 'from-purple-500 to-pink-500',
    },
    {
      label: 'AI Insights',
      value: insight ? 'Generated' : '-',
      trend: insight ? formatDate(insight.generatedAt) : 'Not generated yet',
      icon: (
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
      ),
      color: 'from-amber-400 to-orange-500',
    },
  ]

  return (
    <div className="min-h-screen bg-slate-950">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-900/50 backdrop-blur-xl sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div>
            <div className="text-xs font-semibold tracking-[0.2em] text-cyan-400">DEVPULSE</div>
            <h1 className="text-xl font-bold mt-1">Dashboard</h1>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-sm text-slate-400">
              {user?.email}
            </div>
            <Button variant="ghost" size="sm" onClick={logout}>
              Sign Out
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-8">
        {/* Welcome Banner */}
        <Card variant="glass" className="mb-8 relative overflow-hidden">
          <div className="absolute top-0 right-0 w-64 h-64 bg-cyan-500/5 rounded-full blur-3xl" />
          <div className="relative">
            <h2 className="text-2xl font-bold mb-2">
              Welcome back, {user?.email?.split('@')[0]}! 👋
            </h2>
            <p className="text-slate-400">
              Your developer command center is ready. Connect your GitHub account to unlock powerful insights.
            </p>
          </div>
        </Card>

        {/* Metrics Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          {metrics.map((metric, index) => (
            <Card key={index} variant="glass" hover className="animate-fade-in" style={{ animationDelay: `${index * 50}ms` }}>
              <div className="flex items-start justify-between mb-4">
                <div className={`p-3 rounded-lg bg-gradient-to-br ${metric.color} bg-opacity-10`}>
                  <div className={`text-transparent bg-clip-text bg-gradient-to-r ${metric.color}`}>
                    {metric.icon}
                  </div>
                </div>
              </div>
              <div className="text-3xl font-bold mb-1">{metric.value}</div>
              <div className="text-sm text-slate-400">{metric.label}</div>
              <div className="text-xs text-slate-500 mt-2">{metric.trend}</div>
            </Card>
          ))}
        </div>

        {/* AI Insight */}
        {insight && (
          <Card variant="glass" className="mb-8">
            <h3 className="text-xl font-semibold mb-2">Latest Insight</h3>
            <p className="text-sm text-slate-300 leading-relaxed">{insight.summary}</p>
          </Card>
        )}

        {/* Quick Actions */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card variant="glass">
            <h3 className="text-xl font-semibold mb-4">Quick Actions</h3>
            <div className="space-y-3">
              <Button
                variant="secondary"
                className="w-full justify-start"
                size="lg"
                loading={connectMutation.isPending || syncMutation.isPending}
                onClick={() => (connection?.connected ? syncMutation.mutate() : connectMutation.mutate())}
              >
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10z" />
                </svg>
                {connection?.connected ? 'Sync Now' : 'Connect GitHub Account'}
              </Button>
              {connection?.connected && (
                <Button
                  variant="ghost"
                  className="w-full justify-start"
                  loading={generateInsightMutation.isPending}
                  onClick={() => generateInsightMutation.mutate()}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  Generate Insight
                </Button>
              )}
              <Button variant="ghost" className="w-full justify-start">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
                View Profile
              </Button>
            </div>
          </Card>

          <Card variant="glass">
            <h3 className="text-xl font-semibold mb-4">Getting Started</h3>
            <div className="space-y-4 text-sm text-slate-400">
              <div className="flex items-start gap-3">
                <div className="w-6 h-6 rounded-full bg-cyan-500/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <svg className="w-4 h-4 text-cyan-400" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                </div>
                <div>
                  <div className="font-medium text-slate-300 mb-1">Account Created</div>
                  <div>Your DevPulse account is active and ready.</div>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className={`w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 ${connection?.connected ? 'bg-cyan-500/20 text-cyan-400' : 'bg-slate-700 text-slate-500'}`}>
                  {connection?.connected ? (
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                    </svg>
                  ) : (
                    '2'
                  )}
                </div>
                <div>
                  <div className="font-medium text-slate-300 mb-1">Connect GitHub</div>
                  <div>Link your GitHub account to start syncing your data.</div>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className={`w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 ${insight ? 'bg-cyan-500/20 text-cyan-400' : 'bg-slate-700 text-slate-500'}`}>
                  {insight ? (
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                    </svg>
                  ) : (
                    '3'
                  )}
                </div>
                <div>
                  <div className="font-medium text-slate-300 mb-1">Explore Insights</div>
                  <div>Discover AI-powered insights about your development activity.</div>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}
