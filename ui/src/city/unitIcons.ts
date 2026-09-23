import type { UnitType } from '../api/worlds'
import spearman from '@assets/sprites/hud-unit-spearman.png'
import swordsman from '@assets/sprites/hud-unit-swordsman.png'
import scout from '@assets/sprites/hud-unit-scout.png'
import axeman from '@assets/sprites/hud-unit-axeman.png'
import archer from '@assets/sprites/hud-unit-archer.png'
import lightCav from '@assets/sprites/hud-unit-light-cav.png'
import ram from '@assets/sprites/hud-unit-ram.png'
import heavyCav from '@assets/sprites/hud-unit-heavy-cav.png'
import catapult from '@assets/sprites/hud-unit-catapult.png'
import nobleman from '@assets/sprites/hud-unit-nobleman.png'

/** Medallion for each unit type (128 px copies of the 1024 px originals). */
export const UNIT_ICONS: Record<UnitType, string> = {
  SPEARMAN: spearman,
  SWORDSMAN: swordsman,
  SCOUT: scout,
  AXEMAN: axeman,
  ARCHER: archer,
  LIGHT_CAV: lightCav,
  RAM: ram,
  HEAVY_CAV: heavyCav,
  CATAPULT: catapult,
  NOBLEMAN: nobleman,
}

/** The order the troops are listed in: the order they unlock. */
export const UNIT_ORDER: UnitType[] = [
  'SPEARMAN', 'SWORDSMAN', 'SCOUT', 'AXEMAN', 'ARCHER', 'LIGHT_CAV', 'RAM', 'HEAVY_CAV', 'CATAPULT', 'NOBLEMAN',
]
