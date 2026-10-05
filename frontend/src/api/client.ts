import { clearTokens, getAccessToken, saveAccessToken } from "./tokens";

/**
 * Backend API client. All network access to the Spring Boot backend goes
 * through here (or a feature's own `api` module) — components must not call
 * `fetch` directly.
 */

export const BACKEND_API_BASE_URL: string = import.meta.env.VITE_BACKEND_API_BASE_URL;

/** Common backend envelope (`global/response/ApiResponse`). Null fields are omitted. */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: { code: string; message: string };
}

export class ApiError extends Error {
  readonly status: number;
  /** Backend `ErrorCode` code (e.g. `COMMON_400`), when the error body has one. */
  readonly code: string | undefined;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

/**
 * Thin wrapper around `fetch` that prefixes the backend base URL, sends cookies
 * (`credentials: "include"`) so the backend can read its HTTP-only JWT cookie,
 * and throws {@link ApiError} on non-2xx responses.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  // Always send JSON content type: the backend JWT endpoints declare
  // `consumes = application/json`, so a request without it is rejected with 415
  // even when it has no body. Merge headers last so a caller's headers (e.g.
  // Authorization) add to it instead of replacing it.
  const response = await fetch(`${BACKEND_API_BASE_URL}${path}`, {
    credentials: "include",
    ...init,
    headers: { "Content-Type": "application/json", ...init.headers },
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiResponse<unknown> | null;
    throw new ApiError(
      response.status,
      `Request to ${path} failed (${response.status})`,
      body?.error?.code,
    );
  }

  return response.json() as Promise<T>;
}

/** `Authorization: Bearer …` for the stored access token, or nothing when logged out. */
export function authHeaders(): Record<string, string> {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** In-flight `/refresh`, shared so parallel 401s trigger one refresh. */
let refreshing: Promise<boolean> | null = null;

/**
 * `POST /refresh` trades the HTTP-only refresh cookie for a new access token
 * (raw `{accessToken}`, no envelope) and rotates the cookie. Resolves `false`
 * when the cookie is missing, expired, or already used.
 */
function refreshAccessToken(): Promise<boolean> {
  refreshing ??= apiFetch<{ accessToken: string }>("/refresh", { method: "POST" })
    .then(({ accessToken }) => {
      saveAccessToken(accessToken);
      return true;
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/**
 * Authenticated backend call that returns the envelope's `data`
 * (`undefined` for endpoints that answer just `{"success":true}`).
 *
 * Adds the stored access token. On 401 it refreshes the token once and
 * retries; if the refresh fails it clears the stored tokens and rethrows the
 * 401 {@link ApiError}, so the caller can send the user to /login.
 * `init.headers` must be a plain object (it is spread, see ADR 0016).
 */
export async function apiData<T>(path: string, init: RequestInit = {}): Promise<T> {
  const send = async () => {
    const body = await apiFetch<ApiResponse<T>>(path, {
      ...init,
      headers: { ...authHeaders(), ...init.headers },
    });
    return body.data as T;
  };

  try {
    return await send();
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || getAccessToken() === null) throw error;
    if (!(await refreshAccessToken())) {
      clearTokens();
      throw error;
    }
    return send();
  }
}
