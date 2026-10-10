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
export { default as DemoSessionStrip } from "./components/DemoSessionStrip";
export { default as RoleRouteGuard } from "./components/RoleRouteGuard";
export { default as RoleColorScope } from "./components/RoleColorScope";
export { getUserRole, landingPath, logOut } from "./lib/session";
export type { UserRole } from "./lib/session";
export type { SocialProvider } from "./types";
