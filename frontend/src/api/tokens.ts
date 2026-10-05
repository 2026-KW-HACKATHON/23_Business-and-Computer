/**
 * Access JWT storage. Kept in the shared `api` layer so the client can attach
 * and renew the token; the auth feature re-exports these for pages.
 * The refresh token lives only in the backend's HTTP-only cookie (`/refresh`
 * reads that cookie), so it is never stored here; a value left in
 * `localStorage` under its key is removed.
 * The storage strategy (here, `localStorage`) can be swapped in one place.
 * A 둘러보기 (demo) login also keeps its `demoSessionId` here, so logging out
 * or a failed refresh ends the demo together with the token.
 */
const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";
const DEMO_SESSION_KEY = "demoSessionId";

/** Screens that show login or demo state re-render through this. */
const listeners = new Set<() => void>();

function notify(): void {
  listeners.forEach((listener) => listener());
}

/** For `useSyncExternalStore`: called whenever a token or the demo session changes. */
export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Stores a new access token from the login cookie exchange, a signup call
 * (role PENDING → STUDENT/OWNER) or `/refresh`.
 */
export function saveAccessToken(accessToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  notify();
}

/** Stores a `POST /demo/login` answer: the access token and the demo pair's id. */
export function saveDemoLogin(accessToken: string, demoSessionId: string): void {
  localStorage.setItem(DEMO_SESSION_KEY, demoSessionId);
  saveAccessToken(accessToken);
}

/** Id of the demo owner/student pair, or null when not in 둘러보기. */
export function getDemoSessionId(): string | null {
  return localStorage.getItem(DEMO_SESSION_KEY);
}

/** Leaves 둘러보기 but keeps the token (a Kakao login replaces a demo one). */
export function clearDemoSession(): void {
  localStorage.removeItem(DEMO_SESSION_KEY);
  notify();
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(DEMO_SESSION_KEY);
  notify();
}

export function isLoggedIn(): boolean {
  return getAccessToken() !== null;
}
