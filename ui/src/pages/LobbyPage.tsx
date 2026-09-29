import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type PlayableWorld } from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { useToast } from '../components/Toast'
import worldUrl from '@assets/sprites/hud-world.png'

/** Where the player picks a world: the game's own dark panel over the battlefield, as the profile is. */
export function LobbyPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [worlds, setWorlds] = useState<PlayableWorld[] | null>(null)
  const [busy, setBusy] = useState<number | null>(null)
  // Joining founds a city and cannot be undone, so it is asked for rather than taken on one click.
  const [joining, setJoining] = useState<PlayableWorld | null>(null)

  const load = useCallback(async () => {
    try {
      setWorlds(await worldsApi.list())
    } catch (err) {
      if (!(err instanceof ApiError && err.code === 'SESSION_EXPIRED')) toast.error('Could not load worlds')
    }
  }, [toast])

  useEffect(() => { void load() }, [load])

  async function join(world: PlayableWorld) {
    setBusy(world.id)
    try {
      const res = await worldsApi.join(world.id)
      navigate(`/worlds/${world.id}/city`, { state: { worldName: world.name, city: res.startCity } })
    } catch (err) {
      if (err instanceof ApiError && (err.code === 'WORLD_NOT_PLAYABLE' || err.code === 'ALREADY_JOINED')) {
        toast.error(err.code === 'ALREADY_JOINED' ? 'You already joined this world' : 'This world is not open for play')
        void load()
      } else if (!(err instanceof ApiError && err.code === 'SESSION_EXPIRED')) {
        toast.error('Could not join the world')
      }
    } finally {
      setBusy(null)
    }
  }

  function play(world: PlayableWorld) {
    navigate(`/worlds/${world.id}/city`, { state: { worldName: world.name } })
  }

  return (
    <div className="city-shell">
      <MapTopBar />
      <main className="rk-body lb-body">
        <div className="rk-panel lb-panel">
          <h1>Worlds</h1>

          {worlds === null ? (
            <p className="rk-note">Reading the map…</p>
          ) : worlds.length === 0 ? (
            <p className="rk-note">No world is open for play. Come back later.</p>
          ) : (
            <ul className="lb-list">
              {worlds.map((w) => (
                <li key={w.id} className="lb-row">
                  <img className="lb-mark" src={worldUrl} alt="" />
                  <span className="lb-name">{w.name}</span>
                  {w.joined ? (
                    <button type="button" className="b-info-action lb-go" onClick={() => play(w)}>Enter</button>
                  ) : (
                    <button
                      type="button"
                      className="cave-max lb-join"
                      disabled={busy === w.id}
                      onClick={() => setJoining(w)}
                    >
                      Settle
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}

          {joining && (
            <div className="confirm floating">
              <div className="confirm-box" role="alertdialog" aria-label={`Settle in ${joining.name}`}>
                <h4>Settle in {joining.name}?</h4>
                <p>
                  A city is founded for you there and stays yours. You cannot leave a world once you
                  have settled in it.
                </p>
                <div className="confirm-buttons">
                  <button type="button" className="confirm-no" onClick={() => setJoining(null)}>Not yet</button>
                  <button
                    type="button"
                    className="confirm-yes"
                    disabled={busy !== null}
                    onClick={() => { const w = joining; setJoining(null); void join(w) }}
                  >
                    Settle
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
