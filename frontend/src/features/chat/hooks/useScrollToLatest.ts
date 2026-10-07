import { useCallback, useEffect, useRef } from "react";
import type { ChatMessage } from "../types";

/** 맨 아래에서 이만큼 안이면 맨 아래 근처로 본다 */
const NEAR_BOTTOM_PX = 80;

/** 스크롤되는 가장 가까운 조상. 없으면 문서 */
function scrollParentOf(node: HTMLElement): HTMLElement | null {
  for (let parent = node.parentElement; parent; parent = parent.parentElement) {
    const { overflowY } = getComputedStyle(parent);
    if (overflowY === "auto" || overflowY === "scroll") return parent;
  }
  return document.scrollingElement as HTMLElement | null;
}

/**
 * 채팅방 맨 아래로 내리기. 메시지가 늘 때, 맨 아래 근처에 있었거나 방금 내가 보낸 글이면 내리고,
 * 위로 올려 예전 대화를 읽는 중이면 그대로 둔다. 처음 불러온 대화도 맨 아래부터 보인다.
 * 돌려준 ref 는 메시지 목록 끝에 두는 빈 요소에 단다.
 */
export function useScrollToLatest(messages: ChatMessage[]): (node: HTMLDivElement | null) => () => void {
  const endRef = useRef<HTMLDivElement | null>(null);
  const nearBottom = useRef(true);

  const attachEnd = useCallback((node: HTMLDivElement | null) => {
    endRef.current = node;
    const scroller = node && scrollParentOf(node);
    if (!scroller) return () => undefined;
    const onScroll = () => {
      nearBottom.current =
        scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight <= NEAR_BOTTOM_PX;
    };
    scroller.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      scroller.removeEventListener("scroll", onScroll);
      endRef.current = null;
    };
  }, []);

  const count = messages.length;
  const last = count > 0 ? messages[count - 1] : undefined;
  const justSent = last?.mine === true && last.status === "sending";
  useEffect(() => {
    if (count === 0) return;
    if (nearBottom.current || justSent) endRef.current?.scrollIntoView({ block: "end" });
  }, [count, justSent]);

  return attachEnd;
}
