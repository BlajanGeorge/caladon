import type { BuildingType } from '../api/worlds'
import townHall1 from '@assets/sprites/b-town-hall-1.png'
import townHall2 from '@assets/sprites/b-town-hall-2.png'
import townHall3 from '@assets/sprites/b-town-hall-3.png'
import farm1 from '@assets/sprites/b-farm-1.png'
import farm2 from '@assets/sprites/b-farm-2.png'
import farm3 from '@assets/sprites/b-farm-3.png'
import wood1 from '@assets/sprites/b-woodcutter-1.png'
import wood2 from '@assets/sprites/b-woodcutter-2.png'
import wood3 from '@assets/sprites/b-woodcutter-3.png'
import stone1 from '@assets/sprites/b-stone-mine-1.png'
import stone2 from '@assets/sprites/b-stone-mine-2.png'
import stone3 from '@assets/sprites/b-stone-mine-3.png'
import iron1 from '@assets/sprites/b-iron-mine-1.png'
import iron2 from '@assets/sprites/b-iron-mine-2.png'
import iron3 from '@assets/sprites/b-iron-mine-3.png'
import deposit1 from '@assets/sprites/b-deposit-1.png'
import deposit2 from '@assets/sprites/b-deposit-2.png'
import deposit3 from '@assets/sprites/b-deposit-3.png'
import barracks1 from '@assets/sprites/b-barracks-1.png'
import barracks2 from '@assets/sprites/b-barracks-2.png'
import barracks3 from '@assets/sprites/b-barracks-3.png'
import academy1 from '@assets/sprites/b-academy-1.png'
import academy2 from '@assets/sprites/b-academy-2.png'
import academy3 from '@assets/sprites/b-academy-3.png'
import vault1 from '@assets/sprites/b-vault-1.png'
import vault2 from '@assets/sprites/b-vault-2.png'
import vault3 from '@assets/sprites/b-vault-3.png'

/**
 * A building's art at one tier: the image, plus where its footprint sits across the image (0..1,
 * measured from the art and mirrored in web/assets/building-footprints.json). The generators often
 * put the building off to one side; the view shifts the sprite by that much rather than padding the
 * image, because every sprite is drawn at the same width and padding would shrink the building.
 */
export interface BuildingArt {
  src: string
  footprint: number
  /** Optional: draw this one wider than the standard width, when the art is mostly scenery. */
  scale?: number
  /** Optional: push the base down the plot by this fraction of the plot's height. */
  drop?: number
  /** Optional: slide across the plot by this fraction of the plot's width (negative = left). */
  slide?: number
}

/** Three art tiers per building, as both reference games do: the sprite changes at about a third and
 *  two thirds of the building's max level. A building with no art yet simply isn't in here. */
const SPRITES: Partial<Record<BuildingType, [BuildingArt, BuildingArt, BuildingArt]>> = {
  TOWN_HALL: [
    { src: townHall1, footprint: 0.5702 },
    { src: townHall2, footprint: 0.4957 },
    { src: townHall3, footprint: 0.4975 },
  ],
  FARM: [
    { src: farm1, footprint: 0.5087 },
    { src: farm2, footprint: 0.6866 },
    { src: farm3, footprint: 0.3802 },
  ],
  WOODCUTTER: [
    { src: wood1, footprint: 0.5203 },
    { src: wood2, footprint: 0.5059 },
    { src: wood3, footprint: 0.679 },
  ],
  // The quarry art is mostly rock face, so the building is only a fraction of the image: these are
  // drawn wider, and their footprint points at the building rather than at the rubble the automatic
  // measurement found.
  STONE_MINE: [
    { src: stone1, footprint: 0.26, scale: 1.15, drop: 0.34, slide: -0.26 },
    { src: stone2, footprint: 0.24, scale: 1.1, drop: 0.34, slide: -0.26 },
    { src: stone3, footprint: 0.26, scale: 1.05, drop: 0.34, slide: -0.26 },
  ],
  IRON_MINE: [
    { src: iron1, footprint: 0.4715, slide: 0.08, drop: 0.22 },
    { src: iron2, footprint: 0.483, slide: 0.08, drop: 0.22 },
    { src: iron3, footprint: 0.386, slide: 0.08, drop: 0.22 },
  ],
  DEPOSIT: [
    { src: deposit1, footprint: 0.506, scale: 0.68 },
    { src: deposit2, footprint: 0.3661, scale: 0.72 },
    { src: deposit3, footprint: 0.5518, scale: 0.72 },
  ],
  BARRACKS: [
    { src: barracks1, footprint: 0.5842 },
    { src: barracks2, footprint: 0.4481 },
    { src: barracks3, footprint: 0.5328 },
  ],
  ACADEMY: [
    { src: academy1, footprint: 0.52, scale: 0.62 },
    { src: academy2, footprint: 0.5344, scale: 0.68 },
    { src: academy3, footprint: 0.3754, scale: 0.72 },
  ],
  VAULT: [
    { src: vault1, footprint: 0.4855, scale: 0.6, slide: 0.1, drop: 0.12 },
    { src: vault2, footprint: 0.4597, scale: 0.65, slide: 0.1, drop: 0.12 },
    { src: vault3, footprint: 0.6879, scale: 0.7, slide: 0.1, drop: 0.12 },
  ],
}

/**
 * The Wall has no plot. Art exists (a palisade ring with a gate, plus straight runs and a cropped gate
 * section, all under web/assets/sprites/b-wall-*.png) but nothing draws it yet: laid over the city it
 * read badly, so it waits for a better idea.
 */

/** 1, 2 or 3 for a level, given the building's max level. */
export function tierOf(level: number, maxLevel: number): 1 | 2 | 3 {
  if (level >= Math.round((maxLevel * 2) / 3)) return 3
  if (level >= Math.round(maxLevel / 3)) return 2
  return 1
}

/** The art for a built building, or undefined (not built, or none drawn yet). */
export function buildingArt(type: BuildingType, level: number, maxLevel: number): BuildingArt | undefined {
  const set = SPRITES[type]
  if (!set || level < 1) return undefined
  return set[tierOf(level, maxLevel) - 1]
}
