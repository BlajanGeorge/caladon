import { useState } from 'react'
import type { Movement, Movements, UnitView } from '../api/worlds'
import { UNIT_ICONS } from '../city/unitIcons'
import { formatDuration, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import attackUrl from '@assets/sprites/hud-move-attack.png'
import supportUrl from '@assets/sprites/hud-move-support.png'
import spyUrl from '@assets/sprites/hud-move-spy.png'
import silverUrl from '@assets/sprites/hud-silver.png'
import goodsUrl from '@assets/sprites/hud-resources.png'

interface Props {
  movements: Movements | null
  /** Every unit type, for what a body of troops can carry. */
  units: UnitView[] | null
  busy: boolean
  onRecall: (movementId: number) => void
  onClose: () => void
}

/** Unit types listed before a march has to be opened to see the rest. */
const SHOWN = 3

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
  // A march of every unit type would be ten lines, so only the first few show until it is opened.
  const [opened, setOpened] = useState<number[]>([])
  const out = movements?.outgoing ?? []
  const now = useNow(out.length > 0)
  const carryOf = new Map((units ?? []).map((u) => [u.type, u.carry]))
  const nameOf = new Map((units ?? []).map((u) => [u.type, u.name]))

  const rows = (list: Movement[]) => list.map((m) => {
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

          {m.units.length > 0 && (() => {
            const open = opened.includes(m.id)
            const shown = open ? m.units : m.units.slice(0, SHOWN)
            const hidden = m.units.length - shown.length
            return (
              <>
                <ul className="mw-units">
                  {shown.map((u) => (
                    <li key={u.type}>
                      <img src={UNIT_ICONS[u.type]} alt="" />
                      <span className="mw-unit-name">{nameOf.get(u.type) ?? u.name}</span>
                      <span className="mw-unit-count">{u.count.toLocaleString()}</span>
                    </li>
                  ))}
                </ul>
                {(hidden > 0 || open) && (
                  <button
                    type="button"
                    className="mw-more"
                    onClick={() => setOpened((ids) => (open ? ids.filter((id) => id !== m.id) : [...ids, m.id]))}
                  >
                    {open ? 'Show fewer' : `${hidden} more`}
                  </button>
                )}
              </>
            )
          })()}

          {/* What it carries is a line of the same list, so its number stands under the troop counts. */}
          {m.kind === 'ESPIONAGE' && m.direction === 'OUTWARD' && load && (
            <ul className="mw-units">
              <li>
                <img src={silverUrl} alt="" />
                <span className="mw-unit-name">Silver</span>
                <span className="mw-unit-count">{load.silver.toLocaleString()}</span>
              </li>
            </ul>
          )}
          {m.kind !== 'ESPIONAGE' && m.units.length > 0 && m.direction === 'OUTWARD' && m.kind === 'ATTACK' && (
            <ul className="mw-units">
              <li title="What they can carry home">
                <img src={goodsUrl} alt="" />
                <span className="mw-unit-name">Resources</span>
                <span className="mw-unit-count">{carry.toLocaleString()}</span>
              </li>
            </ul>
          )}
          {m.kind !== 'ESPIONAGE' && m.direction === 'HOMEWARD' && carrying > 0 && (
            <ul className="mw-units">
              <li title={`${load!.wood.toLocaleString()} wood, ${load!.stone.toLocaleString()} stone, ${load!.silver.toLocaleString()} silver`}>
                <img src={goodsUrl} alt="" />
                <span className="mw-unit-name">Resources</span>
                <span className="mw-unit-count">{carrying.toLocaleString()}</span>
              </li>
            </ul>
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
