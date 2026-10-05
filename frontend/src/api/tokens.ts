/**
 * Access JWT storage. Kept in the shared `api` layer so the client can attach
 * and renew the token; the auth feature re-exports these for pages.
 * The refresh token lives only in the backend's HTTP-only cookie (`/refresh`
 * reads that cookie), so it is never stored here; a value left in
 * `localStorage` under its key is removed.
 * The storage strategy (here, `localStorage`) can be swapped in one place.
 */
const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";

/**
 * Stores a new access token from the login cookie exchange, a signup call
 * (role PENDING → STUDENT/OWNER) or `/refresh`.
 */
export function saveAccessToken(accessToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function isLoggedIn(): boolean {
  return getAccessToken() !== null;
}
