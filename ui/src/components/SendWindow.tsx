import { useState } from 'react'
import type { CityDetail, MovementKind, UnitType, UnitView } from '../api/worlds'
import { UNIT_ICONS, UNIT_ORDER } from '../city/unitIcons'

interface Props {
  units: UnitView[]
  /** The city, for the troops standing at home and for where it is on the map. */
  detail: CityDetail | null
  busy: boolean
  onSend: (kind: MovementKind, x: number, y: number, units: Partial<Record<UnitType, number>>) => void
  onClose: () => void
}

/** Spying is not an errand troops go on; it is paid for from the Cave, and is not here. */
const KINDS: { kind: MovementKind; label: string; hint: string }[] = [
  { kind: 'ATTACK', label: 'Attack', hint: 'They fight what stands there and bring back what they can carry.' },
  { kind: 'SUPPORT', label: 'Support', hint: 'They stay and defend that city until you call them home.' },
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
export function SendWindow({ units, detail, busy, onSend, onClose }: Props) {
  const [kind, setKind] = useState<MovementKind>('ATTACK')
  const [target, setTarget] = useState('')
  const [counts, setCounts] = useState<Partial<Record<UnitType, string>>>({})
  const byType = new Map(units.map((u) => [u.type, u]))
  const home = (t: UnitType) => detail?.units.find((u) => u.type === t)?.home ?? 0
  const want = (t: UnitType) => Math.max(0, Math.min(Math.floor(Number(counts[t] ?? '') || 0), home(t)))

  const field = parseTarget(target)
  const chosen = UNIT_ORDER.filter((t) => want(t) > 0)
  // The rules the server enforces, said before the click rather than after it.
  const refusal =
    chosen.length === 0 ? 'Choose some troops'
      : !field ? 'Enter the field as x|y'
      : field.x === detail?.x && field.y === detail?.y ? 'That is this city'
      : null

  return (
    <div className="b-info studies send" role="dialog" aria-label="Send troops">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Send troops</h3>
      <p className="b-info-level">{detail ? `From ${detail.name} (${detail.x}|${detail.y})` : 'From this city'}</p>
      <p className="b-info-desc">{KINDS.find((k) => k.kind === kind)!.hint}</p>

      <div className="send-head">
        <div className="send-kinds" role="group" aria-label="What kind of errand">
          {KINDS.map((k) => (
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
        <label className="send-target">
          Target
          <input
            className="recruit-count send-field"
            value={target}
            placeholder="128|240"
            onChange={(e) => setTarget(e.target.value)}
            aria-label="Target field as x|y"
          />
        </label>
      </div>

      <ul className="study-list send-list">
        {UNIT_ORDER.filter((t) => byType.has(t)).map((type) => {
          const u = byType.get(type)!
          const at = home(type)
          return (
            <li key={type} className={at === 0 ? 'blocked' : 'ready'}>
              <img className="study-icon" src={UNIT_ICONS[type]} alt="" />
              <span className="study-name">
                {u.name}
                <em>{at.toLocaleString()} at home</em>
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
                  className="study-go"
                  disabled={at === 0}
                  title={`Send every ${u.name} at home`}
                  onClick={() => setCounts((c) => ({ ...c, [type]: String(at) }))}
                >
                  all
                </button>
              </span>
            </li>
          )
        })}
      </ul>

      <div className="send-go">
        <span className="send-why">{refusal ?? `${chosen.reduce((n, t) => n + want(t), 0).toLocaleString()} troops to ${field!.x}|${field!.y}`}</span>
        <button
          type="button"
          className="confirm-yes"
          disabled={busy || refusal !== null}
          onClick={() => {
            const load: Partial<Record<UnitType, number>> = {}
            for (const t of chosen) load[t] = want(t)
            onSend(kind, field!.x, field!.y, load)
            setCounts({})
          }}
        >
          Send them
        </button>
      </div>
    </div>
  )
}
