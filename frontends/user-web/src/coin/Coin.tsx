import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import './Coin.css';

// 코인별 현재 상태 정보 (가격, 등락폭 등)
interface CoinStatus {
    currentPrice: number;
    changePrice: number;
    changeRate: number;
    changeType: string; // "RISE", "FALL", "EVEN"
}

// 관리할 코인 목록
const TARGET_CODES = ["KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-DOGE"];

// 코인 한글명 매핑
const COIN_NAMES: Record<string, string> = {
    "KRW-BTC": "비트코인",
    "KRW-ETH": "이더리움",
    "KRW-XRP": "리플",
    "KRW-DOGE": "도지코인"
};

function Coin() {
    const navigate = useNavigate();
    const eventSourceRef = useRef<EventSource | null>(null);
    const [connectionStatus, setConnectionStatus] = useState('Connecting...');

    // 코인별 현재가 정보 저장소
    const [coinStatusMap, setCoinStatusMap] = useState<Record<string, CoinStatus>>({
        "KRW-BTC": { currentPrice: 0, changePrice: 0, changeRate: 0, changeType: 'EVEN' },
        "KRW-ETH": { currentPrice: 0, changePrice: 0, changeRate: 0, changeType: 'EVEN' },
        "KRW-XRP": { currentPrice: 0, changePrice: 0, changeRate: 0, changeType: 'EVEN' },
        "KRW-DOGE": { currentPrice: 0, changePrice: 0, changeRate: 0, changeType: 'EVEN' },
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

                const code = data.code;
                if (!TARGET_CODES.includes(code)) return;

                const price = Number(data.trade_price ?? data.tradePrice ?? 0);
                if (!price) return;

                setCoinStatusMap(prev => ({
                    ...prev,
                    [code]: {
                        currentPrice: price,
                        changePrice: Number(data.change_price ?? data.changePrice ?? 0),
                        changeRate: Number(data.change_rate ?? data.changeRate ?? 0) * 100,
                        changeType: data.change ?? 'EVEN'
                    }
                }));

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

    // 가격 포맷팅
    const formatPrice = (val: number) => new Intl.NumberFormat('ko-KR').format(val);

    // 등락률 포맷팅
    const formatChangeRate = (rate: number, type: string) => {
        const sign = type === 'RISE' ? '+' : type === 'FALL' ? '-' : '';
        return `${sign}${Math.abs(rate).toFixed(2)}%`;
    };

    // 색상 결정
    const getColorClass = (type: string) => {
        if (type === 'RISE') return 'rise';
        if (type === 'FALL') return 'fall';
        return 'even';
    };

    // row 클릭 시 상세 페이지로 이동
    const handleRowClick = (code: string) => {
        navigate(`/coin/${code}`);
    };

    return (
        <div className="coin-container">
            <div className="header-nav">
                <div className="nav-group">
                    <button className="secondary-btn" onClick={() => navigate('/')}>← Home</button>
                </div>

                <h1>Crypto Live Dashboard</h1>

                <span className="status-badge">{connectionStatus}</span>
            </div>

            {/* 코인 테이블 */}
            <div className="coin-table-wrapper">
                <table className="coin-table">
                    <thead>
                        <tr>
                            <th>코인</th>
                            <th>현재가</th>
                            <th>전일대비</th>
                            <th>등락률</th>
                        </tr>
                    </thead>
                    <tbody>
                        {TARGET_CODES.map(code => {
                            const status = coinStatusMap[code];
                            const symbol = code.split('-')[1];
                            const colorClass = getColorClass(status.changeType);

                            return (
                                <tr
                                    key={code}
                                    onClick={() => handleRowClick(code)}
                                    className="coin-row"
                                >
                                    <td className="coin-name-cell">
                                        <span className="coin-symbol">{symbol}</span>
                                        <span className="coin-korean-name">{COIN_NAMES[code]}</span>
                                    </td>
                                    <td className={`price-cell ${colorClass}`}>
                                        {formatPrice(status.currentPrice)} <span className="currency">KRW</span>
                                    </td>
                                    <td className={`change-cell ${colorClass}`}>
                                        {status.changeType === 'RISE' ? '▲' : status.changeType === 'FALL' ? '▼' : '-'}
                                        {formatPrice(status.changePrice)}
                                    </td>
                                    <td className={`rate-cell ${colorClass}`}>
                                        {formatChangeRate(status.changeRate, status.changeType)}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default Coin;