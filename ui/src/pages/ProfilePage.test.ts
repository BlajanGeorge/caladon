import { describe, expect, it } from 'vitest'
import { worldForBar } from './ProfilePage'

const remembered = { id: 3, name: 'Caladon I' }

describe('worldForBar', () => {
  it('takes the world the player was sent with', () => {
    expect(worldForBar({ worldId: 7, worldName: 'Caladon VII' }, remembered)).toEqual({ id: 7, name: 'Caladon VII' })
  })

  it('points nowhere when the player came from the lobby', () => {
    // The lobby sends the keys with nothing in them: the player is in no world, and the bar must not
    // offer a city or a map until they are.
    expect(worldForBar({ worldId: undefined, worldName: undefined }, remembered)).toBeNull()
  })

  it('falls back to the last world only for a page opened cold', () => {
    expect(worldForBar(null, remembered)).toEqual(remembered)
    expect(worldForBar(null, null)).toBeNull()
  })
})
