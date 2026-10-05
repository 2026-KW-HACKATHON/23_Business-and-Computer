/** Public entry for the auth feature — import auth only from here. */
export { socialLoginUrl, exchangeCookieForAccessToken } from "./api/authApi";
export {
  saveAccessToken,
  getAccessToken,
  clearTokens,
  isLoggedIn,
} from "../../api/tokens";
export { getUserRole, landingPath } from "./lib/session";
export type { UserRole } from "./lib/session";
export type { SocialProvider } from "./types";
