import type { ApiResponse, CountryCodeResponse, TodayResponse } from './types';

const BASE = '/api/v1/special-days';

async function fetchApi<T>(url: string): Promise<T> {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const body: ApiResponse<T> = await res.json();
    if (body.result === 'ERROR') throw new Error(body.message ?? 'Unknown error');
    return body.data;
}

export function getCountries(query?: string): Promise<CountryCodeResponse[]> {
    const params = query ? `?query=${encodeURIComponent(query)}` : '';
    return fetchApi<CountryCodeResponse[]>(`${BASE}/countries${params}`);
}

export function getToday(countryCode = 'KR', timezone = 'UTC'): Promise<TodayResponse> {
    const params = new URLSearchParams({ countryCode, timezone });
    return fetchApi<TodayResponse>(`${BASE}/today?${params}`);
}
