import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import type { OwnedCity } from '../api/worlds'
import profileUrl from '@assets/sprites/profile.png'
import accountUrl from '@assets/sprites/account.png'

interface Props {
  worldName?: string
  worldId: number
  city?: OwnedCity | null
}

export function MapTopBar({ worldName }: Props) {
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const rightRef = useRef<HTMLDivElement>(null)

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
