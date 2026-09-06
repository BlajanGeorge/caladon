/** Shape of window.CaladonSprites, defined by web/assets/procedural-sprites.js. */
export interface CaladonSprites {
  grassPattern(id: string): string
  tree(x: number, y: number, h: number, w: number): string
  forest(cx: number, cy: number, count: number, seed: number): string
  mountain(cx: number, cy: number, s?: number): string
  lake(cx: number, cy: number, s?: number): string
  slot(cx: number, cy: number): string
  village(cx: number, cy: number, tier: 1 | 2 | 3, seed: number): string
  barbarians(cx: number, cy: number, seed: number): string
  cityTierKey(points: number): 'entity.city.t1' | 'entity.city.t2' | 'entity.city.t3'
}

declare global {
  interface Window {
    CaladonSprites: CaladonSprites
  }
}
