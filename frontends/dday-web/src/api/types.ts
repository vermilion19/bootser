export interface ApiResponse<T> {
    result: 'SUCCESS' | 'ERROR';
    data: T;
    message: string | null;
    errorCode: string | null;
}

export interface CountryCodeResponse {
    code: string;
    displayName: string;
}

export interface SpecialDayItem {
    name: string;
    category: SpecialDayCategory;
    description: string;
}

export interface UpcomingItem {
    name: string;
    date: string;
    daysUntil: number;
    category: SpecialDayCategory;
}

export interface TodayResponse {
    date: string;
    countryCode: string;
    hasSpecialDay: boolean;
    specialDays: SpecialDayItem[];
    upcoming: UpcomingItem | null;
}

export interface PastResponse {
    name: string;
    date: string;
    daysSince: number;
    category: SpecialDayCategory;
}

export type SpecialDayCategory =
    | 'PUBLIC_HOLIDAY'
    | 'MEMORIAL_DAY'
    | 'SPORTS'
    | 'ENTERTAINMENT'
    | 'MOVIE'
    | 'CUSTOM';
