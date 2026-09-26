import type { BuildingType, BuildingView } from '../api/worlds'
import { describeEffect, describeGain } from '../city/buildingInfo'
import { formatRequirements } from '../city/format'

interface Props {
  view: BuildingView
  onClose: () => void
  /** Shown for buildings that open a window of their own, e.g. the Academy's studies. */
  action?: { label: string; onClick: () => void }
}

/** What one building is and does: its level, what it gives now and what the next level adds. */
export function BuildingInfo({ view, onClose, action }: Props) {
  const type = view.type as BuildingType
  const next = view.next
  const gain = describeGain(type, view)
  return (
    <div className="b-info" role="dialog" aria-label={view.name}>
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>{view.name}</h3>
      <p className="b-info-level">Level {view.level} of {view.maxLevel}</p>
      <p className="b-info-desc">{view.description}</p>

      <dl className="b-info-now">
        <dt>{view.effectLabel}</dt>
        <dd>{describeEffect(type, view.effect)}</dd>
        <dt>Points</dt>
        <dd>{view.points.toLocaleString()}</dd>
      </dl>

      {next ? (
        <div className="b-info-next">
          <h4>Level {next.level}</h4>
          {gain && <p className="b-info-gain">{gain}</p>}
          {next.blockedBy.length > 0 && (
            <p className="b-info-blocked">Needs {formatRequirements(next.blockedBy)}</p>
          )}
        </div>
      ) : (
        <p className="b-info-max">At its highest level.</p>
      )}

      {action && (
        <button type="button" className="b-info-action" onClick={action.onClick}>{action.label}</button>
      )}
    </div>
  )
}
