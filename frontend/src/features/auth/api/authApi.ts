import { apiFetch, BACKEND_API_BASE_URL } from "../../../api/client";
import type { SocialProvider } from "../types";

/**
 * Full backend URL that starts the OAuth2 login flow for a provider. The page
 * navigates the browser here (full redirect), so it is a URL string, not a
 * fetch call. `redirect_origin` tells the backend which frontend (deployed site
 * or local dev server) to send the browser back to after login.
 */
export function socialLoginUrl(provider: SocialProvider): string {
  const origin = encodeURIComponent(window.location.origin);
  return `${BACKEND_API_BASE_URL}/oauth2/authorization/${provider}?redirect_origin=${origin}`;
}

/**
 * After a social login the backend sets an HTTP-only refresh-token cookie and
 * redirects to the cookie page. This exchanges that cookie for an access token
 * in the response body (raw `{accessToken}`, no envelope) and rotates the
 * cookie. The refresh token stays in the cookie.
 */
export async function exchangeCookieForAccessToken(): Promise<string> {
  const { accessToken } = await apiFetch<{ accessToken: string }>("/jwt/exchange", {
    method: "POST",
  });
  return accessToken;
}
