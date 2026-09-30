import { Navigate, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import IntroPage from './pages/IntroPage'
import LoginPage from './pages/LoginPage'
import RoleSelectPage from './pages/RoleSelectPage'
import CookiePage from './pages/CookiePage'
import OwnerSignupLayout from './pages/OwnerSignupLayout'
import OwnerSignupInfoPage from './pages/OwnerSignupInfoPage'
import OwnerSignupVerifyPage from './pages/OwnerSignupVerifyPage'
import OwnerSignupProfilePage from './pages/OwnerSignupProfilePage'
import OwnerSignupDonePage from './pages/OwnerSignupDonePage'
import StudentSignupLayout from './pages/StudentSignupLayout'
import StudentSignupInfoPage from './pages/StudentSignupInfoPage'
import StudentSignupVerifyPage from './pages/StudentSignupVerifyPage'
import StudentSignupProfilePage from './pages/StudentSignupProfilePage'
import StudentSignupDonePage from './pages/StudentSignupDonePage'

function App() {
  return (
    <Routes>
      {/* No splash screen: every app start shows the intro, then home or login. */}
      <Route path="/" element={<IntroPage />} />
      <Route path="/home" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup/role" element={<RoleSelectPage mode="signup" />} />
      <Route path="/demo/role" element={<RoleSelectPage mode="demo" />} />
      {/* Signup steps share their input through each layout route. */}
      <Route path="/signup/owner" element={<OwnerSignupLayout />}>
        <Route index element={<Navigate to="1" replace />} />
        <Route path="1" element={<OwnerSignupInfoPage />} />
        <Route path="2" element={<OwnerSignupVerifyPage />} />
        <Route path="3" element={<OwnerSignupProfilePage />} />
        <Route path="done" element={<OwnerSignupDonePage />} />
      </Route>
      <Route path="/signup/student" element={<StudentSignupLayout />}>
        <Route index element={<Navigate to="1" replace />} />
        <Route path="1" element={<StudentSignupInfoPage />} />
        <Route path="2" element={<StudentSignupVerifyPage />} />
        <Route path="3" element={<StudentSignupProfilePage />} />
        <Route path="done" element={<StudentSignupDonePage />} />
      </Route>
      {/* Backend redirects here after a successful social login. */}
      <Route path="/cookie" element={<CookiePage />} />
      <Route path="*" element={<Navigate to="/home" replace />} />
    </Routes>
  )
}

export default App
