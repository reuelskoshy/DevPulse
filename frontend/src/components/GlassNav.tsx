import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { IslandButton } from './IslandButton'

interface NavLink {
  label: string
  href: string
}

interface GlassNavProps {
  links: NavLink[]
  secondaryLink: { label: string; to: string }
  cta: { label: string; to: string }
}

const EASE_FLUID = [0.32, 0.72, 0, 1] as const

function BrandMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="shrink-0">
      <path
        d="M2 12H7L9.5 5L14.5 19L17 12H22"
        stroke="var(--accent)"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * The "Fluid Island" nav (Section 5A): a floating glass pill detached from
 * the top edge — never an edge-to-edge sticky bar. The hamburger morphs into
 * an X, and the mobile menu opens as a full-screen glass overlay with a
 * staggered mask reveal on each link.
 */
export function GlassNav({ links, secondaryLink, cta }: GlassNavProps) {
  const [open, setOpen] = useState(false)

  return (
    <header className="sticky top-0 z-40 px-4 pt-6">
      <div className="mx-auto flex w-max max-w-[calc(100%-1rem)] items-center gap-1 rounded-full bg-white/[0.04] px-2 py-2 ring-1 ring-white/10 backdrop-blur-2xl">
        <Link to="/" className="flex items-center gap-2 pl-3 pr-2 text-sm font-semibold text-white">
          <BrandMark />
          DevPulse
        </Link>

        <nav className="hidden items-center md:flex">
          {links.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="rounded-full px-3.5 py-2 text-xs font-medium uppercase tracking-[0.08em] text-white/70 transition-colors duration-500 ease-fluid hover:text-white"
            >
              {link.label}
            </a>
          ))}
          <Link
            to={secondaryLink.to}
            className="rounded-full px-3.5 py-2 text-xs font-medium uppercase tracking-[0.08em] text-white/70 transition-colors duration-500 ease-fluid hover:text-white"
          >
            {secondaryLink.label}
          </Link>
        </nav>

        <span className="ml-1 hidden md:block">
          <IslandButton to={cta.to} variant="primary" className="py-1.5 pl-4 text-xs">
            {cta.label}
          </IslandButton>
        </span>

        <button
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-label="Toggle menu"
          className="relative ml-1 h-9 w-9 shrink-0 rounded-full bg-white/5 ring-1 ring-white/10 md:hidden"
        >
          <span
            className="absolute left-1/2 top-1/2 h-px w-4 -translate-x-1/2 bg-white transition-transform duration-500 ease-fluid"
            style={{ transform: open ? 'translate(-50%, 0) rotate(45deg)' : 'translate(-50%, -3px)' }}
          />
          <span
            className="absolute left-1/2 top-1/2 h-px w-4 -translate-x-1/2 bg-white transition-transform duration-500 ease-fluid"
            style={{ transform: open ? 'translate(-50%, 0) rotate(-45deg)' : 'translate(-50%, 3px)' }}
          />
        </button>
      </div>

      <AnimatePresence>
        {open && (
          <motion.div
            key="mobile-nav"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5, ease: EASE_FLUID }}
            className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-7 bg-black/85 backdrop-blur-3xl md:hidden"
          >
            {links.map((link, i) => (
              <motion.a
                key={link.href}
                href={link.href}
                onClick={() => setOpen(false)}
                initial={{ opacity: 0, y: 48 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.1 + i * 0.05, ease: EASE_FLUID }}
                className="text-2xl font-medium text-white"
              >
                {link.label}
              </motion.a>
            ))}
            <motion.div
              initial={{ opacity: 0, y: 48 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.1 + links.length * 0.05, ease: EASE_FLUID }}
            >
              <Link
                to={secondaryLink.to}
                onClick={() => setOpen(false)}
                className="text-2xl font-medium text-white/70"
              >
                {secondaryLink.label}
              </Link>
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 48 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.15 + links.length * 0.05, ease: EASE_FLUID }}
            >
              <IslandButton to={cta.to} variant="primary" onClick={() => setOpen(false)}>
                {cta.label}
              </IslandButton>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  )
}
