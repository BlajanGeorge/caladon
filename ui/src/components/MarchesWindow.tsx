import type { Movement, Movements, UnitView } from '../api/worlds'
import { UNIT_ICONS } from '../city/unitIcons'
import { formatDuration, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import attackUrl from '@assets/sprites/hud-move-attack.png'
import supportUrl from '@assets/sprites/hud-move-support.png'
import spyUrl from '@assets/sprites/hud-move-spy.png'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import silverUrl from '@assets/sprites/hud-silver.png'

interface Props {
  movements: Movements | null
  /** Every unit type, for what a body of troops can carry. */
  units: UnitView[] | null
  busy: boolean
  onRecall: (movementId: number) => void
  onClose: () => void
}

const MARK: Record<Movement['kind'], string> = {
  ATTACK: attackUrl,
  SUPPORT: supportUrl,
  ESPIONAGE: spyUrl,
}

/** The errand, beside its medallion; the arrow after it says which way it is flying. */
const ERRAND: Record<Movement['kind'], string> = {
  ATTACK: 'Attack',
  SUPPORT: 'Support',
  ESPIONAGE: 'Spying',
}

/**
 * Everything on the road in full: who is out, what they can carry, what is coming back and with what,
 * which spy went where and with how much silver. Troops are not sent from here — that is the Barracks.
 */
export function MarchesWindow({ movements, units, busy, onRecall, onClose }: Props) {
  const out = movements?.outgoing ?? []
  const now = useNow(out.length > 0)
  const carryOf = new Map((units ?? []).map((u) => [u.type, u.carry]))

  const rows = (list: Movement[]) => list.map((m) => {
    const troops = m.units.reduce((n, u) => n + u.count, 0)
    // What this body could carry home, from the units' own capacity.
    const carry = m.units.reduce((n, u) => n + (carryOf.get(u.type) ?? 0) * u.count, 0)
    const load = m.carrying
    const carrying = load ? load.wood + load.stone + load.silver : 0
    return (
      <li key={m.id} className={m.direction === 'HOMEWARD' ? 'back' : undefined}>
        <img className="mw-mark" src={MARK[m.kind]} alt="" />
        <div className="mw-body">
          {/* The city and the player are what a march is about, so they lead; the errand sits under them
              beside the numbers, and the medallion has already said which it is. */}
          <div className="mw-head">
            <span className="mw-what">
              {ERRAND[m.kind]}
              <i className="mw-way">{m.direction === 'OUTWARD' ? '→' : '←'}</i>
            </span>
            <span className="mw-city">{m.otherCityName}</span>
            {m.otherPlayerName ? <span className="mw-player">{m.otherPlayerName}</span> : null}
            <b className="mw-clock">{formatDuration(secondsUntil(m.arrivesAt, now))}</b>
          </div>

          {m.units.length > 0 && (
            <ul className="mw-units">
              {m.units.map((u) => (
                <li key={u.type}>
                  <img src={UNIT_ICONS[u.type]} alt="" />
                  <span>{u.count.toLocaleString()}</span>
                </li>
              ))}
            </ul>
          )}

          {/* Only numbers under the head: the errand and its direction are said above. */}
          {m.kind === 'ESPIONAGE' ? (
            m.direction === 'OUTWARD' && load ? (
              <div className="mw-line">
                <span className="mw-load"><img src={silverUrl} alt="Silver" />{load.silver.toLocaleString()} silver</span>
              </div>
            ) : null
          ) : m.units.length === 0 ? null : (
            <div className="mw-line">
              <span className="mw-quiet">{troops.toLocaleString()} troops</span>
              {m.direction === 'OUTWARD' && m.kind === 'ATTACK' && (
                <span className="mw-quiet">can carry {carry.toLocaleString()}</span>
              )}
              {m.direction === 'HOMEWARD' && (
                carrying > 0 ? (
                  <span className="mw-load">
                    <img src={woodUrl} alt="Wood" />{load!.wood.toLocaleString()}
                    <img src={stoneUrl} alt="Stone" />{load!.stone.toLocaleString()}
                    <img src={silverUrl} alt="Silver" />{load!.silver.toLocaleString()}
                  </span>
                ) : <span className="mw-quiet">carrying nothing</span>
              )}
            </div>
          )}
        </div>
        {m.canRecall ? (
          <button type="button" className="cave-max" disabled={busy} onClick={() => onRecall(m.id)}>
            Recall
          </button>
        ) : <span className="mw-nogo" aria-hidden="true" />}
      </li>
    )
  })

  return (
    <div className="b-info studies marches" role="dialog" aria-label="Marches">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Marches</h3>
      <p className="b-info-desc">Everything this city has on the road, out and back.</p>

      {out.length === 0 ? <p className="cq-empty">Nobody is marching</p> : <ul className="mw-list">{rows(out)}</ul>}
    </div>
  )
}
