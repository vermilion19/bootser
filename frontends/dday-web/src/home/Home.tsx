import { useEffect, useRef, useState } from 'react';
import { getCountries, getToday } from '../api/specialDayApi';
import type { CountryCodeResponse, TodayResponse, SpecialDayCategory } from '../api/types';
import { useCountUp } from '../hooks/useCountUp';
import './Home.css';

const CATEGORY_LABELS: Record<SpecialDayCategory, string> = {
    PUBLIC_HOLIDAY: 'Public Holiday',
    MEMORIAL_DAY: 'Memorial Day',
    SPORTS: 'Sports',
    ENTERTAINMENT: 'Entertainment',
    MOVIE: 'Movie',
    CUSTOM: 'Custom',
};

function formatDate(dateStr: string): string {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
    });
}

function UpcomingDday({ daysUntil }: { daysUntil: number }) {
    const count = useCountUp(daysUntil, 1400, true);
    return <div className="upcoming-dday">D-{count}</div>;
}

function Home() {
    const [countries, setCountries] = useState<CountryCodeResponse[]>([]);
    const [selectedCountry, setSelectedCountry] = useState<CountryCodeResponse>({
        code: 'KR',
        displayName: 'South Korea',
    });
    const [query, setQuery] = useState('');
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [today, setToday] = useState<TodayResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        function handleClick(e: MouseEvent) {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setDropdownOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    useEffect(() => {
        const timer = setTimeout(() => {
            if (dropdownOpen) {
                getCountries(query || undefined)
                    .then(setCountries)
                    .catch(() => setCountries([]));
            }
        }, 200);
        return () => clearTimeout(timer);
    }, [query, dropdownOpen]);

    useEffect(() => {
        setLoading(true);
        setError(null);
        getToday(selectedCountry.code)
            .then(setToday)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    }, [selectedCountry]);

    function handleSelectCountry(c: CountryCodeResponse) {
        setSelectedCountry(c);
        setQuery('');
        setDropdownOpen(false);
    }

    return (
        <div className="home">
            <section className="hero">
                <h1 className="hero-title">D-Day</h1>
                <p className="hero-subtitle">
                    Discover today's special moments around the world.
                </p>
            </section>

            <div className="country-selector" ref={dropdownRef}>
                <input
                    className="country-search-input"
                    type="text"
                    placeholder={`${selectedCountry.displayName} (${selectedCountry.code})`}
                    value={query}
                    onChange={(e) => {
                        setQuery(e.target.value);
                        setDropdownOpen(true);
                    }}
                    onFocus={() => setDropdownOpen(true)}
                    aria-label="Search countries"
                />
                {dropdownOpen && countries.length > 0 && (
                    <ul className="country-dropdown">
                        {countries.map((c) => (
                            <li
                                key={c.code}
                                className={c.code === selectedCountry.code ? 'active' : ''}
                                onClick={() => handleSelectCountry(c)}
                            >
                                <span>{c.displayName}</span>
                                <span className="code">{c.code}</span>
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            <div className="section-divider" />

            {loading ? (
                <div className="loading">
                    <div className="spinner" />
                    <p>Loading...</p>
                </div>
            ) : error ? (
                <div className="error-state">
                    <p>Something went wrong.</p>
                    <button
                        className="retry-btn"
                        onClick={() => setSelectedCountry({ ...selectedCountry })}
                    >
                        Try Again
                    </button>
                </div>
            ) : today ? (
                <div className="content">
                    <div className="date-display anim-fade-up" style={{ animationDelay: '0s' }}>
                        <div className="date-text">{formatDate(today.date)}</div>
                        <div className="country-label">{selectedCountry.displayName}</div>
                    </div>

                    <div className="section anim-fade-up" style={{ animationDelay: '0.15s' }}>
                        <div className="section-label">Today</div>
                        {today.hasSpecialDay && today.specialDays.length > 0 ? (
                            <div className="special-day-list">
                                {today.specialDays.map((item, i) => (
                                    <div
                                        className="special-day-item anim-slide-in"
                                        key={i}
                                        style={{ animationDelay: `${0.25 + i * 0.08}s` }}
                                    >
                                        <div className={`category-indicator ${item.category}`} />
                                        <div className="special-day-info">
                                            <div className="name">{item.name}</div>
                                            {item.description && (
                                                <div className="description">{item.description}</div>
                                            )}
                                            <span className="category-tag">
                                                {CATEGORY_LABELS[item.category]}
                                            </span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="no-special-day">
                                Nothing special today — enjoy the ordinary.
                            </div>
                        )}
                    </div>

                    {today.upcoming && (
                        <div className="upcoming-section anim-fade-up" style={{ animationDelay: '0.3s' }}>
                            <div className="section-label">Coming Up</div>
                            <div className="upcoming-name">{today.upcoming.name}</div>
                            <div className="upcoming-date">{formatDate(today.upcoming.date)}</div>
                            <UpcomingDday daysUntil={today.upcoming.daysUntil} />
                            <div className="upcoming-category">
                                {CATEGORY_LABELS[today.upcoming.category]}
                            </div>
                        </div>
                    )}
                </div>
            ) : null}
        </div>
    );
}

export default Home;
