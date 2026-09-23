import type { BuildingType } from '../api/worlds'

/**
 * Building plots on the city ground image (web/assets/sprites/city-ground.png, 2048 × 1152, the generated image uncropped).
 * Mirrors web/assets/city-plots.json. `anchor` is the plot's bottom-centre in image pixels — where a
 * building sprite's ground anchor goes; `box` is the bare-dirt area a sprite should fit in.
 */
export const GROUND_SIZE = { width: 2048, height: 1152 }

/**
 * The generated picture has a soft, jagged transparent border. These insets are how deep it reaches
 * at its deepest on each side (measured), so positioning by them leaves no transparent sliver
 * showing at the screen corner.
 */
export const GROUND_INSETS = { left: 40, top: 80, right: 44, bottom: 69 }
export const GROUND_PAINTED = {
  width: GROUND_SIZE.width - GROUND_INSETS.left - GROUND_INSETS.right,
  height: GROUND_SIZE.height - GROUND_INSETS.top - GROUND_INSETS.bottom,
}

export interface Plot {
  anchor: [number, number]
  box: [number, number, number, number]
}

export const PLOTS: Record<Exclude<BuildingType, 'WALL'>, Plot> = {
  TOWN_HALL: { anchor: [1015, 640], box: [843, 432, 1186, 640] },
  DEPOSIT: { anchor: [590, 544], box: [494, 431, 685, 544] },
  VAULT: { anchor: [1155, 859], box: [1047, 663, 1251, 859] },
  BARRACKS: { anchor: [1443, 551], box: [1349, 438, 1534, 551] },
  ACADEMY: { anchor: [869, 855], box: [770, 661, 972, 855] },
  FARM: { anchor: [823, 395], box: [732, 285, 917, 395] },
  WOODCUTTER: { anchor: [1223, 392], box: [1143, 282, 1306, 392] },
  STONE_MINE: { anchor: [616, 726], box: [520, 611, 709, 726] },
  IRON_MINE: { anchor: [1418, 726], box: [1336, 618, 1503, 726] },
}

/** Anchor as CSS percentages of the image box. */
export function anchorPercent(plot: Plot): { left: string; top: string } {
  return { left: `${(100 * plot.anchor[0]) / GROUND_SIZE.width}%`, top: `${(100 * plot.anchor[1]) / GROUND_SIZE.height}%` }
}
