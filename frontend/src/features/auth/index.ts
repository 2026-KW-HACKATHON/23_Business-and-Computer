/** Public entry for the auth feature — import auth only from here. */
export { socialLoginUrl, exchangeCookieForAccessToken } from "./api/authApi";
export {
  saveAccessToken,
  getAccessToken,
  clearTokens,
  clearDemoSession,
  isLoggedIn,
} from "../../api/tokens";
export { startDemo, switchDemoRole } from "./lib/demo";
export type { DemoLoginResult } from "./lib/demo";
export { useDemoRoleSwitch, useIsDemo } from "./hooks/useDemoSession";
export { getUserRole, landingPath } from "./lib/session";
export type { UserRole } from "./lib/session";
export type { SocialProvider } from "./types";
