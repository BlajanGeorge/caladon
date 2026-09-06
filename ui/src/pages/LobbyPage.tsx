import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type PlayableWorld } from '../api/worlds'
import { TopBar } from '../components/TopBar'
import { useToast } from '../components/Toast'

export function LobbyPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [worlds, setWorlds] = useState<PlayableWorld[] | null>(null)
  const [busy, setBusy] = useState<number | null>(null)

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
    <>
      <TopBar />
      <main className="page">
        <div className="card">
          <h1>Worlds</h1>
          {worlds === null ? (
            <p className="muted">Loading…</p>
          ) : worlds.length === 0 ? (
            <div className="empty">No worlds are open for play right now. Check back later.</div>
          ) : (
            <ul className="world-list">
              {worlds.map((w) => (
                <li key={w.id} className="world-row">
                  <span className="name">{w.name}</span>
                  {w.joined ? (
                    <button className="primary" onClick={() => play(w)}>Play</button>
                  ) : (
                    <button className="secondary" onClick={() => join(w)} disabled={busy === w.id}>Join</button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </main>
    </>
  )
}
