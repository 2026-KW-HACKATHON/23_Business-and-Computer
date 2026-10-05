import type { TokenPair } from "../types";

/**
 * Access/refresh JWT storage. Kept in one place so the storage strategy (here,
 * `localStorage`) can be swapped without touching pages.
 */
const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";

export function saveTokens({ accessToken, refreshToken }: TokenPair): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

/**
 * Stores a new access token from a signup call (role PENDING → STUDENT/OWNER).
 * Those calls send the refresh token only as an HTTP-only cookie, and
 * `/refresh` reads only that cookie, so the old stored refresh token is dropped.
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
