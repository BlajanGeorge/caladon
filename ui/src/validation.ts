export type FieldErrors<K extends string> = Partial<Record<K, string>>

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const NICKNAME = /^[A-Za-z0-9_]{3,20}$/

export function validateEmail(email: string): string | undefined {
  if (!email.trim()) return 'Email is required'
  if (email.length > 255 || !EMAIL.test(email)) return 'Enter a valid email address'
  return undefined
}

export function validateNickname(nickname: string): string | undefined {
  if (!nickname) return 'Nickname is required'
  if (!NICKNAME.test(nickname)) return '3–20 characters: letters, digits and underscore'
  return undefined
}

export function validatePassword(password: string): string | undefined {
  if (!password) return 'Password is required'
  if (password.length < 8 || password.length > 72) return '8–72 characters'
  return undefined
}

export type RegisterField = 'email' | 'nickname' | 'password' | 'confirm'

export function validateRegister(v: { email: string; nickname: string; password: string; confirm: string }): FieldErrors<RegisterField> {
  const errors: FieldErrors<RegisterField> = {}
  const email = validateEmail(v.email)
  const nickname = validateNickname(v.nickname)
  const password = validatePassword(v.password)
  if (email) errors.email = email
  if (nickname) errors.nickname = nickname
  if (password) errors.password = password
  if (!password && v.confirm !== v.password) errors.confirm = 'Passwords do not match'
  return errors
}

/** Maps a 409 error code from /auth/register to the field it belongs to. */
export function registerConflictField(code: string): RegisterField | undefined {
  if (code === 'EMAIL_TAKEN') return 'email'
  if (code === 'NICKNAME_TAKEN') return 'nickname'
  return undefined
}
