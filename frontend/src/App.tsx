import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Home from './home/Home.tsx';
import Dashboard from './dashboard/Dashboard.tsx';
import './App.css';
import Coin from "./coin/Coin.tsx";

function App() {
    return (
        <BrowserRouter>
            <Routes>
                {/* 주소가 http://localhost:5173/ 일 때 Home 컴포넌트 보여줌 */}
                <Route path="/" element={<Home />} />

                {/* 주소가 http://localhost:5173/dashboard 일 때 Dashboard 컴포넌트 보여줌 */}
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/coin" element={<Coin />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;