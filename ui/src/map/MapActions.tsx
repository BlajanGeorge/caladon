import { useEffect, useState } from 'react'
import { worldsApi, type CityDetail, type MovementKind, type UnitType, type UnitView } from '../api/worlds'
import { SendWindow } from '../components/SendWindow'
import { formatDuration } from '../city/format'
import silverUrl from '@assets/sprites/hud-silver.png'

/** What the map was asked to do, and where. */
export interface Action {
  kind: MovementKind
  x: number
  y: number
  name: string
}

interface Props {
  worldId: number
  /** The city the troops and the silver come from. */
  cityId: number
  action: Action
  onDone: () => void
  onClose: () => void
  onError: (message: string) => void
}

/**
 * The window behind a map action. The city's own state is fetched when one is opened rather than kept
 * with the map, which does not otherwise care what is standing at home.
 */
export function MapActions({ worldId, cityId, action, onDone, onClose, onError }: Props) {
  const [detail, setDetail] = useState<CityDetail | null>(null)
  const [units, setUnits] = useState<UnitView[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [silver, setSilver] = useState('')

  useEffect(() => {
    let stop = false
    Promise.all([worldsApi.cityDetail(worldId, cityId), worldsApi.army(worldId, cityId)])
      .then(([d, a]) => { if (!stop) { setDetail(d); setUnits(a) } })
      .catch(() => onError('Could not read your city'))
    return () => { stop = true }
  }, [worldId, cityId, onError])

  const run = async (call: () => Promise<unknown>) => {
    setBusy(true)
    try {
      await call()
      onDone()
    } catch {
      onError('That was refused')
    } finally {
      setBusy(false)
    }
  }

  if (action.kind === 'ESPIONAGE') {
    const held = detail?.cave.silver ?? 0
    const spend = Math.max(0, Math.min(Math.floor(Number(silver) || 0), held))
    // The same shape as sending troops: what it costs above, what it will take below, one gold button.
    const away = detail ? Math.hypot(action.x - detail.x, action.y - detail.y) : 0
    const travel = away > 0 && detail ? Math.round((away * detail.spySpeed * 60) / (detail.worldSpeed || 1)) : 0
    const refusal = held === 0 ? 'No silver in the Cave to pay a spy' : null
    return (
      <div className="b-info studies send map-window" role="dialog" aria-label="Spy">
        <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
        <h3>Spy</h3>
        <p className="b-info-level">{detail ? `From ${detail.name}` : 'From this city'}</p>

        <div className="send-head">
          <span className="send-target">Target <b className="send-there">{action.name}</b></span>
        </div>

        <ul className="study-list send-list">
          <li className={held === 0 ? 'blocked' : 'ready'}>
            <img className="study-icon" src={silverUrl} alt="" />
            <span className="study-name send-name">
              Silver
              <b>{held.toLocaleString()}</b>
            </span>
            <span className="study-action">
              <input
                className="recruit-count"
                inputMode="numeric"
                placeholder="0"
                disabled={held === 0}
                value={silver}
                onChange={(e) => setSilver(e.target.value.replace(/\D/g, ''))}
                aria-label="Silver to spend"
              />
              <button
                type="button"
                className="cave-max"
                disabled={held === 0}
                title={`Spend everything the Cave holds: ${held.toLocaleString()}`}
                onClick={() => setSilver(String(held))}
              >
                Max
              </button>
            </span>
          </li>
        </ul>

        <div className="send-go">
          {refusal ? (
            <span className="send-why">{refusal}</span>
          ) : spend === 0 ? (
            <span className="send-why" />
          ) : (
            <span className="send-why send-facts">
              <span><i>Travel time</i><b>{formatDuration(travel)}</b></span>
            </span>
          )}
          <button
            type="button"
            className="b-info-action send-off"
            disabled={busy || spend === 0 || refusal !== null}
            onClick={() => void run(() => worldsApi.spy(worldId, cityId, action.x, action.y, spend))}
          >
            Send
          </button>
        </div>
      </div>
    )
  }

  if (!units) return null
  return (
    <SendWindow
      units={units}
      detail={detail}
      busy={busy}
      target={{ x: action.x, y: action.y, name: action.name }}
      className="map-window"
      only={[action.kind]}
      onSend={(kind, x, y, load: Partial<Record<UnitType, number>>) =>
        void run(() => worldsApi.send(worldId, cityId, kind, x, y, load))}
      onClose={onClose}
    />
  )
}
