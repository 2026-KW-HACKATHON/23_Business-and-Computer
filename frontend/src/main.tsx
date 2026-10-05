import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/tokens.css'
import './index.css'
import App from './App.tsx'
import { DemoSessionStrip } from './features/auth'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {/* 둘러보기 중에는 모든 화면 위에 「둘러보기 중」 띠 */}
      <DemoSessionStrip />
      <App />
    </BrowserRouter>
  </StrictMode>,
)
