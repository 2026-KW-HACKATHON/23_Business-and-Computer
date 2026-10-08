import { useEffect, useState } from "react";

/**
 * 열린 것을 닫을 때 바로 지우지 않고 exitMs 동안 「나가는 중」으로 그려 둔다 (닫히는 애니메이션).
 * 동작 줄이기 설정이면 바로 지운다.
 * @returns mounted = 그릴지, exiting = 나가는 중인지
 */
export function usePresence(open: boolean, exitMs: number): { mounted: boolean; exiting: boolean } {
  const [prevOpen, setPrevOpen] = useState(open);
  const [exiting, setExiting] = useState(false);
  // 닫히는 순간은 그리는 동안 이전 값과 비교해 알아챈다. 그래야 한 번도 사라지지 않고 바로 나가기 시작한다
  if (open !== prevOpen) {
    setPrevOpen(open);
    setExiting(!open);
  }
  useEffect(() => {
    if (!exiting) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const timer = window.setTimeout(() => setExiting(false), reduce ? 0 : exitMs);
    return () => window.clearTimeout(timer);
  }, [exiting, exitMs]);
  return { mounted: open || exiting, exiting: exiting && !open };
}
