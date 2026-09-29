import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { authApi } from '../api/auth'
import { useToast } from '../components/Toast'

/** The way in: the game's own battlefield and dark panel, before there is any world to show. */
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
    <div className="city-shell">
      <main className="rk-body au-body">
        <form className="rk-panel au-panel" onSubmit={submit} noValidate>
          <h1 className="au-brand">Caladon</h1>
          <h2 className="au-title">Log in</h2>

          {error && <p className="au-bad" role="alert">{error}</p>}

          <div className="au-fields">
            <label className="au-field" htmlFor="email">
              <span>Email</span>
              <input
                id="email"
                className="au-input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
              />
            </label>
            <label className="au-field" htmlFor="password">
              <span>Password</span>
              <input
                id="password"
                className="au-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </label>
          </div>

          <button className="b-info-action au-go" type="submit" disabled={!email || !password || submitting}>
            Log in
          </button>
          <p className="au-links">No account yet? <Link to="/register">Register</Link></p>
        </form>
      </main>
    </div>
  )
}
