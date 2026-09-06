import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, setFetchImpl, setSessionExpiredHandler } from './client'
import { session } from '../session'

// Minimal localStorage for the session module under Node.
const store = new Map<string, string>()
;(globalThis as { localStorage?: unknown }).localStorage = {
  getItem: (k: string) => store.get(k) ?? null,
  setItem: (k: string, v: string) => void store.set(k, v),
  removeItem: (k: string) => void store.delete(k),
}

function response(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api client', () => {
  const calls: { url: string; init: RequestInit }[] = []
  let responses: Response[] = []

  beforeEach(() => {
    calls.length = 0
    responses = []
    store.clear()
    session.set({ accessToken: 'old', refreshToken: 'refresh', nickname: 'george' })
    setFetchImpl(async (url, init) => {
      calls.push({ url: String(url), init: init ?? {} })
      return responses.shift() ?? response(500)
    })
  })

  it('sends the bearer token and parses JSON', async () => {
    responses = [response(200, [{ id: 1 }])]
    await expect(api('/worlds')).resolves.toEqual([{ id: 1 }])
    expect(calls[0].url).toBe('/api/v1/worlds')
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBe('Bearer old')
  })

  it('on 401 refreshes once, stores the new access token and retries', async () => {
    responses = [response(401, { error: 'UNAUTHORIZED' }), response(200, { accessToken: 'new' }), response(200, { ok: true })]
    await expect(api('/worlds')).resolves.toEqual({ ok: true })
    expect(calls.map((c) => c.url)).toEqual(['/api/v1/worlds', '/api/v1/auth/refresh', '/api/v1/worlds'])
    expect(JSON.parse(calls[1].init.body as string)).toEqual({ refreshToken: 'refresh' })
    expect((calls[2].init.headers as Record<string, string>).Authorization).toBe('Bearer new')
    expect(session.get()?.accessToken).toBe('new')
    expect(session.get()?.refreshToken).toBe('refresh')
  })

  it('clears the session and signals expiry when the refresh is rejected', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    responses = [response(401, { error: 'UNAUTHORIZED' }), response(401, { error: 'INVALID_REFRESH_TOKEN' })]
    await expect(api('/worlds')).rejects.toMatchObject({ status: 401, code: 'SESSION_EXPIRED' })
    expect(session.get()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
    expect(calls).toHaveLength(2)
  })

  it('does not try to refresh on public endpoints', async () => {
    responses = [response(401, { error: 'INVALID_CREDENTIALS' })]
    const err = (await api('/auth/login', { method: 'POST', body: { email: 'a', password: 'b' }, auth: false }).catch((e) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.code).toBe('INVALID_CREDENTIALS')
    expect(calls).toHaveLength(1)
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBeUndefined()
  })

  it('returns undefined for empty 201/204 bodies', async () => {
    responses = [new Response(null, { status: 201 })]
    await expect(api('/auth/register', { method: 'POST', body: {}, auth: false })).resolves.toBeUndefined()
  })

  it('exposes error codes and validation details', async () => {
    responses = [response(400, { error: 'VALIDATION_ERROR', details: { endX: 'too wide' } })]
    await expect(api('/x')).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR', details: { endX: 'too wide' } })
  })
})
