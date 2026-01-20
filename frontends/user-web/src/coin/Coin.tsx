import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import './Coin.css';

// 1. 차트 데이터 타입 정의
interface ChartData {
    time: string;
    price: number;
    timestamp: number;
}

// 2. 코인별 현재 상태 정보 (가격, 등락폭 등)
interface CoinStatus {
    currentPrice: number;
    changePrice: number;
    changeType: string; // "RISE", "FALL", "EVEN"
}

// 3. 관리할 코인 목록
const TARGET_CODES = ["KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-DOGE"];

/* =========================================
   [Sub Component] 개별 코인 카드 (그래프 포함)
   ========================================= */
const CoinCard = ({ code, data, status }: { code: string, data: ChartData[], status: CoinStatus }) => {
    // 코인 이름만 추출 (KRW-BTC -> BTC)
    const symbol = code.split('-')[1];

    // 색상 결정
    const getColor = () => {
        if (status.changeType === 'RISE') return '#ef4444'; // Red
        if (status.changeType === 'FALL') return '#3b82f6'; // Blue
        return '#94a3b8'; // Gray
    };

    // 가격 포맷팅
    const formatPrice = (val: number) => new Intl.NumberFormat('ko-KR').format(val);

    return (
        <div className="coin-card">
            <div className="card-header">
                <h3>{symbol} <span className="currency">KRW</span></h3>
                <div className="price-info" style={{ color: getColor() }}>
                    <span className="current-price">{formatPrice(status.currentPrice)}</span>
                    <span className="change-info">
                        {status.changeType === 'RISE' ? '▲' : status.changeType === 'FALL' ? '▼' : '-'}
                        {formatPrice(status.changePrice)}
                    </span>
                </div>
            </div>

            <div className="mini-chart-wrapper">
                <ResponsiveContainer width="100%" height={200}>
                    <LineChart data={data}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                        <XAxis
                            dataKey="time"
                            stroke="#64748b"
                            tick={{ fontSize: 10 }}
                            interval="preserveStartEnd"
                        />
                        <YAxis
                            // [중요] 코인마다 가격 단위가 다르므로 비율로 여백 설정 (위아래 0.2%)
                            domain={[
                                (min: number) => Math.floor(min * 0.998),
                                (max: number) => Math.ceil(max * 1.002)
                            ]}
                            stroke="#64748b"
                            tickFormatter={(val) => val >= 1000000 ? `${(val/10000).toFixed(0)}만` : val.toLocaleString()}
                            width={50}
                            tick={{ fontSize: 10 }}
                            hide={false}
                        />
                        <Tooltip
                            contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', color: '#fff', fontSize: '12px' }}
                            formatter={(val: any) => [formatPrice(val), 'Price']}
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
};

/* =========================================
   [Main Component] 전체 화면
   ========================================= */
function Coin() {
    const navigate = useNavigate();
    const eventSourceRef = useRef<EventSource | null>(null);
    const [connectionStatus, setConnectionStatus] = useState('Connecting...');

    // 코인별 차트 데이터 저장소 (Key: 코인코드, Value: 데이터배열)
    const [coinDataMap, setCoinDataMap] = useState<Record<string, ChartData[]>>({
        "KRW-BTC": [], "KRW-ETH": [], "KRW-XRP": [], "KRW-DOGE": []
    });

    // 코인별 현재가 정보 저장소
    const [coinStatusMap, setCoinStatusMap] = useState<Record<string, CoinStatus>>({
        "KRW-BTC": { currentPrice: 0, changePrice: 0, changeType: 'EVEN' },
        "KRW-ETH": { currentPrice: 0, changePrice: 0, changeType: 'EVEN' },
        "KRW-XRP": { currentPrice: 0, changePrice: 0, changeType: 'EVEN' },
        "KRW-DOGE": { currentPrice: 0, changePrice: 0, changeType: 'EVEN' },
    });

    useEffect(() => {
        const eventSource = new EventSource('/coin/v1/stream');
        eventSourceRef.current = eventSource;

        eventSource.onopen = () => {
            setConnectionStatus('🟢 Real-time Connected');
        };

        // 'trade' 이벤트 수신
        eventSource.addEventListener('trade', (event) => {
            try {
                const messageEvent = event as MessageEvent;
                const data = JSON.parse(messageEvent.data);

                // 1. 우리가 원하는 코인인지 확인 ("KRW-BTC" 등)
                const code = data.code;
                if (!TARGET_CODES.includes(code)) return;

                const price = Number(data.trade_price ?? data.tradePrice ?? 0);
                if (!price) return;

                // 2. 해당 코인의 상태(Status) 업데이트
                setCoinStatusMap(prev => ({
                    ...prev,
                    [code]: {
                        currentPrice: price,
                        changePrice: Number(data.change_price ?? 0),
                        changeType: data.change ?? 'EVEN'
                    }
                }));

                // 3. 해당 코인의 차트 데이터(Data) 업데이트
                setCoinDataMap(prev => {
                    const currentArray = prev[code] || [];
                    const newPoint = {
                        time: data.trade_time || "00:00:00",
                        price: price,
                        timestamp: data.trade_timestamp || Date.now()
                    };

                    // 중복 제거
                    if (currentArray.length > 0 && currentArray[currentArray.length - 1].timestamp === newPoint.timestamp) {
                        return prev;
                    }

                    const newArray = [...currentArray, newPoint];
                    // 각 코인별로 최근 50개 유지
                    if (newArray.length > 50) return { ...prev, [code]: newArray.slice(newArray.length - 50) };
                    return { ...prev, [code]: newArray };
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
    }, []);

    return (
        <div className="coin-container">
            <div className="header-nav">
                {/* 버튼들을 그룹으로 묶어서 왼쪽 기둥에 배치 */}
                <div className="nav-group">
                    <button className="secondary-btn" onClick={() => navigate('/')}>← Home</button>
                    {/*<button className="primary-btn" onClick={() => navigate('/investment')}>My Wallet</button>*/}
                </div>

                <h1>Crypto Live Dashboard</h1>

                <span className="status-badge">{connectionStatus}</span>
            </div>

            {/* 4개의 그리드 레이아웃 */}
            <div className="charts-grid">
                {TARGET_CODES.map(code => (
                    <CoinCard
                        key={code}
                        code={code}
                        data={coinDataMap[code]}
                        status={coinStatusMap[code]}
                    />
                ))}
            </div>
        </div>
    );
}

export default Coin;