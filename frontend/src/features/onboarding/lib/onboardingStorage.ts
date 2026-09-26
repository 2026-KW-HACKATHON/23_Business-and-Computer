/**
 * 온보딩을 본 적 있는지 기억한다. 첫 실행에만 온보딩을 보여 주기 위해 쓴다.
 * 저장소를 못 쓰는 환경(사생활 보호 모드 등)에서는 매번 처음 실행으로 본다.
 */
const ONBOARDING_SEEN_KEY = "onboardingSeen";

export function hasSeenOnboarding(): boolean {
  try {
    return localStorage.getItem(ONBOARDING_SEEN_KEY) === "true";
  } catch {
    return false;
  }
}

export function markOnboardingSeen(): void {
  try {
    localStorage.setItem(ONBOARDING_SEEN_KEY, "true");
  } catch {
    // 저장하지 못해도 로그인으로 넘어가는 흐름은 그대로 둔다.
  }
}
