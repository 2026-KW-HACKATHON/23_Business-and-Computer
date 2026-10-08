import { useLayoutEffect } from "react";

/**
 * 첫 안내가 뒤 화면 요소의 자리를 잰다 (ADR 0053). 뒤 화면이 그려지며 자리를 잡는 동안(처음 1초) 매 프레임
 * 다시 재고, 창 크기가 바뀌면 또 잰다. measure 는 바뀐 값만 저장하고, 렌더마다 새로 만들지 않는다 (useCallback)
 */
export function useGuideMeasure(measure: () => void): void {
  useLayoutEffect(() => {
    const until = performance.now() + 1000;
    let frame = 0;
    const tick = () => {
      measure();
      if (performance.now() < until) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    window.addEventListener("resize", measure);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", measure);
    };
  }, [measure]);
}
