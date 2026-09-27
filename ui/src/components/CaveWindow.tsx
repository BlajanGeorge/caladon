import { useState } from 'react'
import type { CityDetail } from '../api/worlds'
import silverUrl from '@assets/sprites/hud-silver.png'

interface Props {
  detail: CityDetail | null
  busy: boolean
  onStore: (amount: number) => void
  onClose: () => void
}

/**
 * The Cave's silver: what it holds, what it can hold, and moving some in from the city's stock. Silver
 * only ever goes one way; it leaves by being spent on spying.
 */
export function CaveWindow({ detail, busy, onStore, onClose }: Props) {
  const [typed, setTyped] = useState('')
  const held = detail?.cave.silver ?? 0
  const capacity = detail?.cave.capacity ?? 0
  const stock = detail?.resources.silver.stock ?? 0
  const most = Math.max(0, Math.min(stock, capacity - held))
  const amount = Math.max(0, Math.min(Math.floor(Number(typed) || 0), most))

  const refusal =
    capacity === 0 ? 'Build a Cave first'
      : most === 0 ? (held >= capacity ? 'The Cave is full' : 'No silver to move')
      : amount === 0 ? 'Enter how much'
      : null

  return (
    <div className="b-info" role="dialog" aria-label="Cave">
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

      <div className="send-go">
        <span className="send-why">{refusal ?? `Move ${amount.toLocaleString()} silver in`}</span>
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
          className="study-go"
          disabled={most === 0}
          title="Move everything that fits"
          onClick={() => setTyped(String(most))}
        >
          all
        </button>
        <button
          type="button"
          className="confirm-yes"
          disabled={busy || refusal !== null}
          onClick={() => { onStore(amount); setTyped('') }}
        >
          <img src={silverUrl} alt="" />
          Store
        </button>
      </div>
    </div>
  )
}
