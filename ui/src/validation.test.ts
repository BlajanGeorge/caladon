import { describe, expect, it } from 'vitest'
import { registerConflictField, validateRegister } from './validation'

describe('validateRegister', () => {
  it('accepts valid input', () => {
    expect(validateRegister({ email: 'a@b.com', nickname: 'george', password: 'password1', confirm: 'password1' })).toEqual({})
  })

  it('mirrors the server rules', () => {
    const errors = validateRegister({ email: 'nope', nickname: 'x-y', password: 'short', confirm: 'short' })
    expect(Object.keys(errors).sort()).toEqual(['email', 'nickname', 'password'])
    expect(validateRegister({ email: 'a@b.com', nickname: 'a'.repeat(21), password: 'p'.repeat(73), confirm: '' })).toMatchObject({
      nickname: expect.any(String),
      password: expect.any(String),
    })
  })

  it('requires the confirmation to match', () => {
    expect(validateRegister({ email: 'a@b.com', nickname: 'george', password: 'password1', confirm: 'password2' })).toEqual({
      confirm: 'Passwords do not match',
    })
  })

  it('maps 409 codes to fields', () => {
    expect(registerConflictField('EMAIL_TAKEN')).toBe('email')
    expect(registerConflictField('NICKNAME_TAKEN')).toBe('nickname')
    expect(registerConflictField('OTHER')).toBeUndefined()
  })
})
