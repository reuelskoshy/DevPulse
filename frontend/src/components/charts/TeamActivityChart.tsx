import { useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, ReferenceDot, Tooltip, XAxis, YAxis } from 'recharts'
import { useReducedMotion } from 'framer-motion'
import { Table } from '@phosphor-icons/react'
import type { DailyCommits } from '../../api/team'
import { formatCount, formatUtcDayShort } from '../../lib/format'
import { countAxisTicks, findPeakDay } from '../../lib/team'
import { CommitsTooltip } from './CommitsTooltip'

interface TeamActivityChartProps {
  daily: DailyCommits[]
  /** Accessible name for the plot, e.g. "Team commits per day, Sep 14 – 27, 2026 (UTC)". */
  label: string
  /** Accessible description: the headline numbers a sighted reader gets at a glance. */
  summary?: string
}

// Ticks are 11px text, so they take --text-muted (7:1 on the surface), not the dimmer --text-dim.
const AXIS_TICK = { fill: 'var(--text-muted)', fontSize: 11 }

/**
 * Single-series area chart of team commits per UTC day (dataviz skill: 2px line,
 * ~10% wash, hairline solid grid, crosshair tooltip, peak marked with a ringed
 * dot, no legend because the card title names the series), plus a table-view
 * twin so no value is tooltip-only.
 */
export function TeamActivityChart({ daily, label, summary }: TeamActivityChartProps) {
  const reduceMotion = useReducedMotion()

  const peak = useMemo(() => findPeakDay(daily), [daily])
  const yTicks = useMemo(() => countAxisTicks(peak?.commits ?? 0), [peak])

  return (
    <div>
      {/* Fixed height includes the x-axis band, so the card never gets a nested scroll. */}
      <div className="h-[260px] w-full [&_.recharts-cartesian-axis-tick-value]:tabular-nums">
        {/*
          The svg keeps recharts' keyboard layer (role="application", arrow keys step through
          days), so it is named with aria-label rather than a role="img" wrapper, which would
          hide that focusable layer. No `title` prop: an svg <title> pops a native hover
          tooltip on top of the custom one.
        */}
        <AreaChart
          responsive
          aria-label={label}
          desc={summary}
          data={daily}
          style={{ width: '100%', height: '100%' }}
          margin={{ top: 12, right: 8, bottom: 0, left: 0 }}
        >
          <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
          <XAxis
            dataKey="date"
            tickFormatter={formatUtcDayShort}
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={{ stroke: 'var(--chart-axis)' }}
            tickMargin={10}
            minTickGap={28}
            interval="preserveStartEnd"
          />
          <YAxis
            allowDecimals={false}
            domain={[0, yTicks[yTicks.length - 1]]}
            ticks={yTicks}
            tickFormatter={formatCount}
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            width={36}
          />
          <Tooltip
            content={CommitsTooltip}
            cursor={{ stroke: 'var(--chart-cursor)', strokeWidth: 1 }}
            isAnimationActive={false}
          />
          <Area
            type="monotone"
            dataKey="commits"
            name="Commits"
            stroke="var(--chart-1)"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="var(--chart-1)"
            fillOpacity={0.1}
            dot={false}
            activeDot={{ r: 4, fill: 'var(--chart-1)', stroke: 'var(--surface)', strokeWidth: 2 }}
            isAnimationActive={!reduceMotion}
            animationDuration={900}
          />
          {peak && (
            <ReferenceDot
              x={peak.date}
              y={peak.commits}
              r={4}
              fill="var(--chart-1)"
              stroke="var(--surface)"
              strokeWidth={2}
            />
          )}
        </AreaChart>
      </div>

      <details className="group mt-4">
        <summary className="inline-flex cursor-pointer list-none items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium text-white/50 transition-all duration-700 ease-fluid hover:bg-white/[0.04] hover:text-white [&::-webkit-details-marker]:hidden">
          <Table weight="light" className="h-4 w-4" />
          <span className="group-open:hidden">View as table</span>
          <span className="hidden group-open:inline">Hide table</span>
        </summary>
        <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1.5 px-3 text-xs sm:grid-cols-3 lg:grid-cols-5">
          {daily.map((day) => (
            <div key={day.date} className="flex items-baseline justify-between gap-3">
              <dt className="text-[var(--text-muted)]">{formatUtcDayShort(day.date)}</dt>
              <dd className="font-medium tabular-nums text-white/80">{formatCount(day.commits)}</dd>
            </div>
          ))}
        </dl>
      </details>
    </div>
  )
}
