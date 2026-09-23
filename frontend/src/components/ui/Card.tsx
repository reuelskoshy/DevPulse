import type { ReactNode, HTMLAttributes } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  variant?: 'default' | 'glass' | 'gradient-border'
  padding?: 'none' | 'sm' | 'md' | 'lg'
  hover?: boolean
}

export default function Card({
  children,
  variant = 'default',
  padding = 'md',
  hover = false,
  className = '',
  ...props
}: CardProps) {
  const baseStyles = 'rounded-xl transition-all duration-300'

  const variants = {
    default: 'bg-slate-900 border border-slate-800',
    glass: 'bg-slate-900/50 backdrop-blur-xl border border-slate-800/50 shadow-xl',
    'gradient-border': 'relative group',
  }

  const paddings = {
    none: '',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
  }

  const hoverStyles = hover
    ? 'hover:border-cyan-500/50 hover:shadow-xl hover:shadow-cyan-500/10 cursor-pointer'
    : ''

  if (variant === 'gradient-border') {
    return (
      <div className={`${baseStyles} ${className}`} {...props}>
        <div className="absolute inset-0 bg-gradient-to-r from-cyan-400 to-blue-500 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity blur" />
        <div className={`relative bg-slate-900 border border-slate-800 rounded-xl ${paddings[padding]} ${hoverStyles}`}>
          {children}
        </div>
      </div>
    )
  }

  return (
    <div
      className={`${baseStyles} ${variants[variant]} ${paddings[padding]} ${hoverStyles} ${className}`}
      {...props}
    >
      {children}
    </div>
  )
}
