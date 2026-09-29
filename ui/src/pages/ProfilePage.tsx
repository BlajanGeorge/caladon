import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { authApi, type Profile } from '../api/auth'
import { worldsApi, type OwnedCity } from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { lastWorld } from '../lastWorld'

const MIN_PASSWORD = 8

/** Who you are, and the one thing worth changing here. */
interface NavState {
  worldId?: number
  worldName?: string
  city?: OwnedCity
}

export function ProfilePage() {
  const navigate = useNavigate()
  // Where the player came from, so the bar can still take them back into the world.
  const from = (useLocation().state ?? {}) as NavState
  /**
   * Which world the bar points back into. Whatever the player came from, failing that the last one they
   * were in, and failing that whichever they have joined — the bar should carry the whole game however
   * this page was reached.
   */
  const [back, setBack] = useState<{ id: number; name?: string } | null>(
    from.worldId !== undefined ? { id: from.worldId, name: from.worldName } : lastWorld(),
  )
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

  useEffect(() => {
    if (back) return
    worldsApi.mine().then((worlds) => {
      if (worlds.length > 0) setBack({ id: worlds[0].id, name: worlds[0].name })
    }).catch(() => { /* no world to go back to: the bar simply offers less */ })
  }, [back])

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
    <div className="city-shell">
      <MapTopBar
        showCityButton
        showWorldButton
        worldName={back?.name}
        worldId={back?.id}
        city={from.city}
      />
      <main className="rk-body pf-body">
      <div className="pf-stack">
        <div className="rk-panel pf-panel">
          <div className="rk-head">
            <h1>{profile?.nickname ?? 'Profile'}</h1>
            <button type="button" className="cave-max pf-back" onClick={() => navigate(-1)}>Back</button>
          </div>

          <dl className="b-info-now pf-facts">
            <dt>Email</dt><dd>{profile?.email ?? '…'}</dd>
            <dt>Playing since</dt>
            <dd>{profile ? new Date(profile.since).toLocaleDateString(undefined, { dateStyle: 'long' }) : '…'}</dd>
          </dl>
        </div>

        <form className="rk-panel pf-panel pf-form" onSubmit={submit}>
          <h2>Change your password</h2>
          <div className="pf-fields">
            <label>
              Current
              <input
                className="recruit-count"
                type="password"
                autoComplete="current-password"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
              />
            </label>
            <label>
              New
              <input
                className="recruit-count"
                type="password"
                autoComplete="new-password"
                value={next}
                onChange={(e) => setNext(e.target.value)}
              />
            </label>
            <label>
              New again
              <input
                className="recruit-count"
                type="password"
                autoComplete="new-password"
                value={again}
                onChange={(e) => setAgain(e.target.value)}
              />
            </label>
          </div>

          <div className="send-go">
            <span className="send-why">
              {error ? <span className="pf-bad">{error}</span>
                : done ? <span className="pf-good">Changed. Anywhere else you were signed in has been signed out.</span>
                : refusal ?? 'Everywhere else you are signed in will be signed out.'}
            </span>
            <button type="submit" className="b-info-action send-off" disabled={busy || refusal !== null}>
              Change
            </button>
          </div>
        </form>
      </div>
      </main>
    </div>
  )
}
