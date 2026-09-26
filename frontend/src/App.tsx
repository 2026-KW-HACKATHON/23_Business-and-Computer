import { Navigate, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import OnboardingPage from './pages/OnboardingPage'
import RoleSelectPage from './pages/RoleSelectPage'
import CookiePage from './pages/CookiePage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/onboarding" element={<OnboardingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup/role" element={<RoleSelectPage mode="signup" />} />
      <Route path="/demo/role" element={<RoleSelectPage mode="demo" />} />
      {/* Backend redirects here after a successful social login. */}
      <Route path="/cookie" element={<CookiePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
