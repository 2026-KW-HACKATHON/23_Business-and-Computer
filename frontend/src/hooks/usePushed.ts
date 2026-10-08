import { useNavigationType } from "react-router-dom";

/**
 * 앞으로 들어온 화면인지 (ADR 0060). 그렇다면 틀에 `screen-pushed` 를 붙여 오른쪽에서 밀려 들어오게 한다.
 * 뒤로 가기 · 바꿔 끼우기(완료 뒤 이동 등) · 새로고침은 false 라 바로 보인다
 */
export function usePushed(): boolean {
  return useNavigationType() === "PUSH";
}
