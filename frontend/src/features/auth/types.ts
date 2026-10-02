/** Social login providers wired on the backend (Spring Security OAuth2). */
export type SocialProvider = "kakao" | "google" | "naver";

/** JWT pair the backend returns from the cookie→body exchange. */
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}
