import { useNavigate } from 'react-router-dom';
import './Home.css';

function Home() {
    const navigate = useNavigate();

    return (
        <div className="home-wrapper">
            <div className="content-box">
                <h1 className="brand-title">vermilion19</h1>
                <p className="brand-subtitle">System Administration & Control Center</p>
                <div className="divider-line"></div>
                <button className="enter-btn" onClick={() => navigate('/dashboard')}>
                    Dashboard Access
                </button>
                <button className="enter-btn" onClick={() => navigate('/coin')}>
                    Coin Trade
                </button>
            </div>
        </div>
    );
}

export default Home;