import { useLayoutEffect, useRef, useState } from 'react'
import type { BuildingView, CityDetail, OwnedCity, UnitView } from '../api/worlds'
import { ResourceStrip } from './ResourceStrip'
import { GROUND_INSETS, GROUND_PAINTED, GROUND_SIZE, PLOTS, anchorPercent } from '../city/plots'
import { UNIT_ICONS, UNIT_ORDER } from '../city/unitIcons'
import groundUrl from '@assets/sprites/city-ground.png'

/** Width of the side panel that holds resources and troops. */
const PANEL_W = 270
/**
 * The picture is wider than the area it sits in (about 2:1 against roughly 1.7:1), so it is scaled to
 * *cover* that area and centred: the grass margin the generator left is what gets cropped, and there is
 * never a black band, whatever the window size.
 */

interface Props {
  buildings: BuildingView[] | null
  /** The city, for the name and points; `detail` also feeds the resource strip. */
  city: OwnedCity | null
  detail: CityDetail | null
  units: UnitView[] | null
}

/**
 * The city view: the picture fills the area left of the side panel, which holds the city's name, its resources and its troops. Plot labels (later:
 * building sprites) live inside the picture box, so their percentage anchors stay on the plots.
 */
export function CityScene({ buildings, city, detail, units }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const [box, setBox] = useState({ width: 0, height: 0, left: 0, top: 0 })

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const update = () => {
      // Cover the free area with the painted part, then shift the file's transparent border out of view.
      const cw = Math.max(0, el.clientWidth - PANEL_W)
      const ch = el.clientHeight
      const scale = Math.max(cw / GROUND_PAINTED.width, ch / GROUND_PAINTED.height)
      setBox({
        width: Math.round(GROUND_SIZE.width * scale),
        height: Math.round(GROUND_SIZE.height * scale),
        left: Math.round((cw - GROUND_PAINTED.width * scale) / 2 - GROUND_INSETS.left * scale),
        top: Math.round((ch - GROUND_PAINTED.height * scale) / 2 - GROUND_INSETS.top * scale),
      })
    }
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const levelOf = (type: string) => buildings?.find((b) => b.type === type)?.level
  const shown = detail ?? city
  const countOf = (type: string) => units?.find((u) => u.type === type)?.count ?? 0
  const nameOf = (type: string) => units?.find((u) => u.type === type)?.name

  return (
    <div className="city-scene" ref={ref}>
      <div className="city-scene-box" style={{ width: box.width, height: box.height, left: box.left, top: box.top }}>
        <img src={groundUrl} alt="" draggable={false} />
        {(Object.keys(PLOTS) as (keyof typeof PLOTS)[]).map((type) => {
          const level = levelOf(type)
          const name = type.toLowerCase().replace('_', ' ')
          return (
            <span key={type} className={'plot-label' + (level === 0 ? ' empty' : '')} style={anchorPercent(PLOTS[type])}>
              {name}{level !== undefined ? ` ${level}` : ''}
            </span>
          )
        })}
      </div>

      <aside className="city-panel" style={{ width: PANEL_W }} aria-label="City information">
        <div className="cp-head">
          <span className="cp-name">{shown?.name ?? 'Your city'}</span>
          <span className="cp-meta">{shown ? `${shown.points.toLocaleString()} points` : '…'}</span>
        </div>

        <section className="cp-section">
          <h2>Resources</h2>
          <ResourceStrip resources={detail?.resources} population={detail?.population} withNames />
        </section>

        <section className="cp-section cp-troops">
          <h2>Troops</h2>
          {/* Every unit type is always listed, with 0 when the city has none of it. */}
          <ul className="cp-unit-list">
            {UNIT_ORDER.map((type) => {
              const count = countOf(type)
              const label = nameOf(type) ?? type.toLowerCase().replace('_', ' ')
              return (
                <li key={type}>
                  <img className="cp-unit-icon" src={UNIT_ICONS[type]} alt="" title={label} />
                  <span className="cp-unit-name">{label}</span>
                  <span className="cp-unit-count">{units === null ? '…' : count.toLocaleString()}</span>
                </li>
              )
            })}
          </ul>
        </section>
      </aside>
    </div>
  )
}
