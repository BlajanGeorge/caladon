import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { worldsApi, type OwnedCity } from '../api/worlds'
import profileUrl from '@assets/sprites/profile.png'
import accountUrl from '@assets/sprites/account.png'
import worldUrl from '@assets/sprites/hud-world.png'
import rankingUrl from '@assets/sprites/hud-ranking.png'
import cityUrl from '@assets/sprites/ctl-city.png'
import reportsUrl from '@assets/sprites/hud-reports.png'

interface Props {
  worldName?: string
  worldId: number
  city?: OwnedCity | null
  /** Rendered in the right-hand group, just before the Profile icon (the City view's resource strip). */
  strip?: ReactNode
  /** Show the World map button (the City view does; the Map itself does not). */
  showWorldButton?: boolean
  /** Show the City button (every screen but the city itself). */
  showCityButton?: boolean
}

export function MapTopBar({ worldName, worldId, city, strip, showWorldButton = false, showCityButton = false }: Props) {
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const rightRef = useRef<HTMLDivElement>(null)

  // What is waiting to be read, asked for on entry and then at the poll cadence of the rest of the HUD.
  useEffect(() => {
    if (!worldId) return
    let stop = false
    const ask = () => {
      worldsApi.reports(worldId, { limit: 1, page: 1 })
        .then((r) => { if (!stop) setUnread(r.unread) })
        .catch(() => {})
    }
    ask()
    const t = window.setInterval(ask, 60_000)
    return () => { stop = true; window.clearInterval(t) }
  }, [worldId])

  useEffect(() => {
    if (!menuOpen) return
    const onDown = (e: MouseEvent) => {
      if (rightRef.current && !rightRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    const onEsc = (e: KeyboardEvent) => e.key === 'Escape' && setMenuOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onEsc)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onEsc)
    }
  }, [menuOpen])

  async function logout() {
    await authApi.logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="map-topbar">
      <span className="mtb-world">{worldName ?? 'Caladon'}</span>

      <div className="mtb-right" ref={rightRef}>
        {strip}
        {showWorldButton && (
          <button
            type="button"
            className="mtb-icon-btn"
            title="World map"
            aria-label="World map"
            onClick={() => navigate(`/worlds/${worldId}/map`, { state: { city } })}
          >
            <img src={worldUrl} alt="" />
          </button>
        )}
        {showCityButton && (
          <button
            type="button"
            className="mtb-icon-btn"
            title="Your city"
            aria-label="Your city"
            onClick={() => navigate(`/worlds/${worldId}/city`, { state: { worldName, city } })}
          >
            <img src={cityUrl} alt="" />
          </button>
        )}
        <button
          type="button"
          className="mtb-icon-btn mtb-with-badge"
          title="Reports"
          aria-label={unread > 0 ? `Reports, ${unread} unread` : 'Reports'}
          onClick={() => navigate(`/worlds/${worldId}/reports`, { state: { worldName, city } })}
        >
          <img src={reportsUrl} alt="" />
          {unread > 0 && <span className="mtb-badge">{unread > 99 ? '99+' : unread}</span>}
        </button>
        <button
          type="button"
          className="mtb-icon-btn"
          title="Ranking"
          aria-label="Ranking"
          onClick={() => navigate(`/worlds/${worldId}/ranking`, { state: { worldName, city } })}
        >
          <img src={rankingUrl} alt="" />
        </button>
        <button type="button" className="mtb-icon-btn" title="Profile" aria-label="Profile" onClick={() => navigate('/profile')}>
          <img src={profileUrl} alt="" />
        </button>
        <div className="mtb-account">
          <button
            type="button"
            className="mtb-icon-btn"
            title="Account"
            aria-label="Account"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}
          >
            <img src={accountUrl} alt="" />
          </button>
          {menuOpen && (
            <div className="mtb-menu" role="menu">
              <button type="button" role="menuitem" onClick={() => navigate('/lobby')}><span>Back to Lobby</span></button>
              <button type="button" role="menuitem" className="danger" onClick={logout}><span>Logout</span></button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
