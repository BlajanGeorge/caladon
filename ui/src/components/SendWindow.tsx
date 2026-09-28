import { useState } from 'react'
import type { CityDetail, MovementKind, UnitType, UnitView } from '../api/worlds'
import { UNIT_ICONS, UNIT_ORDER } from '../city/unitIcons'
import { formatDuration } from '../city/format'

interface Props {
  units: UnitView[]
  /** The city, for the troops standing at home and for where it is on the map. */
  detail: CityDetail | null
  busy: boolean
  /** Where it is going, when the map has already chosen; the field is then not asked for. */
  target?: { x: number; y: number; name: string }
  /** Which errands are open; a barbarian village can only be attacked. */
  only?: MovementKind[]
  /** Extra classes, so the map can float it in the middle rather than over a plot. */
  className?: string
  onSend: (kind: MovementKind, x: number, y: number, units: Partial<Record<UnitType, number>>) => void
  onClose: () => void
}

/** Spying is not an errand troops go on; it is paid for from the Cave, and is not here. */
const KINDS: { kind: MovementKind; label: string }[] = [
  { kind: 'ATTACK', label: 'Attack' },
  { kind: 'SUPPORT', label: 'Support' },
]

/** "128|240" or "128 240" or "128,240" — whichever the player types. */
function parseTarget(text: string): { x: number; y: number } | null {
  const m = text.trim().match(/^(\d{1,3})\s*[|,: ]\s*(\d{1,3})$/)
  if (!m) return null
  const x = Number(m[1])
  const y = Number(m[2])
  return x <= 999 && y <= 999 ? { x, y } : null
}

/** Sending troops somewhere: what kind of errand, which field, and how many of each unit go. */
export function SendWindow({ units, detail, busy, target, only, className, onSend, onClose }: Props) {
  const offered = KINDS.filter((k) => !only || only.includes(k.kind))
  const [kind, setKind] = useState<MovementKind>(offered[0].kind)
  const [typed, setTyped] = useState('')
  const [counts, setCounts] = useState<Partial<Record<UnitType, string>>>({})
  const byType = new Map(units.map((u) => [u.type, u]))
  const home = (t: UnitType) => detail?.units.find((u) => u.type === t)?.home ?? 0
  const want = (t: UnitType) => Math.max(0, Math.min(Math.floor(Number(counts[t] ?? '') || 0), home(t)))

  const field = target ?? parseTarget(typed)
  const chosen = UNIT_ORDER.filter((t) => want(t) > 0)

  // What the march will cost and what it can bring back, worked out the way the server will: the
  // straight-line distance at the slowest unit's pace, and every unit's own carry.
  const away = field && detail
    ? Math.hypot(field.x - detail.x, field.y - detail.y)
    : 0
  const slowest = chosen.reduce((m, t) => Math.max(m, byType.get(t)?.speed ?? 0), 0)
  const travel = away > 0 && slowest > 0
    ? Math.round((away * slowest * 60) / (detail?.worldSpeed || 1))
    : 0
  const carry = chosen.reduce((n, t) => n + (byType.get(t)?.carry ?? 0) * want(t), 0)
  // The rules the server enforces, said before the click rather than after it. Choosing nothing yet is
  // not a complaint worth making: the button is simply dark until something is.
  const nothingChosen = chosen.length === 0
  const refusal =
    !field ? 'Enter the field as x|y'
      : field.x === detail?.x && field.y === detail?.y ? 'That is this city'
      : null

  return (
    <div className={'b-info studies send' + (className ? ` ${className}` : '')} role="dialog" aria-label="Send troops">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>{offered.length === 1 ? offered[0].label : 'Send troops'}</h3>
      <p className="b-info-level">{detail ? `From ${detail.name}` : 'From this city'}</p>

      <div className="send-head">
        {/* Only worth asking when there is a choice: from the map, the errand is already chosen. */}
        {offered.length > 1 && (
          <div className="send-kinds" role="group" aria-label="What kind of errand">
            {offered.map((k) => (
              <button
                key={k.kind}
                type="button"
                className={'send-kind' + (kind === k.kind ? ' on' : '')}
                onClick={() => setKind(k.kind)}
              >
                {k.label}
              </button>
            ))}
          </div>
        )}
        {target ? (
          <span className="send-target">Target <b className="send-there">{target.name}</b></span>
        ) : (
        <label className="send-target">
          Target
          <input
            className="recruit-count send-field"
            value={typed}
            placeholder="128|240"
            onChange={(e) => setTyped(e.target.value)}
            aria-label="Target field as x|y"
          />
        </label>
        )}
      </div>

      <ul className="study-list send-list">
        {UNIT_ORDER.filter((t) => byType.has(t)).map((type) => {
          const u = byType.get(type)!
          const at = home(type)
          return (
            <li key={type} className={at === 0 ? 'blocked' : 'ready'}>
              <img className="study-icon" src={UNIT_ICONS[type]} alt="" />
              <span className="study-name send-name">
                {u.name}
                <b>{at.toLocaleString()}</b>
              </span>
              <span className="study-action">
                <input
                  className="recruit-count"
                  inputMode="numeric"
                  placeholder="0"
                  disabled={at === 0}
                  value={counts[type] ?? ''}
                  onChange={(e) => setCounts((c) => ({ ...c, [type]: e.target.value.replace(/\D/g, '') }))}
                  aria-label={`How many ${u.name}`}
                />
                <button
                  type="button"
                  className="cave-max"
                  disabled={at === 0}
                  title={`Send every ${u.name} at home: ${at.toLocaleString()}`}
                  onClick={() => setCounts((c) => ({ ...c, [type]: String(at) }))}
                >
                  Max
                </button>
              </span>
            </li>
          )
        })}
      </ul>

      <div className="send-go">
        {refusal ? (
          <span className="send-why">{refusal}</span>
        ) : nothingChosen ? (
          <span className="send-why" />
        ) : (
          <span className="send-why send-facts">
            <span><i>Travel time</i><b>{formatDuration(travel)}</b></span>
            <span><i>Resources</i><b>{carry.toLocaleString()}</b></span>
          </span>
        )}
        <button
          type="button"
          className="b-info-action send-off"
          disabled={busy || nothingChosen || refusal !== null}
          onClick={() => {
            const load: Partial<Record<UnitType, number>> = {}
            for (const t of chosen) load[t] = want(t)
            onSend(kind, field!.x, field!.y, load)
            setCounts({})
          }}
        >
          Send
        </button>
      </div>
    </div>
  )
}
