import type { BuildingType } from '../api/worlds'
import townHall from '@assets/sprites/icon-town-hall.png'
import farm from '@assets/sprites/icon-farm.png'
import woodcutter from '@assets/sprites/icon-woodcutter.png'
import stoneMine from '@assets/sprites/icon-stone-mine.png'
import ironMine from '@assets/sprites/icon-iron-mine.png'
import deposit from '@assets/sprites/icon-deposit.png'
import barracks from '@assets/sprites/icon-barracks.png'
import academy from '@assets/sprites/icon-academy.png'
import vault from '@assets/sprites/icon-vault.png'
import wall from '@assets/sprites/icon-wall.png'

/**
 * A small picture of each building, the way the unit medallions stand for the units. Made from the
 * top-tier art (`b-*-3.png`) by `docs/building_icons.py`: the whole sprite scaled to fit a 96 px square,
 * never cropped, so the building is complete however small it is drawn.
 */
export const BUILDING_ICONS: Record<BuildingType, string> = {
  TOWN_HALL: townHall,
  FARM: farm,
  WOODCUTTER: woodcutter,
  STONE_MINE: stoneMine,
  IRON_MINE: ironMine,
  DEPOSIT: deposit,
  BARRACKS: barracks,
  ACADEMY: academy,
  VAULT: vault,
  WALL: wall,
}
