import { useEffect, useState } from "react";

/**
 * 목록 끝에 둔 빈 요소가 화면 가까이(200px) 오면 onReach 를 부른다. 돌려준 함수를 그 요소의 ref 로 쓴다.
 * enabled 나 onReach 가 바뀌면 다시 지켜봐서, 한 쪽을 불러온 뒤에도 끝이 보이면 또 부른다.
 */
export function useLoadMoreSentinel(
  enabled: boolean,
  onReach: () => void,
): (node: HTMLElement | null) => void {
  const [node, setNode] = useState<HTMLElement | null>(null);

  useEffect(() => {
    if (!node || !enabled) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) onReach();
      },
      { rootMargin: "200px 0px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [node, enabled, onReach]);

  return setNode;
}
