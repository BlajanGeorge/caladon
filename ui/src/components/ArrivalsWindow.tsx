import type { Movement, Movements, UnitView } from '../api/worlds'
import { UNIT_ICONS } from '../city/unitIcons'
import { formatDuration, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import attackUrl from '@assets/sprites/hud-move-attack.png'
import supportUrl from '@assets/sprites/hud-move-support.png'
import spyUrl from '@assets/sprites/hud-move-spy.png'
import goodsUrl from '@assets/sprites/hud-resources.png'

interface Props {
  movements: Movements | null
  /** Every unit type, for what an army coming in could carry away. */
  units: UnitView[] | null
  onClose: () => void
}

const MARK: Record<Movement['kind'], string> = {
  ATTACK: attackUrl,
  SUPPORT: supportUrl,
  ESPIONAGE: spyUrl,
}

const ERRAND: Record<Movement['kind'], string> = {
  ATTACK: 'Attack',
  SUPPORT: 'Support',
  ESPIONAGE: 'Spying',
}

/**
 * Everything heading here, in full. An attack is only made out in the last quarter of its flight; before
 * that the city can see that something is coming and nothing else, which is the server's rule, not this
 * window's — it is simply sent no units until then.
 */
export function ArrivalsWindow({ movements, units, onClose }: Props) {
  const incoming = movements?.incoming ?? []
  const now = useNow(incoming.length > 0)
  const carryOf = new Map((units ?? []).map((u) => [u.type, u.carry]))
  const nameOf = new Map((units ?? []).map((u) => [u.type, u.name]))

  return (
    <div className="b-info studies marches" role="dialog" aria-label="Arrivals">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Arrivals</h3>
      <p className="b-info-desc">Everything on its way to this city.</p>

      {incoming.length === 0 ? (
        <p className="cq-empty">Nothing is arriving</p>
      ) : (
        <ul className="mw-list">
          {incoming.map((m) => {
            const carry = m.units.reduce((n, u) => n + (carryOf.get(u.type) ?? 0) * u.count, 0)
            return (
              <li key={m.id} className={m.kind === 'ATTACK' ? 'alarm' : undefined}>
                <img className="mw-mark" src={MARK[m.kind]} alt="" />
                <div className="mw-body">
                  <div className="mw-head">
                    <span className="mw-what">
                      {ERRAND[m.kind]}
                      <i className="mw-way">←</i>
                    </span>
                    <span className="mw-city">{m.otherCityName}</span>
                    {m.otherPlayerName ? <span className="mw-player">{m.otherPlayerName}</span> : null}
                    <b className="mw-clock">{formatDuration(secondsUntil(m.arrivesAt, now))}</b>
                  </div>

                  {m.units.length === 0 ? (
                    <div className="mw-line">
                      <span className="mw-quiet">Too far off to make out what is coming</span>
                    </div>
                  ) : (
                    <>
                      <ul className="mw-units">
                        {m.units.map((u) => (
                          <li key={u.type}>
                            <img src={UNIT_ICONS[u.type]} alt="" />
                            <span className="mw-unit-name">{nameOf.get(u.type) ?? u.name}</span>
                            <span className="mw-unit-count">{u.count.toLocaleString()}</span>
                          </li>
                        ))}
                      </ul>
                      {m.kind === 'ATTACK' && (
                        <ul className="mw-units">
                          <li title="What they could carry away">
                            <img src={goodsUrl} alt="" />
                            <span className="mw-unit-name">Resources</span>
                            <span className="mw-unit-count">{carry.toLocaleString()}</span>
                          </li>
                        </ul>
                      )}
                    </>
                  )}
                </div>
                <span className="mw-nogo" aria-hidden="true" />
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
