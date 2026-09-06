import { api } from './client'
import { session } from '../session'

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  nickname: string
}

export const authApi = {
  register(email: string, nickname: string, password: string): Promise<void> {
    return api<void>('/auth/register', { method: 'POST', body: { email, nickname, password }, auth: false })
  },

  async login(email: string, password: string): Promise<LoginResponse> {
    const res = await api<LoginResponse>('/auth/login', { method: 'POST', body: { email, password }, auth: false })
    session.set({ accessToken: res.accessToken, refreshToken: res.refreshToken, nickname: res.nickname })
    return res
  },

  /** Best effort: the local session is cleared even if the server call fails. */
  async logout(): Promise<void> {
    const current = session.get()
    try {
      if (current) await api<void>('/auth/logout', { method: 'POST', body: { refreshToken: current.refreshToken } })
    } catch {
      /* ignore: the session is gone locally either way */
    } finally {
      session.clear()
    }
  },
}
