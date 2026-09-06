/** v1 session storage: tokens + nickname in localStorage. */
export interface Session {
  accessToken: string
  refreshToken: string
  nickname: string
}

const KEY = 'caladon.session'

type Listener = (session: Session | null) => void
const listeners = new Set<Listener>()

function read(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

function write(session: Session | null) {
  try {
    if (session) localStorage.setItem(KEY, JSON.stringify(session))
    else localStorage.removeItem(KEY)
  } catch {
    /* storage unavailable: session lives only in memory for this page */
  }
  listeners.forEach((l) => l(session))
}

export const session = {
  get: read,
  set: write,
  clear: () => write(null),
  setAccessToken(accessToken: string) {
    const current = read()
    if (current) write({ ...current, accessToken })
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}
