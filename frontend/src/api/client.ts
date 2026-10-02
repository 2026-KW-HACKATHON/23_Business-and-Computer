/**
 * Backend API client. All network access to the Spring Boot backend goes
 * through here (or a feature's own `api` module) — components must not call
 * `fetch` directly.
 */

export const BACKEND_API_BASE_URL: string = import.meta.env.VITE_BACKEND_API_BASE_URL;

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
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
  // even when it has no body.
  const response = await fetch(`${BACKEND_API_BASE_URL}${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...init.headers },
    ...init,
  });

  if (!response.ok) {
    throw new ApiError(response.status, `Request to ${path} failed (${response.status})`);
  }

  return response.json() as Promise<T>;
}
