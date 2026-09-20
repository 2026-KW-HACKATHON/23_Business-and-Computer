import { apiFetch, BACKEND_API_BASE_URL } from "../../../api/client";
import type { SocialProvider, TokenPair } from "../types";

/**
 * Full backend URL that starts the OAuth2 login flow for a provider. The page
 * navigates the browser here (full redirect), so it is a URL string, not a
 * fetch call.
 */
export function socialLoginUrl(provider: SocialProvider): string {
  return `${BACKEND_API_BASE_URL}/oauth2/authorization/${provider}`;
}

/**
 * After a social login the backend sets an HTTP-only JWT cookie and redirects
 * to the cookie page. This exchanges that cookie for a JWT pair in the response
 * body (same shape the normal login returns), which we can then store.
 */
export function exchangeCookieForTokens(): Promise<TokenPair> {
  return apiFetch<TokenPair>("/jwt/exchange", { method: "POST" });
}
