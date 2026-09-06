import { useEffect, useState, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { setSessionExpiredHandler } from './api/client'
import { ToastProvider, useToast } from './components/Toast'
import { session } from './session'
import { CityPage } from './pages/CityPage'
import { LobbyPage } from './pages/LobbyPage'
import { LoginPage } from './pages/LoginPage'
import { MapPage } from './pages/MapPage'
import { RegisterPage } from './pages/RegisterPage'

function useSession() {
  const [current, setCurrent] = useState(session.get())
  useEffect(() => session.subscribe(setCurrent), [])
  return current
}

function RequireSession({ children }: { children: ReactNode }) {
  return useSession() ? <>{children}</> : <Navigate to="/login" replace />
}

function RedirectIfLoggedIn({ children }: { children: ReactNode }) {
  return useSession() ? <Navigate to="/lobby" replace /> : <>{children}</>
}

/** Wires the API client's "refresh token rejected" signal to navigation. */
function SessionExpiryHandler() {
  const navigate = useNavigate()
  const toast = useToast()
  useEffect(() => {
    setSessionExpiredHandler(() => {
      toast.error('Your session expired. Please log in again.')
      navigate('/login', { replace: true })
    })
  }, [navigate, toast])
  return null
}

export function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <SessionExpiryHandler />
        <Routes>
          <Route path="/" element={<Navigate to="/lobby" replace />} />
          <Route path="/register" element={<RedirectIfLoggedIn><RegisterPage /></RedirectIfLoggedIn>} />
          <Route path="/login" element={<RedirectIfLoggedIn><LoginPage /></RedirectIfLoggedIn>} />
          <Route path="/lobby" element={<RequireSession><LobbyPage /></RequireSession>} />
          <Route path="/worlds/:id/city" element={<RequireSession><CityPage /></RequireSession>} />
          <Route path="/worlds/:id/map" element={<RequireSession><MapPage /></RequireSession>} />
          <Route path="*" element={<Navigate to="/lobby" replace />} />
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  )
}
