import type { ReactNode } from 'react'
import { motion, useReducedMotion } from 'framer-motion'

interface RevealProps {
  children: ReactNode
  delay?: number
  className?: string
}

const EASE_FLUID = [0.32, 0.72, 0, 1] as const

/**
 * Heavy fade-up-blur scroll entry (Section 5C): elements never appear
 * statically — they resolve in as they cross the viewport, once, via
 * IntersectionObserver (framer-motion's whileInView), not a scroll listener.
 */
export function Reveal({ children, delay = 0, className }: RevealProps) {
  const reduceMotion = useReducedMotion()

  if (reduceMotion) {
    return <div className={className}>{children}</div>
  }

  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 64, filter: 'blur(12px)' }}
      whileInView={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
      viewport={{ once: true, margin: '-80px' }}
      transition={{ duration: 0.9, delay, ease: EASE_FLUID }}
    >
      {children}
    </motion.div>
  )
}
