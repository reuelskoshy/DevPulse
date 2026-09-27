import { Area, AreaChart, Tooltip, XAxis, YAxis } from 'recharts'
import type { DailyCommits } from '../../api/team'
import { CommitsTooltip } from './CommitsTooltip'

interface SparklineProps {
  daily: DailyCommits[]
  /** Shared y-max so every member's sparkline uses the same scale. */
  max: number
  /**
   * Accessible summary, e.g. "48 commits over 14 days". Leave it out when those
   * numbers are already printed next to the sparkline: it is then aria-hidden.
   */
  label?: string
  className?: string
}

/**
 * Axis-free daily-commit sparkline. A member with no commits draws a flat muted
 * baseline instead of the accent. Pointer hover shows the day's value; assistive
 * tech gets the `label` summary, or nothing when the sparkline is decorative.
 */
export function Sparkline({ daily, max, label, className = '' }: SparklineProps) {
  const hasActivity = daily.some((day) => day.commits > 0)
  const color = hasActivity ? 'var(--chart-1)' : 'var(--chart-muted)'
  const a11y = label ? { role: 'img', 'aria-label': label } : { 'aria-hidden': true }

  return (
    <div {...a11y} className={`h-11 w-full ${className}`}>
      <AreaChart
        responsive
        accessibilityLayer={false}
        data={daily}
        style={{ width: '100%', height: '100%' }}
        margin={{ top: 6, right: 6, bottom: 3, left: 6 }}
      >
        <XAxis dataKey="date" hide />
        <YAxis hide domain={[0, Math.max(1, max)]} />
        <Tooltip
          content={CommitsTooltip}
          cursor={{ stroke: 'var(--chart-cursor)', strokeWidth: 1 }}
          isAnimationActive={false}
          allowEscapeViewBox={{ x: false, y: true }}
          position={{ y: -60 }}
          wrapperStyle={{ zIndex: 20 }}
        />
        <Area
          type="monotone"
          dataKey="commits"
          stroke={color}
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
          fill={color}
          fillOpacity={hasActivity ? 0.1 : 0}
          dot={false}
          activeDot={hasActivity ? { r: 4, fill: 'var(--chart-1)', stroke: 'var(--surface)', strokeWidth: 2 } : false}
          isAnimationActive={false}
        />
      </AreaChart>
    </div>
  )
}
