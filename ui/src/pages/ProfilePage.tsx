import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { authApi, type Profile } from '../api/auth'

const MIN_PASSWORD = 8

/** Who you are, and the one thing worth changing here. */
export function ProfilePage() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  useEffect(() => {
    authApi.me().then(setProfile).catch(() => setError('Could not read your account'))
  }, [])

  const refusal =
    !current ? 'Your current password'
      : next.length < MIN_PASSWORD ? `At least ${MIN_PASSWORD} characters`
      : next !== again ? 'The two do not match'
      : null

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (refusal) return
    setBusy(true)
    setError(null)
    try {
      await authApi.changePassword(current, next)
      setCurrent(''); setNext(''); setAgain(''); setDone(true)
    } catch (err) {
      setDone(false)
      setError(err instanceof ApiError && err.code === 'INVALID_CREDENTIALS'
        ? 'That is not your current password'
        : 'Could not change your password')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page profile">
      <h1>Profile</h1>

      <dl className="pf-facts">
        <dt>Name</dt><dd>{profile?.nickname ?? '…'}</dd>
        <dt>Email</dt><dd>{profile?.email ?? '…'}</dd>
        <dt>Playing since</dt>
        <dd>{profile ? new Date(profile.since).toLocaleDateString(undefined, { dateStyle: 'long' }) : '…'}</dd>
      </dl>

      <form className="pf-form" onSubmit={submit}>
        <h2>Change your password</h2>
        <label>
          Current password
          <input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        </label>
        <label>
          New password
          <input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </label>
        <label>
          New password again
          <input type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} />
        </label>

        <p className="pf-note">
          {error ? <span className="pf-bad">{error}</span>
            : done ? <span className="pf-good">Changed. Anywhere else you were signed in has been signed out.</span>
            : refusal ? <span>{refusal}</span>
            : <span>Everywhere else you are signed in will be signed out.</span>}
        </p>

        <div className="pf-buttons">
          <button type="button" className="secondary" onClick={() => navigate(-1)}>Back</button>
          <button type="submit" className="primary" disabled={busy || refusal !== null}>Change password</button>
        </div>
      </form>
    </div>
  )
}
