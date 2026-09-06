import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { authApi } from '../api/auth'
import { useToast } from '../components/Toast'

export function LoginPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await authApi.login(email.trim(), password)
      navigate('/lobby', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) setError('Email or password is incorrect')
      else toast.error('Login failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="page">
      <form className="card" onSubmit={submit} noValidate>
        <h1>Log in</h1>
        {error && <div className="form-error" role="alert">{error}</div>}
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        </div>
        <button className="primary" type="submit" disabled={!email || !password || submitting}>Log in</button>
        <p className="links">No account yet? <Link to="/register">Register</Link></p>
      </form>
    </main>
  )
}
