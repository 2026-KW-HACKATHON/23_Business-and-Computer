import type { Role } from "../types/role";

/**
 * 가입 후 첫 안내를 아직 보지 않은 역할 (ADR 0053). 서버 값 없이 이 기기 저장소에만 두어, 다른 기기에서는
 * 한 번 더 보일 수 있다
 */
const KEY = "signupGuide";

/** 가입 저장(POST /auth/owner · /auth/student)이 끝나면 부른다. 그 역할의 홈에 처음 들어올 때 한 번 안내한다 */
export function markSignupGuide(role: Role): void {
  try {
    localStorage.setItem(KEY, role);
  } catch {
    // 저장소를 못 쓰면 안내 없이 간다
  }
}

export function pendingSignupGuide(role: Role): boolean {
  try {
    return localStorage.getItem(KEY) === role;
  } catch {
    return false;
  }
}

/** 알겠어요 · 닫기 · 바깥 누르기로 닫으면 다시 띄우지 않는다 */
export function clearSignupGuide(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // 저장소를 못 쓰면 지울 것도 없다
  }
}
