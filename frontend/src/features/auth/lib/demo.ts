import { ApiError } from "../../../api/client";
import { clearTokens, getDemoSessionId, saveDemoLogin } from "../../../api/tokens";
import type { Role } from "../../../types/role";
import { requestDemoLogin } from "../api/demoApi";

/**
 * ok · limit = 한 시간에 만들 수 있는 데모 쌍을 다 씀 (DEMO_429) ·
 * expired = 이 데모 쌍이 서버에 없음 (지워졌거나 모르는 id) · failed = 그 밖의 실패
 */
export type DemoLoginResult = "ok" | "limit" | "expired" | "failed";

const DEMO_ROLE = { owner: "OWNER", student: "STUDENT" } as const;

function toResult(error: unknown): DemoLoginResult {
  if (!(error instanceof ApiError)) return "failed";
  if (error.status === 429) return "limit";
  if (error.status === 401 || error.status === 403 || error.status === 404) return "expired";
  return "failed";
}

async function login(role: Role, demoSessionId?: string): Promise<DemoLoginResult> {
  try {
    const answer = await requestDemoLogin(DEMO_ROLE[role], demoSessionId);
    saveDemoLogin(answer.accessToken, answer.demoSessionId);
    return "ok";
  } catch (error) {
    return toResult(error);
  }
}

/**
 * 「사장님으로 / 대학생으로 둘러보기」. 이미 둘러보던 쌍이 있으면 그 쌍으로 들어가고,
 * 그 쌍이 서버에 없으면 새 쌍을 만든다.
 */
export async function startDemo(role: Role): Promise<DemoLoginResult> {
  const current = getDemoSessionId();
  if (current) {
    const result = await login(role, current);
    if (result !== "expired") return result;
    clearTokens();
  }
  return login(role, undefined);
}

/** 홈 배지 「○○으로 보기 ⇄」: 같은 쌍의 다른 역할로 바꾼다. 쌍이 없으면 expired */
export async function switchDemoRole(role: Role): Promise<DemoLoginResult> {
  const current = getDemoSessionId();
  if (!current) return "expired";
  const result = await login(role, current);
  if (result === "expired") clearTokens();
  return result;
}
