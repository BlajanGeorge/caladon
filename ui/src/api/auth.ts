import { api } from './client'
import { session } from '../session'

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  nickname: string
}

export interface Profile {
  nickname: string
  email: string
  role: string
  /** When the account was made. */
  since: string
}

export const authApi = {
  me(): Promise<Profile> {
    return api<Profile>('/auth/me')
  },

  /**
   * Changes the password. Every other session is revoked by the server, which hands this one a fresh
   * pair so the browser it was done in stays signed in.
   */
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    const res = await api<LoginResponse>('/auth/password', { method: 'POST', body: { currentPassword, newPassword } })
    session.set({ accessToken: res.accessToken, refreshToken: res.refreshToken, nickname: res.nickname })
  },

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
