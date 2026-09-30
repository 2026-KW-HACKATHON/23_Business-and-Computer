import { Navigate, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import IntroPage from './pages/IntroPage'
import LoginPage from './pages/LoginPage'
import RoleSelectPage from './pages/RoleSelectPage'
import CookiePage from './pages/CookiePage'

function App() {
  return (
    <Routes>
      {/* No splash screen: every app start shows the intro, then home or login. */}
      <Route path="/" element={<IntroPage />} />
      <Route path="/home" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup/role" element={<RoleSelectPage mode="signup" />} />
      <Route path="/demo/role" element={<RoleSelectPage mode="demo" />} />
      {/* Backend redirects here after a successful social login. */}
      <Route path="/cookie" element={<CookiePage />} />
      <Route path="*" element={<Navigate to="/home" replace />} />
    </Routes>
  )
}

export default App
