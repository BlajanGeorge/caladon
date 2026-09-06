import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { authApi } from '../api/auth'
import { useToast } from '../components/Toast'
import { registerConflictField, validateRegister, type FieldErrors, type RegisterField } from '../validation'

export function RegisterPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [values, setValues] = useState({ email: '', nickname: '', password: '', confirm: '' })
  const [touched, setTouched] = useState<Partial<Record<RegisterField, boolean>>>({})
  const [serverErrors, setServerErrors] = useState<FieldErrors<RegisterField>>({})
  const [submitting, setSubmitting] = useState(false)

  const clientErrors = validateRegister(values)
  const errors: FieldErrors<RegisterField> = { ...clientErrors, ...serverErrors }
  const valid = Object.keys(clientErrors).length === 0

  function update(field: RegisterField, value: string) {
    setValues((v) => ({ ...v, [field]: value }))
    setServerErrors((e) => {
      const { [field]: _cleared, ...rest } = e
      return rest
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setTouched({ email: true, nickname: true, password: true, confirm: true })
    if (!valid) return
    setSubmitting(true)
    try {
      await authApi.register(values.email.trim(), values.nickname, values.password)
      toast.info('Account created. Please log in.')
      navigate('/login')
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const field = registerConflictField(err.code)
        if (field) setServerErrors({ [field]: field === 'email' ? 'This email is already registered' : 'This nickname is taken' })
      } else if (err instanceof ApiError && err.code === 'VALIDATION_ERROR' && err.details) {
        setServerErrors(err.details as FieldErrors<RegisterField>)
      } else {
        toast.error('Registration failed. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const field = (name: RegisterField, label: string, type = 'text') => (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <input
        id={name}
        type={type}
        value={values[name]}
        className={touched[name] && errors[name] ? 'invalid' : ''}
        onChange={(e) => update(name, e.target.value)}
        onBlur={() => setTouched((t) => ({ ...t, [name]: true }))}
        autoComplete={type === 'password' ? 'new-password' : name === 'email' ? 'email' : 'username'}
      />
      {touched[name] && errors[name] && <span className="error">{errors[name]}</span>}
    </div>
  )

  return (
    <main className="page">
      <form className="card" onSubmit={submit} noValidate>
        <h1>Create account</h1>
        {field('email', 'Email', 'email')}
        {field('nickname', 'Nickname')}
        {field('password', 'Password', 'password')}
        {field('confirm', 'Confirm password', 'password')}
        <button className="primary" type="submit" disabled={!valid || submitting}>Register</button>
        <p className="links">Already have an account? <Link to="/login">Log in</Link></p>
      </form>
    </main>
  )
}
