/**
 * The world the player was last in. Screens that sit outside a world — the profile — still want the
 * top bar's way back into one, and a bookmarked or reloaded page has no navigation state to read.
 */
export interface LastWorld {
  id: number
  name?: string
}

const KEY = 'caladon.lastWorld'

export function rememberWorld(world: LastWorld) {
  try {
    localStorage.setItem(KEY, JSON.stringify(world))
  } catch {
    /* storage unavailable: the bar simply offers less this session */
  }
}

export function lastWorld(): LastWorld | null {
  try {
    const raw = localStorage.getItem(KEY)
    const parsed = raw ? (JSON.parse(raw) as LastWorld) : null
    return parsed && typeof parsed.id === 'number' ? parsed : null
  } catch {
    return null
  }
}
