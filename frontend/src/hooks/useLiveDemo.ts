import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import { apiErrorMessage } from '../api/client'

/**
 * Opens the live demo: signs in as the demo manager, then lands on the Team view.
 * `pending` stays true after success because the page is navigating away.
 */
export function useLiveDemo() {
  const { loginDemo } = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const launch = async () => {
    if (pending) return
    setError(null)
    setPending(true)
    try {
      await loginDemo()
      navigate('/app/team')
    } catch (err) {
      setError(apiErrorMessage(err, "Couldn't open the live demo. Please try again."))
      setPending(false)
    }
  }

  return { launch, pending, error, clearError: () => setError(null) }
}
