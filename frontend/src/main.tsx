import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/tokens.css'
import './index.css'
import App from './App.tsx'
import { DemoSessionStrip, RoleRouteGuard } from './features/auth'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {/* 둘러보기 중에는 모든 화면 위에 「둘러보기 중」 띠 */}
      <DemoSessionStrip />
      {/* 화면 주소의 역할과 토큰의 역할이 다르면 그 역할의 홈으로 */}
      <RoleRouteGuard>
        <App />
      </RoleRouteGuard>
    </BrowserRouter>
  </StrictMode>,
)
