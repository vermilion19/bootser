import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import './Coin.css';

// 차트 데이터 타입
interface ChartData {
    time: string;
    price: number;
    timestamp: number;
}

// 코인 상태 정보
interface CoinStatus {
    currentPrice: number;
    changePrice: number;
    changeRate: number;
    changeType: string;
}

// 코인 한글명 매핑
const COIN_NAMES: Record<string, string> = {
    "KRW-BTC": "비트코인",
    "KRW-ETH": "이더리움",
    "KRW-XRP": "리플",
    "KRW-DOGE": "도지코인"
};

function CoinDetail() {
    const navigate = useNavigate();
    const { code } = useParams<{ code: string }>();
    const eventSourceRef = useRef<EventSource | null>(null);
    const [connectionStatus, setConnectionStatus] = useState('Connecting...');

    // 차트 데이터
    const [chartData, setChartData] = useState<ChartData[]>([]);

    // 현재가 정보
    const [status, setStatus] = useState<CoinStatus>({
        currentPrice: 0,
        changePrice: 0,
        changeRate: 0,
        changeType: 'EVEN'
    });

    useEffect(() => {
        if (!code) return;

        const eventSource = new EventSource('/coin/v1/stream');
        eventSourceRef.current = eventSource;

        eventSource.onopen = () => {
            setConnectionStatus('🟢 Real-time Connected');
        };

        eventSource.addEventListener('trade', (event) => {
            try {
                const messageEvent = event as MessageEvent;
                const data = JSON.parse(messageEvent.data);

                // 현재 코인만 처리
                if (data.code !== code) return;

                const price = Number(data.trade_price ?? data.tradePrice ?? 0);
                if (!price) return;

                // 상태 업데이트
                setStatus({
                    currentPrice: price,
                    changePrice: Number(data.change_price ?? data.changePrice ?? 0),
                    changeRate: Number(data.change_rate ?? data.changeRate ?? 0) * 100,
                    changeType: data.change ?? 'EVEN'
                });

                // 차트 데이터 업데이트
                setChartData(prev => {
                    const newPoint = {
                        time: data.trade_time || "00:00:00",
                        price: price,
                        timestamp: data.trade_timestamp || Date.now()
                    };

                    // 중복 제거
                    if (prev.length > 0 && prev[prev.length - 1].timestamp === newPoint.timestamp) {
                        return prev;
                    }

                    const newArray = [...prev, newPoint];
                    // 최근 50개 유지
                    if (newArray.length > 50) {
                        return newArray.slice(newArray.length - 50);
                    }
                    return newArray;
                });

            } catch (error) {
                console.error("Parsing error:", error);
            }
        });

        eventSource.onerror = () => {
            setConnectionStatus('🔴 Connection Lost');
            eventSource.close();
        };

        return () => {
            eventSource.close();
        };
    }, [code]);

    // 가격 포맷팅
    const formatPrice = (val: number) => new Intl.NumberFormat('ko-KR').format(val);

    // 색상 결정
    const getColor = () => {
        if (status.changeType === 'RISE') return '#ef4444';
        if (status.changeType === 'FALL') return '#3b82f6';
        return '#94a3b8';
    };

    // 색상 클래스
    const getColorClass = () => {
        if (status.changeType === 'RISE') return 'rise';
        if (status.changeType === 'FALL') return 'fall';
        return 'even';
    };

    const symbol = code?.split('-')[1] || '';
    const coinName = code ? COIN_NAMES[code] || code : '';

    return (
        <div className="coin-container">
            <div className="header-nav">
                <div className="nav-group">
                    <button className="secondary-btn" onClick={() => navigate('/coin')}>← 목록으로</button>
                </div>

                <h1>{symbol} {coinName}</h1>

                <span className="status-badge">{connectionStatus}</span>
            </div>

            {/* 가격 정보 카드 */}
            <div className="coin-info-card">
                <div className="coin-title">
                    <span className="coin-symbol">{symbol}/KRW</span>
                    <span className="connection-badge">{coinName}</span>
                </div>
                <div className="price-display">
                    <h2 style={{ color: getColor() }}>
                        {formatPrice(status.currentPrice)} <span className="currency">KRW</span>
                    </h2>
                    <p className={`change-rate ${getColorClass()}`}>
                        {status.changeType === 'RISE' ? '▲' : status.changeType === 'FALL' ? '▼' : '-'}
                        {formatPrice(status.changePrice)} ({status.changeType === 'RISE' ? '+' : status.changeType === 'FALL' ? '-' : ''}{Math.abs(status.changeRate).toFixed(2)}%)
                    </p>
                </div>
            </div>

            {/* 차트 영역 */}
            <div className="chart-wrapper">
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                        <XAxis
                            dataKey="time"
                            stroke="#64748b"
                            tick={{ fontSize: 11 }}
                            interval="preserveStartEnd"
                        />
                        <YAxis
                            domain={[
                                (min: number) => Math.floor(min * 0.998),
                                (max: number) => Math.ceil(max * 1.002)
                            ]}
                            stroke="#64748b"
                            tickFormatter={(val) => val >= 1000000 ? `${(val/10000).toFixed(0)}만` : val.toLocaleString()}
                            width={70}
                            tick={{ fontSize: 11 }}
                        />
                        <Tooltip
                            contentStyle={{
                                backgroundColor: '#1e293b',
                                borderColor: '#475569',
                                color: '#fff',
                                fontSize: '12px'
                            }}
                            formatter={(val: number) => [formatPrice(val) + ' KRW', '가격']}
                            labelStyle={{ color: '#94a3b8' }}
                        />
                        <Line
                            type="monotone"
                            dataKey="price"
                            stroke={getColor()}
                            strokeWidth={2}
                            dot={false}
                            isAnimationActive={false}
                        />
                    </LineChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default CoinDetail;