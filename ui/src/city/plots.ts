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
export const GROUND_INSETS = { left: 36, top: 77, right: 42, bottom: 64 }
export const GROUND_PAINTED = {
  width: GROUND_SIZE.width - GROUND_INSETS.left - GROUND_INSETS.right,
  height: GROUND_SIZE.height - GROUND_INSETS.top - GROUND_INSETS.bottom,
}

export interface Plot {
  anchor: [number, number]
  box: [number, number, number, number]
}

export const PLOTS: Record<Exclude<BuildingType, 'WALL'>, Plot> = {
  TOWN_HALL: { anchor: [1017, 640], box: [857, 434, 1181, 640] },
  DEPOSIT: { anchor: [587, 534], box: [498, 434, 682, 534] },
  VAULT: { anchor: [1162, 859], box: [1069, 741, 1251, 859] },
  BARRACKS: { anchor: [1446, 547], box: [1347, 438, 1537, 547] },
  ACADEMY: { anchor: [861, 853], box: [777, 731, 954, 853] },
  FARM: { anchor: [826, 398], box: [744, 279, 914, 398] },
  WOODCUTTER: { anchor: [1224, 397], box: [1134, 278, 1307, 397] },
  STONE_MINE: { anchor: [615, 724], box: [529, 608, 725, 724] },
  IRON_MINE: { anchor: [1422, 722], box: [1318, 614, 1510, 722] },
}

/** Anchor as CSS percentages of the image box. */
export function anchorPercent(plot: Plot): { left: string; top: string } {
  return { left: `${(100 * plot.anchor[0]) / GROUND_SIZE.width}%`, top: `${(100 * plot.anchor[1]) / GROUND_SIZE.height}%` }
}
