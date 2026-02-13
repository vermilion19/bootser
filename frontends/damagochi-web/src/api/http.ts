import type { ApiErrorPayload } from "./types";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.toString() ?? "http://localhost:8080";

export class ApiError extends Error {
  readonly status: number;
  readonly payload?: ApiErrorPayload;

  constructor(status: number, payload?: ApiErrorPayload) {
    super(payload?.message ?? `HTTP ${status}`);
    this.status = status;
    this.payload = payload;
  }
}

interface RequestOptions extends RequestInit {
  userId?: number;
}

export async function apiRequest<T>(
  path: string,
  { headers, userId, ...init }: RequestOptions = {}
): Promise<T> {
  const finalHeaders = new Headers(headers);
  if (!finalHeaders.has("Content-Type") && init.body != null) {
    finalHeaders.set("Content-Type", "application/json");
  }
  if (userId != null) {
    finalHeaders.set("X-User-Id", String(userId));
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: finalHeaders,
  });

  if (!res.ok) {
    let payload: ApiErrorPayload | undefined;
    try {
      payload = (await res.json()) as ApiErrorPayload;
    } catch {
      payload = undefined;
    }
    throw new ApiError(res.status, payload);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  return (await res.json()) as T;
}
