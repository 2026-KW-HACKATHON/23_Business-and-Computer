import { apiData, apiFetch } from "../../../api/client";

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

/**
 * 데모 로그인 직후 들어갈 홈(GET /me/home)을 바로 받기 시작한다. 홈 화면은 같은 요청을 이어받거나
 * 기억해 둔 답을 쓴다 (ADR 0077 탭 목록 캐시, ADR 0081). 실패는 홈 화면이 다시 부를 때 다룬다.
 */
export function prefetchDemoHome(): void {
  void apiData("/me/home").catch(() => undefined);
}
