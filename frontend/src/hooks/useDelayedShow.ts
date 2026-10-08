import { useEffect, useState } from "react";

/** 로딩 표시를 늦게 띄우는 시간. 이보다 빨리 끝나는 로딩은 아무것도 보이지 않아 깜빡이지 않는다 */
export const LOADING_DELAY_MS = 300;

/** 붙은 뒤 ms 가 지나면 true. 빨리 끝나 사라질 로딩 표시가 번쩍이지 않게 한다 */
export function useDelayedShow(ms: number = LOADING_DELAY_MS): boolean {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setShown(true), ms);
    return () => window.clearTimeout(timer);
  }, [ms]);
  return shown;
}
