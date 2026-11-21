import type { ApiErrorBody } from "../types";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public service?: string | null,
  ) {
    super(message);
  }

  /** The node behind this feature is down (503 from the gateway, or the gateway itself is unreachable). */
  get serviceDown() {
    return this.code === "SERVICE_UNAVAILABLE";
  }
}

/** Called on any 401 so the app can drop the session and show the login page. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

export async function api<T>(path: string, init?: { method?: string; body?: unknown }): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method: init?.method ?? "GET",
      credentials: "same-origin",
      headers: init?.body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
    });
  } catch {
    throw new ApiError(0, "SERVICE_UNAVAILABLE", "Can't reach the server.", "gateway");
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    // A proxy error page instead of JSON means the gateway is down.
    if (res.status === 502 || res.status === 503 || res.status === 504) {
      throw new ApiError(res.status, "SERVICE_UNAVAILABLE", "Can't reach the server.", "gateway");
    }
  }

  if (!res.ok) {
    const err = (data ?? {}) as Partial<ApiErrorBody>;
    if (res.status === 401 && path !== "/auth/login") onUnauthorized();
    throw new ApiError(res.status, err.error ?? "ERROR", err.message ?? "Something went wrong.", err.service);
  }
  return data as T;
}
