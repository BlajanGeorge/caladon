import { useEffect, useState } from 'react'
import { worldsApi, type CityDetail, type MovementKind, type UnitType, type UnitView } from '../api/worlds'
import { SendWindow } from '../components/SendWindow'
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
    const refusal = held === 0 ? 'No silver in the Cave to pay a spy' : spend === 0 ? 'Enter how much silver to spend' : null
    return (
      <div className="b-info cave map-window" role="dialog" aria-label="Send a spy">
        <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
        <h3>Send a spy</h3>
        <p className="b-info-level">To {action.name}</p>
        <p className="b-info-desc">
          The silver is spent whatever happens. Outbid what their Cave holds and your spy comes home with
          what it saw; fall short and they learn who tried.
        </p>
        <dl className="b-info-now">
          <dt>In your Cave</dt>
          <dd>{held.toLocaleString()} silver</dd>
        </dl>
        <div className="cave-go">
          {refusal && <span className="send-why">{refusal}</span>}
          <div className="cave-row">
            <input
              className="recruit-count"
              inputMode="numeric"
              placeholder={String(held)}
              value={silver}
              disabled={held === 0}
              onChange={(e) => setSilver(e.target.value.replace(/\D/g, ''))}
              aria-label="Silver to spend"
            />
            <button type="button" className="cave-max" disabled={held === 0} onClick={() => setSilver(String(held))}>
              Max
            </button>
            <button
              type="button"
              className="b-info-action cave-store"
              disabled={busy || refusal !== null}
              onClick={() => void run(() => worldsApi.spy(worldId, cityId, action.x, action.y, spend))}
            >
              <img src={silverUrl} alt="" />
              Spy
            </button>
          </div>
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
      only={action.kind === 'ATTACK' && !action.name.startsWith('Barbarian') ? undefined : [action.kind]}
      onSend={(kind, x, y, load: Partial<Record<UnitType, number>>) =>
        void run(() => worldsApi.send(worldId, cityId, kind, x, y, load))}
      onClose={onClose}
    />
  )
}
