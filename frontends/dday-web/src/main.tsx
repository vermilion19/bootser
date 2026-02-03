import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

const isKorean = navigator.language.startsWith('ko');
document.title = isKorean ? 'D-Day - 오늘의 특별한 날' : 'D-Day - Today\'s Special Moments';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
