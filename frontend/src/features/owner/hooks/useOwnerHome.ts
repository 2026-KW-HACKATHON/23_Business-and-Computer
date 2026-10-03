import { SAMPLE_OWNER_HOME } from "../lib/sampleHome";
import type { OwnerHome } from "../types";

/**
 * 사장님 홈에 그릴 데이터. 지금은 임시 예시 데이터를 돌려준다.
 * 백엔드를 연동할 때 이 안만 API 호출로 바꾸면 화면은 그대로 쓴다.
 */
export function useOwnerHome(): OwnerHome {
  return SAMPLE_OWNER_HOME;
}
