import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { session } from '../session'

interface Props {
  worldName?: string
  /** Extra navigation shown before Logout (e.g. Back to Lobby / Back to City). */
  links?: { to: string; label: string; state?: unknown }[]
}

export function TopBar({ worldName, links = [] }: Props) {
  const navigate = useNavigate()
  const nickname = session.get()?.nickname

  async function logout() {
    await authApi.logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="topbar">
      <span className="brand">Caladon</span>
      {worldName && <span className="world">{worldName}</span>}
      <span className="spacer" />
      {links.map((l) => (
        <Link key={l.to} className="btn" to={l.to} state={l.state}>{l.label}</Link>
      ))}
      <span className="nick">{nickname}</span>
      <button onClick={logout}>Logout</button>
    </header>
  )
}
