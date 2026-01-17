import { useNavigate } from 'react-router-dom';
import './App.css';

function Home() {
    const navigate = useNavigate();

    return (
        <div className="home-container">
            <h1>🚀 부스터 백엔드 어드민</h1>
            <p>식당 관리 시스템에 오신 것을 환영합니다.</p>
            <button className="primary-btn big-btn" onClick={() => navigate('/dashboard')}>
                식당 관리 대시보드 입장
            </button>
        </div>
    );
}

export default Home;