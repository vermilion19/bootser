import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './App.css';

interface Restaurant {
    id: number;
    name: string;
    capacity: number;
    currentOccupancy: number;
    maxWaitingLimit: number;
    status: 'OPEN' | 'CLOSED';
}

function Dashboard() {
    const navigate = useNavigate();
    const [restaurants, setRestaurants] = useState<Restaurant[]>([]);

    // 주소는 입력 안 받지만 백엔드 전송용으로 빈 값 유지
    const [form, setForm] = useState({
        name: '',
        capacity: 0,
        maxWaitingLimit: 30
    });

    useEffect(() => {
        fetchRestaurants();
    }, []);

    const fetchRestaurants = async () => {
        try {
            const response = await fetch('/restaurants/v1');
            if (response.ok) {
                const data = await response.json();
                if (Array.isArray(data)) setRestaurants(data);
                else if (data.content) setRestaurants(data.content);
                else if (data.data) setRestaurants(data.data);
                else setRestaurants([]);
            }
        } catch (error) {
            console.error(error);
            setRestaurants([]);
        }
    };

    const handleCreate = async () => {
        if (!form.name) return alert('식당 이름을 입력해주세요');

        await fetch('/restaurants/v1', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(form),
        });

        setForm({ name: '', capacity: 0, maxWaitingLimit: 30 });
        fetchRestaurants();
    };

    const handleStatus = async (id: number, type: 'open' | 'close') => {
        await fetch(`/restaurants/v1/${id}/${type}`, { method: 'POST' });
        fetchRestaurants();
    };

    const handleTraffic = async (id: number, type: 'entry' | 'exit') => {
        const size = prompt(`몇 명이 ${type === 'entry' ? '입장' : '퇴장'}하나요?`, '4');
        if (!size) return;
        await fetch(`/restaurants/v1/${id}/${type}?partySize=${size}`, { method: 'POST' });
        fetchRestaurants();
    };

    const handleUpdate = async (id: number) => {
        const newName = prompt('새로운 식당 이름을 입력하세요');
        if (!newName) return;
        await fetch(`/restaurants/v1/${id}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: newName }),
        });
        fetchRestaurants();
    };

    return (
        <div className="container">
            <div className="header-nav">
                <button className="secondary-btn" onClick={() => navigate('/')}>← 홈으로</button>
                <h1>🍽️ 식당 관리자 대시보드</h1>
            </div>

            <div className="card form-card">
                <h3>새 식당 등록</h3>
                <input
                    placeholder="식당 이름"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                />

                {/* 주소 입력창 삭제됨 */}

                <div className="input-group">
                    <label>수용 인원</label>
                    <input
                        type="number"
                        placeholder="0"
                        // [수정] 값이 0이면 빈 문자열('')을 보여줘서 0을 없앰
                        value={form.capacity || ''}
                        onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })}
                    />
                </div>

                <div className="input-group">
                    <label>최대 웨이팅</label>
                    <input
                        type="number"
                        placeholder="0"
                        // [수정] 값이 0이면 빈 문자열('')을 보여줘서 0을 없앰
                        value={form.maxWaitingLimit || ''}
                        onChange={(e) => setForm({ ...form, maxWaitingLimit: Number(e.target.value) })}
                    />
                </div>

                <button className="primary-btn" onClick={handleCreate}>등록하기</button>
            </div>

            <div className="restaurant-list">
                {restaurants.map((rest) => (
                    <div key={rest.id} className={`card ${rest.status === 'OPEN' ? 'open' : 'closed'}`}>
                        <div className="card-header">
                            <h2>{rest.name}</h2>
                            <span className="badge">{rest.status}</span>
                        </div>
                        {/* 주소가 없어도 에러 안 나게 처리 */}
                        <p>👥 인원: {rest.currentOccupancy} / {rest.capacity}</p>
                        <p>⏳ 최대 대기: {rest.maxWaitingLimit} 팀</p>

                        <div className="actions">
                            <button onClick={() => handleStatus(rest.id, 'open')} disabled={rest.status === 'OPEN'}>영업 시작</button>
                            <button onClick={() => handleStatus(rest.id, 'close')} disabled={rest.status === 'CLOSED'}>영업 종료</button>
                            <div className="divider" />
                            <button onClick={() => handleTraffic(rest.id, 'entry')}>입장 (+)</button>
                            <button onClick={() => handleTraffic(rest.id, 'exit')}>퇴장 (-)</button>
                            <div className="divider" />
                            <button className="secondary-btn" onClick={() => handleUpdate(rest.id)}>이름 수정</button>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default Dashboard;