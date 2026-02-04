import { useEffect, useRef, useState } from 'react';
import { getCountries, getPast, getToday } from '../api/specialDayApi';
import type { CountryCodeResponse, PastResponse, TodayResponse, SpecialDayCategory } from '../api/types';
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

function PastDday({ daysSince }: { daysSince: number }) {
    const count = useCountUp(daysSince, 1400, true);
    return <div className="past-dday">D+{count}</div>;
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
    const [showPast, setShowPast] = useState(false);
    const [past, setPast] = useState<PastResponse | null>(null);
    const [pastLoading, setPastLoading] = useState(false);
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
        setPast(null);
        setShowPast(false);
        getToday(selectedCountry.code)
            .then(setToday)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    }, [selectedCountry]);

    useEffect(() => {
        if (!showPast) {
            setPast(null);
            return;
        }
        setPastLoading(true);
        getPast(selectedCountry.code)
            .then(setPast)
            .catch(() => setPast(null))
            .finally(() => setPastLoading(false));
    }, [showPast, selectedCountry]);

    function handleSelectCountry(c: CountryCodeResponse) {
        setSelectedCountry(c);
        setQuery('');
        setDropdownOpen(false);
    }

    return (
        <div className="home">
            <section className="hero">
                <div className="burst-wrapper">
                    <div className="burst-glow-left" />
                    <div className="burst-glow-right" />
                    <div className="burst-glow-center" />
                    <h1 className="hero-title">D-Day</h1>
                </div>
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

                    <div className="past-toggle anim-fade-up" style={{ animationDelay: '0.2s' }}>
                        <label className="toggle-label">
                            <input
                                type="checkbox"
                                className="toggle-checkbox"
                                checked={showPast}
                                onChange={(e) => setShowPast(e.target.checked)}
                            />
                            <span className="toggle-switch" />
                            <span className="toggle-text">Show most recent past event</span>
                        </label>
                    </div>

                    {showPast && (
                        <div className="past-section anim-fade-up" style={{ animationDelay: '0.25s' }}>
                            <div className="section-label">Recent Past</div>
                            {pastLoading ? (
                                <div className="past-loading">
                                    <div className="spinner" />
                                </div>
                            ) : past ? (
                                <>
                                    <div className="past-name">{past.name}</div>
                                    <div className="past-date">{formatDate(past.date)}</div>
                                    <PastDday daysSince={past.daysSince} />
                                    <div className="past-category">
                                        {CATEGORY_LABELS[past.category]}
                                    </div>
                                </>
                            ) : (
                                <div className="no-special-day">
                                    No recent past events found.
                                </div>
                            )}
                        </div>
                    )}

                    {/*{today.upcoming && (*/}
                    {/*    <div className="upcoming-section anim-fade-up" style={{ animationDelay: '0.3s' }}>*/}
                    {/*        <div className="section-label">Coming Up</div>*/}
                    {/*        <div className="upcoming-name">{today.upcoming.name}</div>*/}
                    {/*        <div className="upcoming-date">{formatDate(today.upcoming.date)}</div>*/}
                    {/*        <UpcomingDday daysUntil={today.upcoming.daysUntil} />*/}
                    {/*        <div className="upcoming-category">*/}
                    {/*            {CATEGORY_LABELS[today.upcoming.category]}*/}
                    {/*        </div>*/}
                    {/*    </div>*/}
                    {/*)}*/}
                    {today.upcoming && today.upcoming.length > 0 && (
                        <div className="upcoming-section anim-fade-up" style={{ animationDelay: '0.3s' }}>
                            <div className="section-label">Coming Up</div>

                            <div className="upcoming-list">
                                {/* 여기에 ?. 를 붙여주세요 */}
                                {today.upcoming?.map((item, index) => (
                                    <div
                                        key={`${item.name}-${index}`}
                                        className="upcoming-item"
                                        style={{ marginBottom: '20px' }}
                                    >
                                        <div className="upcoming-name">{item.name}</div>
                                        <div className="upcoming-date">{formatDate(item.date)}</div>
                                        <UpcomingDday daysUntil={item.daysUntil} />
                                        <div className="upcoming-category">
                                            {CATEGORY_LABELS[item.category]}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                </div>
            ) : null}
        </div>
    );
}

export default Home;
