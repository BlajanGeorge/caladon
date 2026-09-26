import { useLayoutEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import type { BuildingType, BuildingView, CityDetail, OwnedCity, UnitType, UnitView } from '../api/worlds'
import { ResourceStrip } from './ResourceStrip'
import { GROUND_INSETS, GROUND_PAINTED, GROUND_SIZE, PLOTS, anchorPercent } from '../city/plots'
import { UNIT_ICONS, UNIT_ORDER } from '../city/unitIcons'
import { buildingArt } from '../city/buildingSprites'
import { BuildingInfo } from './BuildingInfo'
import { StudiesWindow } from './StudiesWindow'
import { RecruitWindow } from './RecruitWindow'
import { BuildingsWindow } from './BuildingsWindow'
import groundUrl from '@assets/sprites/city-ground.png'

/**
 * Every building is drawn at the same width, in the ground image's own pixels, so a farm is not half
 * the size of the town hall just because its plot is smaller. (The plots differ: 343 px for the town
 * hall, 163 px for the woodcutter.)
 */
const SPRITE_WIDTH = 195
/**
 * Where the sprite's base sits inside the plot, as a fraction of the plot box's height: 1 would put it
 * on the box's bottom edge, which reads as standing below the circle. A bit above centre looks planted
 * in it.
 */
const SPRITE_BASE = 0.74
/** Nudge across the plot, as a fraction of its width: positive moves the building right. */
const SPRITE_SHIFT = 0.06
/** Width of the side panel that holds resources and troops, and its inset from the screen edge. */
const PANEL_W = 352
const PANEL_INSET = 14

interface Props {
  buildings: BuildingView[] | null
  /** The city, for the name and points; `detail` also feeds the resource strip. */
  city: OwnedCity | null
  detail: CityDetail | null
  units: UnitView[] | null
  busy: boolean
  onStudy: (unit: UnitType) => void
  onRecruit: (unit: UnitType, count: number) => void
  onCancelStudy: (unit: UnitType) => void
  onCancelRecruit: (orderId: number) => void
  onUpgrade: (building: BuildingType) => void
  onCancelBuild: (orderId: number) => void
}

/**
 * The city view: the picture fills the whole view with the side panel floating over it, which holds the city's name, its resources and its troops. Plot labels (later:
 * building sprites) live inside the picture box, so their percentage anchors stay on the plots.
 */
export function CityScene({ buildings, city, detail, units, busy, onStudy, onRecruit, onCancelStudy, onCancelRecruit, onUpgrade, onCancelBuild }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const [box, setBox] = useState({ width: 0, height: 0, left: 0, top: 0 })
  const [hover, setHover] = useState<string | null>(null)
  // One window at a time: a building's own, or the Academy's studies.
  const [window_, setWindow] = useState<
    { kind: 'building'; type: string } | { kind: 'studies' } | { kind: 'recruit' } | { kind: 'buildings' } | null
  >(null)
  const open = window_?.kind === 'building' ? window_.type : null
  const studies = window_?.kind === 'studies'
  const recruiting = window_?.kind === 'recruit'
  const building = window_?.kind === 'buildings'

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const update = () => {
      // The picture covers the whole view, panel included, so no background shows anywhere; it is
      // centred on the part left free by the panel, so the town itself stays in the open.
      const cw = el.clientWidth
      const ch = el.clientHeight
      const free = Math.max(0, cw - PANEL_W - PANEL_INSET * 2)
      const scale = Math.max(cw / GROUND_PAINTED.width, ch / GROUND_PAINTED.height)
      const paintedW = GROUND_PAINTED.width * scale
      // Centre on the free part, but never far enough to uncover an edge of the view.
      const paintedLeft = Math.min(0, Math.max(cw - paintedW, (free - paintedW) / 2))
      setBox({
        width: Math.round(GROUND_SIZE.width * scale),
        height: Math.round(GROUND_SIZE.height * scale),
        left: Math.round(paintedLeft - GROUND_INSETS.left * scale),
        top: Math.round((ch - GROUND_PAINTED.height * scale) / 2 - GROUND_INSETS.top * scale),
      })
    }
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  // One tooltip for the three troop counts, explaining all of them at once: native `title` is slow and
  // easy to miss, and the panel scrolls, so the tip is drawn ourselves at a fixed position beside them.
  const [tip, setTip] = useState<{ x: number; y: number } | null>(null)
  const tipRef = useRef<HTMLDivElement>(null)
  const showTip = (e: ReactMouseEvent<HTMLElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    setTip({ x: r.left, y: r.top })
  }
  const hideTip = () => setTip(null)
  // A row near the bottom of the screen would push the tip off it; keep it inside once measured.
  useLayoutEffect(() => {
    const el = tipRef.current
    if (!tip || !el) return
    const h = el.offsetHeight
    el.style.top = `${Math.max(8, Math.min(tip.y, window.innerHeight - h - 8))}px`
  }, [tip])

  const viewOf = (type: string) => buildings?.find((b) => b.type === type)
  const shown = detail ?? city
  // The panel shows three counts per unit: at home, sheltering here, away supporting someone else.
  const cityUnit = (type: string) => detail?.units.find((u) => u.type === type)
  const nameOf = (type: string) => units?.find((u) => u.type === type)?.name

  return (
    <div className="city-scene" ref={ref}>
      <div className="city-scene-box" style={{ width: box.width, height: box.height, left: box.left, top: box.top }}>
        <img src={groundUrl} alt="" draggable={false} />
        {/* Only the wall's near section with the gate, across the front of the city, over everything. */}
        {(Object.keys(PLOTS) as (keyof typeof PLOTS)[]).map((type) => {
          const view = viewOf(type)
          const level = view?.level
          const plot = PLOTS[type]
          const art = view ? buildingArt(type, view.level, view.maxLevel) : undefined
          const name = type.toLowerCase().replace('_', ' ')
          // A building with art is drawn on its plot; the rest keep the text label for now.
          return art ? (
            <img
              key={type}
              className="plot-sprite"
              src={art.src}
              alt=""
              onMouseEnter={() => setHover(type)}
              onMouseLeave={() => setHover((h) => (h === type ? null : h))}
              onClick={() => setWindow({ kind: 'building', type })}
              style={{
                // Centre of the plot, nudged across it, then corrected for where the art's footprint sits.
                left: `${(100 * ((plot.box[0] + plot.box[2]) / 2 + (plot.box[2] - plot.box[0]) * (SPRITE_SHIFT + (art.slide ?? 0)) + (0.5 - art.footprint) * SPRITE_WIDTH * (art.scale ?? 1))) / GROUND_SIZE.width}%`,
                top: `${(100 * (plot.box[1] + (plot.box[3] - plot.box[1]) * (SPRITE_BASE + (art.drop ?? 0)))) / GROUND_SIZE.height}%`,
                width: `${(100 * SPRITE_WIDTH * (art.scale ?? 1)) / GROUND_SIZE.width}%`,
              }}
            />
          ) : (
            <span
              key={type}
              className={'plot-label' + (level === 0 ? ' empty' : '')}
              style={anchorPercent(plot)}
              onMouseEnter={() => setHover(type)}
              onMouseLeave={() => setHover((h) => (h === type ? null : h))}
              onClick={() => setWindow({ kind: 'building', type })}
            >
              {name}{level !== undefined ? ` ${level}` : ''}
            </span>
          )
        })}

        {/* Hover label: the building's name and level, above its plot. */}
        {hover && (() => {
          const view = viewOf(hover)
          const plot = PLOTS[hover as keyof typeof PLOTS]
          const label = hover.toLowerCase().replace('_', ' ')
          return (
            <span className="plot-tip" style={{
              left: `${(100 * (plot.box[0] + plot.box[2]) / 2) / GROUND_SIZE.width}%`,
              top: `${(100 * plot.box[1]) / GROUND_SIZE.height}%`,
            }}>
              <b>{label}</b>{view ? <em>level {view.level}</em> : null}
            </span>
          )
        })()}
      </div>

      {open && viewOf(open) && (
        <BuildingInfo
          view={viewOf(open)!}
          onClose={() => setWindow(null)}
          action={
            open === 'ACADEMY' && units ? { label: 'Studies', onClick: () => setWindow({ kind: 'studies' }) }
              : open === 'BARRACKS' && units ? { label: 'Train troops', onClick: () => setWindow({ kind: 'recruit' }) }
              : open === 'TOWN_HALL' && buildings ? { label: 'Buildings', onClick: () => setWindow({ kind: 'buildings' }) }
              : undefined
          }
        />
      )}
      {building && buildings && (
        <BuildingsWindow
          buildings={buildings}
          townHallLevel={viewOf('TOWN_HALL')?.level ?? 0}
          detail={detail}
          busy={busy}
          onUpgrade={onUpgrade}
          onCancel={onCancelBuild}
          onClose={() => setWindow(null)}
        />
      )}
      {recruiting && units && (
        <RecruitWindow
          units={units}
          barracksLevel={viewOf('BARRACKS')?.level ?? 0}
          detail={detail}
          busy={busy}
          onRecruit={onRecruit}
          onCancel={onCancelRecruit}
          onClose={() => setWindow(null)}
        />
      )}
      {studies && units && (
        <StudiesWindow
          units={units}
          academyLevel={viewOf('ACADEMY')?.level ?? 0}
          detail={detail}
          busy={busy}
          onStudy={onStudy}
          onCancel={onCancelStudy}
          onClose={() => setWindow(null)}
        />
      )}

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
              const u = cityUnit(type)
              const label = nameOf(type) ?? type.toLowerCase().replace('_', ' ')
              return (
                <li key={type}>
                  <img className="cp-unit-icon" src={UNIT_ICONS[type]} alt="" title={label} />
                  <span className="cp-unit-name">{label}</span>
                  {u === undefined ? (
                    <span className="cp-unit-count">…</span>
                  ) : (
                    <span className="cp-unit-count" onMouseEnter={showTip} onMouseLeave={hideTip}>
                      <b className="at-home">{u.home.toLocaleString()}</b>
                      <i>/</i>
                      <b className="hosted">{u.supporting.toLocaleString()}</b>
                      <i>/</i>
                      <b className="away">{u.sentAway.toLocaleString()}</b>
                    </span>
                  )}
                </li>
              )
            })}
          </ul>
        </section>
      </aside>

      {/* Anchored to the counts' top-left corner and drawn to their left, clear of the panel's edge. */}
      {tip && (
        <div className="cp-tip" ref={tipRef} style={{ left: tip.x, top: tip.y }} role="tooltip">
          <span><b className="at-home">•</b> Own, in this city</span>
          <span><b className="hosted">•</b> In this city, supporting from another city</span>
          <span><b className="away">•</b> Away, supporting another city</span>
        </div>
      )}
    </div>
  )
}
