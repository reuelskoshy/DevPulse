interface BrandMarkProps {
  size?: number
  className?: string
}

/** The DevPulse pulse-line glyph, stroked in the emerald accent. */
export function BrandMark({ size = 18, className = '' }: BrandMarkProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={`shrink-0 ${className}`}
    >
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
