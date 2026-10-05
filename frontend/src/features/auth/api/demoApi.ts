import { apiFetch } from "../../../api/client";

/** 백엔드 DemoRole */
export type DemoRole = "OWNER" | "STUDENT";

/**
 * POST /demo/login. demoSessionId 가 없으면 새 데모 사장님 · 학생 한 쌍과 예시 데이터를 만들고,
 * 있으면 그 쌍의 요청한 역할로 바꾼다. 카카오 로그인처럼 refresh token 은 HTTP-only 쿠키로 오고,
 * 응답은 봉투 없이 { accessToken, demoSessionId } 다.
 */
export function requestDemoLogin(
  role: DemoRole,
  demoSessionId?: string,
): Promise<{ accessToken: string; demoSessionId: string }> {
  return apiFetch("/demo/login", {
    method: "POST",
    body: JSON.stringify({ role, demoSessionId }),
  });
}
