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
        if (!form.name) return alert('Please enter a restaurant name.');

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
        const promptMsg = type === 'entry' ? 'How many people are entering?' : 'How many people are leaving?';
        const size = prompt(promptMsg, '4');
        if (!size) return;

        await fetch(`/restaurants/v1/${id}/${type}?partySize=${size}`, { method: 'POST' });
        fetchRestaurants();
    };

    const handleUpdate = async (id: number) => {
        const newName = prompt('Enter new restaurant name:');
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
                <button className="secondary-btn" onClick={() => navigate('/')}>← Back to Home</button>
                <h1>Admin Dashboard</h1>
            </div>

            {/* 중요: 제목을 form-card 밖으로 빼야 Grid 정렬이 깨지지 않음 */}
            <div className="section-header">
                <h3>Register New Restaurant</h3>
            </div>

            <div className="card form-card">
                {/* 1. 식당 이름 입력 그룹 */}
                <div className="input-group">
                    <label>Restaurant Name</label>
                    <input
                        placeholder="e.g. Vermilion Dining"
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                    />
                </div>

                {/* 2. 수용 인원 입력 그룹 */}
                <div className="input-group">
                    <label>Capacity</label>
                    <input
                        type="number"
                        placeholder="0"
                        value={form.capacity || ''}
                        onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })}
                    />
                </div>

                {/* 3. 웨이팅 제한 입력 그룹 */}
                <div className="input-group">
                    <label>Max Waiting</label>
                    <input
                        type="number"
                        placeholder="0"
                        value={form.maxWaitingLimit || ''}
                        onChange={(e) => setForm({ ...form, maxWaitingLimit: Number(e.target.value) })}
                    />
                </div>

                {/* 4. 등록 버튼 */}
                <button className="primary-btn" onClick={handleCreate}>Register</button>
            </div>

            <div className="restaurant-list">
                {restaurants.map((rest) => (
                    <div key={rest.id} className={`card ${rest.status === 'OPEN' ? 'open' : 'closed'}`}>
                        <div className="card-header">
                            <h2>{rest.name}</h2>
                            <span className="badge">{rest.status}</span>
                        </div>

                        <div className="card-body">
                            <p>Occupancy: {rest.currentOccupancy} / {rest.capacity}</p>
                            <p>Max Waiting: {rest.maxWaitingLimit} Teams</p>
                        </div>

                        <div className="actions">
                            <button onClick={() => handleStatus(rest.id, 'open')} disabled={rest.status === 'OPEN'}>Open</button>
                            <button onClick={() => handleStatus(rest.id, 'close')} disabled={rest.status === 'CLOSED'}>Close</button>

                            <div className="divider" />

                            <button onClick={() => handleTraffic(rest.id, 'entry')}>Entry (+)</button>
                            <button onClick={() => handleTraffic(rest.id, 'exit')}>Exit (-)</button>

                            <div className="divider" />

                            <button className="secondary-btn" onClick={() => handleUpdate(rest.id)}>Edit Name</button>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default Dashboard;