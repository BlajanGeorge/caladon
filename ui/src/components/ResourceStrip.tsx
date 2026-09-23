import type { CityResources } from '../api/worlds'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import ironUrl from '@assets/sprites/hud-iron.png'
import populationUrl from '@assets/sprites/hud-population.png'

type ResourceKey = 'wood' | 'stone' | 'iron'
const RESOURCE_KEYS: ResourceKey[] = ['wood', 'stone', 'iron']

const ICONS: Record<ResourceKey | 'population', { src: string; label: string }> = {
  wood: { src: woodUrl, label: 'Wood' },
  stone: { src: stoneUrl, label: 'Stone' },
  iron: { src: ironUrl, label: 'Iron' },
  population: { src: populationUrl, label: 'Population' },
}

interface Props {
  /** Last server response; undefined while loading (icons render with placeholders). */
  resources?: CityResources
  population?: number
  /** Show the resource's name next to its medallion (the city panel does, the map HUD does not). */
  withNames?: boolean
}

/**
 * Wood / Stone / Iron / Population for the HUD top bar. Every number is shown exactly as the
 * server returned it; the values only change when the owner re-fetches (once a minute, or after
 * an action). No local ticking.
 */
export function ResourceStrip({ resources, population, withNames = false }: Props) {
  return (
    <div className="mtb-resources" aria-label="Resources">
      {RESOURCE_KEYS.map((key) => {
        const value = resources ? resources[key].stock : null
        const full = resources !== undefined && value !== null && value >= resources.capacity
        const title = resources
          ? `${ICONS[key].label}: +${resources[key].ratePerHour}/h · ${resources.capacity} cap`
          : ICONS[key].label
        return (
          <span key={key} className={'mtb-res' + (full ? ' full' : '')} title={title}>
            <img src={ICONS[key].src} alt={ICONS[key].label} />
            {withNames && <span className="mtb-res-name">{ICONS[key].label}</span>}
            <span className="mtb-res-value">{value === null ? '…' : value.toLocaleString()}</span>
          </span>
        )
      })}
      <span className="mtb-res" title="Population">
        <img src={ICONS.population.src} alt="Population" />
        {withNames && <span className="mtb-res-name">Population</span>}
        <span className="mtb-res-value">{population === undefined ? '…' : population.toLocaleString()}</span>
      </span>
    </div>
  )
}
