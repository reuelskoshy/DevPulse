import type { ReactNode, MouseEventHandler } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight } from '@phosphor-icons/react'

interface IslandButtonProps {
  children: ReactNode
  variant?: 'primary' | 'ghost'
  icon?: ReactNode | null
  className?: string
  to?: string
  href?: string
  type?: 'button' | 'submit'
  disabled?: boolean
  onClick?: MouseEventHandler
}

const VARIANT_CLASSES = {
  primary: 'bg-white text-black hover:bg-white/90',
  ghost: 'bg-white/5 text-white ring-1 ring-white/10 hover:bg-white/10',
}

const ICON_WRAP_CLASSES = {
  primary: 'bg-black/5',
  ghost: 'bg-white/10',
}

/**
 * Pill CTA with the "button-in-button" trailing icon — the icon never sits
 * naked next to the label, it lives in its own nested circular wrapper that
 * drifts diagonally on hover (magnetic hover physics, Section 5B of the skill).
 */
export function IslandButton({
  children,
  variant = 'primary',
  icon,
  className = '',
  to,
  href,
  type = 'button',
  disabled,
  onClick,
}: IslandButtonProps) {
  const shared = `group relative inline-flex items-center gap-3 rounded-full py-2 pl-6 pr-2 text-sm font-semibold transition-all duration-700 ease-fluid active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 ${VARIANT_CLASSES[variant]} ${className}`

  const iconNode =
    icon === null ? null : (
      <span
        className={`flex h-8 w-8 items-center justify-center rounded-full transition-transform duration-700 ease-fluid group-hover:-translate-y-[1px] group-hover:translate-x-1 group-hover:scale-105 ${ICON_WRAP_CLASSES[variant]}`}
      >
        {icon ?? <ArrowUpRight weight="light" className="h-4 w-4" />}
      </span>
    )

  const content = (
    <>
      <span>{children}</span>
      {iconNode}
    </>
  )

  if (to) {
    return (
      <Link to={to} className={shared} onClick={onClick}>
        {content}
      </Link>
    )
  }

  if (href) {
    return (
      <a href={href} className={shared} onClick={onClick}>
        {content}
      </a>
    )
  }

  return (
    <button type={type} className={shared} disabled={disabled} onClick={onClick}>
      {content}
    </button>
  )
}
