import { useState } from 'react'
import type { CityDetail } from '../api/worlds'

interface Props {
  detail: CityDetail | null
  busy: boolean
  onStore: (amount: number) => void
  /** Sends a spy to a field, paid for out of the Cave. */
  onSpy: (x: number, y: number, silver: number) => void
  onClose: () => void
}

/** "128|240" or "128 240" or "128,240" — whichever the player types. */
function parseTarget(text: string): { x: number; y: number } | null {
  const m = text.trim().match(/^(\d{1,3})\s*[|,: ]\s*(\d{1,3})$/)
  if (!m) return null
  const x = Number(m[1])
  const y = Number(m[2])
  return x <= 999 && y <= 999 ? { x, y } : null
}

/**
 * The Cave's silver: what it holds, what it can hold, and moving some in from the city's stock. Silver
 * only ever goes one way; it leaves by being spent on spying.
 */
export function CaveWindow({ detail, busy, onStore, onSpy, onClose }: Props) {
  const [typed, setTyped] = useState('')
  const [target, setTarget] = useState('')
  const [spend, setSpend] = useState('')
  const held = detail?.cave.silver ?? 0
  const capacity = detail?.cave.capacity ?? 0
  const stock = detail?.resources.silver.stock ?? 0
  const most = Math.max(0, Math.min(stock, capacity - held))
  const amount = Math.max(0, Math.min(Math.floor(Number(typed) || 0), most))

  // Only the things the player cannot see for themselves; an empty field says enough on its own.
  const field = parseTarget(target)
  const silver = Math.max(0, Math.min(Math.floor(Number(spend) || 0), held))
  const spyRefusal =
    capacity === 0 ? 'Build a Cave first'
      : held === 0 ? 'No silver in the Cave to pay a spy'
      : !field ? 'Enter the field as x|y'
      : field.x === detail?.x && field.y === detail?.y ? 'That is this city'
      : silver === 0 ? 'Enter how much silver to spend'
      : null

  const refusal =
    capacity === 0 ? 'Build a Cave first'
      : most === 0 ? (held >= capacity ? 'The Cave is full' : 'No silver to move')
      : null

  return (
    <div className="b-info cave" role="dialog" aria-label="Cave">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Cave</h3>
      <p className="b-info-level">{held.toLocaleString()} of {capacity.toLocaleString()} silver</p>
      <p className="b-info-desc">
        Silver kept here pays for this city's spying, and another city's spies must outbid it to learn
        anything. It never comes back out into the stock.
      </p>

      <dl className="b-info-now">
        <dt>In the city</dt>
        <dd>{stock.toLocaleString()} silver</dd>
        <dt>Room left</dt>
        <dd>{Math.max(0, capacity - held).toLocaleString()} silver</dd>
      </dl>

      <div className="cave-go">
        {/* Spying is paid for from here, so it is sent from here. */}
        <span className="send-why">{spyRefusal ?? `Send a spy to ${field!.x}|${field!.y} with ${silver.toLocaleString()} silver`}</span>
        <div className="cave-row">
          <input
            className="recruit-count"
            value={target}
            placeholder="128|240"
            onChange={(e) => setTarget(e.target.value)}
            aria-label="Field to spy on"
          />
          <input
            className="recruit-count"
            inputMode="numeric"
            value={spend}
            placeholder="silver"
            onChange={(e) => setSpend(e.target.value.replace(/\D/g, ''))}
            aria-label="Silver to spend"
          />
          <button
            type="button"
            className="b-info-action cave-store"
            disabled={busy || spyRefusal !== null}
            onClick={() => { onSpy(field!.x, field!.y, silver); setSpend('') }}
          >
            Spy
          </button>
        </div>
      </div>

      <div className="cave-go">
        {(refusal || amount > 0) && (
          <span className="send-why">{refusal ?? `Move ${amount.toLocaleString()} silver in`}</span>
        )}
        <div className="cave-row">
        <input
          className="recruit-count"
          inputMode="numeric"
          placeholder={String(most)}
          value={typed}
          disabled={most === 0}
          onChange={(e) => setTyped(e.target.value.replace(/\D/g, ''))}
          aria-label="How much silver to move"
        />
        <button
          type="button"
          className="cave-max"
          disabled={most === 0}
          title={`Move everything that fits: ${most.toLocaleString()} silver`}
          onClick={() => setTyped(String(most))}
        >
          Max
        </button>
        <button
          type="button"
          className="b-info-action cave-store"
          disabled={busy || refusal !== null || amount === 0}
          onClick={() => { onStore(amount); setTyped('') }}
        >
          Store
        </button>
        </div>
      </div>
    </div>
  )
}
