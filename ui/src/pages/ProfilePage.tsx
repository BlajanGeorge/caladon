import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { ApiError } from '../api/client'
import { authApi, type Profile } from '../api/auth'
import { worldsApi, type OwnedCity } from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { lastWorld } from '../lastWorld'

const MIN_PASSWORD = 8

/** What a screen inside the game sends with it, so the bar can carry the game onwards. */
interface NavState {
  worldId?: number
  worldName?: string
  city?: OwnedCity
}

export interface BarWorld { id: number; name?: string }

/**
 * Which world the top bar points back into, and whether it points into one at all.
 *
 * A player who opened this from the lobby is in no world, and the bar must not offer them a city, a
 * map, reports or standings. What they were sent with says so: arriving from anywhere in the game
 * carries it, naming the world or plainly leaving it out. Only a page opened cold, by link or by
 * bookmark, carries nothing at all, and only then is the world they were last in worth guessing at.
 */
export function worldForBar(sent: NavState | null, remembered: BarWorld | null): BarWorld | null {
  if (!sent) return remembered
  return sent.worldId !== undefined ? { id: sent.worldId, name: sent.worldName } : null
}

export function ProfilePage() {
  // Where the player came from, so the bar can still take them back into the world.
  const sent = useLocation().state as NavState | null
  const from = sent ?? {}
  const [back, setBack] = useState<BarWorld | null>(() => worldForBar(sent, lastWorld()))
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

  // Opened cold with nothing remembered: whichever world the player has joined will do.
  useEffect(() => {
    if (sent || back) return
    worldsApi.mine().then((worlds) => {
      if (worlds.length > 0) setBack({ id: worlds[0].id, name: worlds[0].name })
    }).catch(() => { /* no world to go back to: the bar simply offers less */ })
  }, [sent, back])

  // The grey button says what is still missing; the page says nothing until something happens.
  const ready = current !== '' && next.length >= MIN_PASSWORD && next === again

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!ready) return
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
          <h1>{profile?.nickname ?? 'Profile'}</h1>

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
                : done ? <span className="pf-good">Changed.</span>
                : null}
            </span>
            <button type="submit" className="b-info-action send-off" disabled={busy || !ready}>
              Change
            </button>
          </div>
        </form>
      </div>
      </main>
    </div>
  )
}
