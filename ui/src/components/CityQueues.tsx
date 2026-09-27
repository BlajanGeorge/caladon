import type { CSSProperties } from 'react'
import type { CityDetail } from '../api/worlds'
import { UNIT_ICONS } from '../city/unitIcons'
import { formatDuration, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'

interface Props {
  detail: CityDetail | null
  /** Where the bar sits: the scene keeps it clear of the side panel. */
  style?: CSSProperties
  /** Opens the window that owns a section's queue. */
  onOpenBuildings: () => void
  onOpenRecruit: () => void
  onOpenStudies: () => void
}

/**
 * The bar along the bottom of the city: what the city is working on, in three sections that mirror the
 * three queues. The first entry of each is running and counts down; the rest wait their turn. A section's
 * header opens the window that owns it, where orders are placed and cancelled.
 *
 * Movements will join it later — attacks coming in and going out, support arriving and leaving, scouts on
 * their way, and troops on their way home — each as another section of the same bar.
 */
export function CityQueues({ detail, style, onOpenBuildings, onOpenRecruit, onOpenStudies }: Props) {
  const builds = detail?.buildQueue ?? []
  const troops = detail?.recruitQueue ?? []
  const studies = detail?.studyQueue ?? []
  const busy = builds.length + troops.length + studies.length > 0
  const now = useNow(busy)

  return (
    <div className="city-queues" style={style} aria-label="What the city is working on">
      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenBuildings}>
          Construction{builds.length > 0 ? <em>{builds.length}</em> : null}
        </button>
        {builds.length === 0 ? (
          <p className="cq-empty">Nothing being built</p>
        ) : (
          <ul className="cq-list">
            {builds.map((o, i) => (
              <li key={o.id} className={i === 0 ? 'running' : undefined}>
                <span className="cq-name" title={`${o.name} level ${o.targetLevel}`}>{o.name} <em>level {o.targetLevel}</em></span>
                <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenRecruit}>
          Training{troops.length > 0 ? <em>{troops.length}</em> : null}
        </button>
        {troops.length === 0 ? (
          <p className="cq-empty">No troops in training</p>
        ) : (
          <ul className="cq-list">
            {troops.map((o, i) => (
              <li key={o.id} className={i === 0 ? 'running' : undefined}>
                <img src={UNIT_ICONS[o.unit]} alt="" />
                <span className="cq-name" title={`${o.remaining.toLocaleString()} × ${o.name}`}>{o.remaining.toLocaleString()} × {o.name}</span>
                <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenStudies}>
          Studies{studies.length > 0 ? <em>{studies.length}</em> : null}
        </button>
        {studies.length === 0 ? (
          <p className="cq-empty">Nothing being studied</p>
        ) : (
          <ul className="cq-list">
            {studies.map((o, i) => (
              <li key={o.unit} className={i === 0 ? 'running' : undefined}>
                <img src={UNIT_ICONS[o.unit]} alt="" />
                <span className="cq-name" title={o.name}>{o.name}</span>
                <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
