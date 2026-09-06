import { session } from '../session'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly details?: Record<string, string>,
  ) {
    super(code)
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  /** false for public endpoints (register/login/refresh): no bearer, no refresh-and-retry on 401. */
  auth?: boolean
}

/** Called when the refresh token is rejected: the app clears the session and shows Login. */
let onSessionExpired: () => void = () => {}
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

/** Overridable for tests. */
export let fetchImpl: typeof fetch = (...args) => fetch(...args)
export function setFetchImpl(f: typeof fetch) {
  fetchImpl = f
}

async function send(path: string, opts: RequestOptions, accessToken: string | null): Promise<Response> {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
  return fetchImpl(`/api/v1${path}`, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })
}

let refreshing: Promise<boolean> | null = null

/** POST /auth/refresh with the stored refresh token; true if a new access token was stored. */
function refreshAccessToken(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const current = session.get()
      if (!current) return false
      try {
        const res = await send('/auth/refresh', { method: 'POST', body: { refreshToken: current.refreshToken } }, null)
        if (!res.ok) return false
        const body = (await res.json()) as { accessToken: string }
        session.setAccessToken(body.accessToken)
        return true
      } catch {
        return false
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

async function parseError(res: Response): Promise<ApiError> {
  let code = 'UNKNOWN'
  let details: Record<string, string> | undefined
  try {
    const body = (await res.json()) as { error?: string; details?: Record<string, string> }
    if (body.error) code = body.error
    details = body.details
  } catch {
    /* no JSON body */
  }
  return new ApiError(res.status, code, details)
}

/**
 * Sends a request with the bearer token. On 401 it refreshes the access token once and retries;
 * if the refresh is rejected the session is cleared and the app returns to Login.
 */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const auth = opts.auth ?? true
  let res = await send(path, opts, auth ? (session.get()?.accessToken ?? null) : null)

  if (res.status === 401 && auth) {
    const refreshed = await refreshAccessToken()
    if (!refreshed) {
      session.clear()
      onSessionExpired()
      throw new ApiError(401, 'SESSION_EXPIRED')
    }
    res = await send(path, opts, session.get()?.accessToken ?? null)
  }

  if (!res.ok) throw await parseError(res)
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}
