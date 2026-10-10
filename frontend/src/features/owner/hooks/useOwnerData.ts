import { findRequestExample } from "../lib/taskFinder";
import type { RequestExample } from "../types";

/** 이 화면을 연 동안 후기를 남긴 작업. 끝난 목록을 다시 불러오기 전에도 「후기 작성 완료」로 보인다 */
const reviewedWorkIds = new Set<string>();

/** 후기를 남김 */
export function markOwnerWorkReviewed(workId: string): void {
  reviewedWorkIds.add(workId);
}

/** 이 화면을 연 동안 후기를 남겼는지 */
export function isOwnerWorkReviewed(workId: string): boolean {
  return reviewedWorkIds.has(workId);
}

/** 홈 「이런 의뢰는 어때요?」 · 맡길 일 찾기 예시 하나. 의뢰 등록을 이 내용으로 채워 시작한다 */
export function useRequestExample(exampleId: string | undefined): RequestExample | undefined {
  return findRequestExample(exampleId);
}
