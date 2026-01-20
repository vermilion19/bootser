import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import './Investment.css';

// --- 인터페이스 정의 ---
interface CoinDetail {
    code: string;
    amount: number;
    averagePrice: number;
    currentPrice: number;
    profitRate: number;
    profitAmount: number;
}

interface WalletResponse {
    totalKrw: number;
    totalAssetValue: number;
    totalProfitRate: number;
    coins: CoinDetail[];
}

interface AssetHistory {
    time: string;
    value: number;
    profitRate: number;
}

function Investment() {
    const navigate = useNavigate();
    const TEST_USER_ID = "user123";

    const [wallet, setWallet] = useState<WalletResponse | null>(null);
    const [loading, setLoading] = useState(false);

    // 그래프 데이터
    const [assetHistory, setAssetHistory] = useState<AssetHistory[]>([]);

    // 주문 상태
    const [orderType, setOrderType] = useState<'MARKET' | 'LIMIT'>('MARKET');
    const [coinCode, setCoinCode] = useState("KRW-BTC");
    const [price, setPrice] = useState<string>("");
    const [amount, setAmount] = useState<string>("");

    const eventSourceRef = useRef<EventSource | null>(null);

    useEffect(() => {
        fetchWallet();
        connectWalletStream();

        return () => {
            if (eventSourceRef.current) {
                eventSourceRef.current.close();
            }
        };
    }, []);

    const fetchWallet = async () => {
        try {
            const res = await fetch(`/investment/v1/wallet?userId=${TEST_USER_ID}`);
            if (res.ok) {
                const data = await res.json();
                setWallet(data);
                updateHistory(data);
            }
        } catch (error) {
            console.error("지갑 조회 실패:", error);
        }
    };

    const createWallet = async () => {
        setLoading(true);
        try {
            const res = await fetch('/investment/v1/wallet', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: TEST_USER_ID })
            });
            if (res.ok) {
                alert("지갑 생성 완료! 초기 자금 1억원이 지급되었습니다.");
                fetchWallet();
            } else {
                alert("지갑 생성 실패");
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const handleBuy = async () => {
        // [수정] 유효성 검사 로직 변경
        if (!amount) return alert("수량을 입력해주세요.");
        // 지정가일 때만 가격 입력 확인
        if (orderType === 'LIMIT' && !price) return alert("가격을 입력해주세요.");

        const endpoint = orderType === 'MARKET'
            ? '/investment/v1/buy/market'
            : '/investment/v1/buy/limit';

        const payload = {
            userId: TEST_USER_ID,
            coinCode,
            // 시장가면 가격 0으로 전송 (백엔드에서 현재가 처리), 지정가면 입력값 전송
            price: orderType === 'LIMIT' ? parseFloat(price) : 0,
            amount: parseFloat(amount)
        };

        try {
            const res = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                alert(`${orderType === 'MARKET' ? '시장가' : '지정가'} 매수 주문이 완료되었습니다.`);
                fetchWallet();
                setAmount("");
                // 지정가일 때만 가격 초기화 (시장가는 어차피 안보임)
                if (orderType === 'LIMIT') setPrice("");
            } else {
                alert("주문 실패 (잔액 부족 등)");
            }
        } catch (error) {
            console.error("주문 에러:", error);
        }
    };

    const updateHistory = (data: WalletResponse) => {
        setAssetHistory(prev => {
            const now = new Date();
            const timeStr = now.toLocaleTimeString('ko-KR', { hour12: false });

            const newPoint = {
                time: timeStr,
                value: data.totalAssetValue,
                profitRate: data.totalProfitRate
            };

            if (prev.length > 0 && prev[prev.length - 1].time === timeStr) return prev;

            const newArray = [...prev, newPoint];
            if (newArray.length > 50) return newArray.slice(newArray.length - 50);
            return newArray;
        });
    };

    const connectWalletStream = () => {
        if (eventSourceRef.current) eventSourceRef.current.close();

        const eventSource = new EventSource(`/investment/v1/stream/private?userId=${TEST_USER_ID}`);
        eventSourceRef.current = eventSource;

        eventSource.onopen = () => {
            console.log("💰 내 지갑 실시간 스트림 연결됨");
        };

        eventSource.addEventListener('wallet-update', (event) => {
            try {
                const messageEvent = event as MessageEvent;
                const data: WalletResponse = JSON.parse(messageEvent.data);

                setWallet(data);
                updateHistory(data);

            } catch (error) {
                console.error("SSE 파싱 에러:", error);
            }
        });

        eventSource.onerror = () => {
            eventSource.close();
        };
    };

    const formatKrw = (val: number | undefined) => new Intl.NumberFormat('ko-KR').format(val || 0);

    const getProfitColor = (rate: number) => {
        if (rate > 0) return '#ef4444';
        if (rate < 0) return '#3b82f6';
        return '#94a3b8';
    };

    return (
        <div className="invest-container">
            <div className="header-nav">
                <div className="nav-group">
                    <button className="secondary-btn" onClick={() => navigate('/')}>← Home</button>
                    <button className="secondary-btn" onClick={() => navigate('/coin')}>Live Market</button>
                </div>
                <h1>Investment Dashboard</h1>
                <span className="user-badge">User: {TEST_USER_ID}</span>
            </div>

            <div className="asset-summary-card">
                {!wallet ? (
                    <div className="empty-wallet">
                        <p>지갑 정보가 없습니다.</p>
                        <button className="primary-btn" onClick={createWallet} disabled={loading}>
                            {loading ? "생성 중..." : "🚀 모의투자 시작하기 (지갑 생성)"}
                        </button>
                    </div>
                ) : (
                    <div className="summary-grid">
                        <div className="summary-item">
                            <label>총 보유자산</label>
                            <h2>{formatKrw(wallet.totalAssetValue)} <span className="unit">KRW</span></h2>
                        </div>
                        <div className="summary-item">
                            <label>주문가능 현금</label>
                            <h2>{formatKrw(wallet.totalKrw)} <span className="unit">KRW</span></h2>
                        </div>
                        <div className="summary-item">
                            <label>총 수익률</label>
                            <h2 style={{ color: getProfitColor(wallet.totalProfitRate) }}>
                                {wallet.totalProfitRate > 0 ? '+' : ''}
                                {(wallet.totalProfitRate * 100).toFixed(2)}%
                            </h2>
                        </div>
                    </div>
                )}
            </div>

            {assetHistory.length > 1 && (
                <div className="chart-section card">
                    <h3>📈 실시간 총 자산 추이</h3>
                    <div style={{ width: '100%', height: 300 }}>
                        <ResponsiveContainer>
                            <AreaChart data={assetHistory}>
                                <defs>
                                    <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#e34a33" stopOpacity={0.3}/>
                                        <stop offset="95%" stopColor="#e34a33" stopOpacity={0}/>
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                                <XAxis
                                    dataKey="time"
                                    stroke="#64748b"
                                    tick={{ fontSize: 12 }}
                                    minTickGap={30}
                                />
                                <YAxis
                                    domain={['auto', 'auto']}
                                    stroke="#64748b"
                                    tickFormatter={(val) => `${(val/10000).toFixed(0)}만`}
                                    width={60}
                                />
                                <Tooltip
                                    contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', color: '#fff' }}
                                    formatter={(val: any) => [formatKrw(val), '총 자산']}
                                    labelStyle={{ color: '#94a3b8' }}
                                />
                                <Area
                                    type="monotone"
                                    dataKey="value"
                                    stroke="#e34a33"
                                    fillOpacity={1}
                                    fill="url(#colorValue)"
                                    isAnimationActive={false}
                                />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </div>
            )}

            <div className="main-content">
                <div className="order-panel card">
                    <div className="tab-header">
                        <button
                            className={orderType === 'MARKET' ? 'active' : ''}
                            onClick={() => setOrderType('MARKET')}
                        >
                            시장가 (즉시)
                        </button>
                        <button
                            className={orderType === 'LIMIT' ? 'active' : ''}
                            onClick={() => setOrderType('LIMIT')}
                        >
                            지정가 (예약)
                        </button>
                    </div>

                    <div className="order-form">
                        <div className="input-group">
                            <label>코인 코드</label>
                            <select value={coinCode} onChange={(e) => setCoinCode(e.target.value)}>
                                <option value="KRW-BTC">비트코인 (BTC)</option>
                                <option value="KRW-ETH">이더리움 (ETH)</option>
                                <option value="KRW-XRP">리플 (XRP)</option>
                                <option value="KRW-DOGE">도지코인 (DOGE)</option>
                            </select>
                        </div>

                        {/* [수정] 지정가(LIMIT) 일 때만 가격 입력창 표시 */}
                        {orderType === 'LIMIT' && (
                            <div className="input-group">
                                <label>희망 가격 (지정가)</label>
                                <input
                                    type="number"
                                    placeholder="가격 입력"
                                    value={price}
                                    onChange={(e) => setPrice(e.target.value)}
                                />
                            </div>
                        )}

                        <div className="input-group">
                            <label>주문 수량</label>
                            <input
                                type="number"
                                placeholder="수량 입력"
                                value={amount}
                                onChange={(e) => setAmount(e.target.value)}
                            />
                        </div>

                        <div className="order-summary">
                            <span>예상 주문금액:</span>
                            <strong>
                                {orderType === 'MARKET' ? (
                                    <span style={{ color: '#e34a33' }}>시장가로 체결됩니다</span>
                                ) : (
                                    `${formatKrw(Number(price) * Number(amount))} KRW`
                                )}
                            </strong>
                        </div>

                        <button className="primary-btn buy-btn" onClick={handleBuy}>
                            {orderType === 'MARKET' ? '시장가 매수' : '지정가 매수'}
                        </button>
                    </div>
                </div>

                <div className="portfolio-panel card">
                    <h3>보유 자산 목록</h3>
                    {wallet && wallet.coins && wallet.coins.length > 0 ? (
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                <tr>
                                    <th>자산</th>
                                    <th>보유수량</th>
                                    <th>매수평균가</th>
                                    <th>현재가</th>
                                    <th>평가손익</th>
                                    <th>수익률</th>
                                </tr>
                                </thead>
                                <tbody>
                                {wallet.coins.map((coin) => (
                                    <tr key={coin.code}>
                                        <td className="fw-bold">{coin.code.split('-')[1]}</td>
                                        <td>{coin.amount}</td>
                                        <td>{formatKrw(coin.averagePrice)}</td>
                                        <td>{formatKrw(coin.currentPrice)}</td>
                                        <td style={{ color: getProfitColor(coin.profitAmount) }}>
                                            {formatKrw(coin.profitAmount)}
                                        </td>
                                        <td style={{ color: getProfitColor(coin.profitRate) }}>
                                            {coin.profitRate > 0 ? '+' : ''}
                                            {(coin.profitRate * 100).toFixed(2)}%
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <p className="no-data">보유 중인 가상자산이 없습니다.</p>
                    )}
                </div>
            </div>
        </div>
    );
}

export default Investment;