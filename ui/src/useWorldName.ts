import { useEffect, useState } from 'react'
import { worldsApi } from './api/worlds'

/** World name for the top bar: from navigation state when available, otherwise looked up. */
export function useWorldName(worldId: number, fromState?: string): string | undefined {
  const [name, setName] = useState(fromState)
  useEffect(() => {
    if (name) return
    worldsApi.mine()
      .then((worlds) => setName(worlds.find((w) => w.id === worldId)?.name))
      .catch(() => {})
  }, [name, worldId])
  return name
}
