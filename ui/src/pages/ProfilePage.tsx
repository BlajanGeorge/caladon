import { useNavigate } from 'react-router-dom'

export function ProfilePage() {
  const navigate = useNavigate()
  return (
    <div className="page">
      <h1>Profile</h1>
      <p>Your profile is coming soon.</p>
      <button className="secondary" onClick={() => navigate(-1)}>Back</button>
    </div>
  )
}
