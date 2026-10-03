import { useRef } from "react";
import type { DragEvent, MouseEvent, PointerEvent } from "react";

/** 이만큼 넘게 움직여야 끌기로 본다 (그 아래는 누르기) */
const DRAG_THRESHOLD_PX = 4;

/**
 * 스크롤바를 숨긴 가로 목록(분야 · 업종 칩 · 예시 카드)을 마우스로 끌어 넘긴다.
 * 터치는 브라우저가 원래 밀어 넘기므로 마우스만 다룬다.
 * 반환값을 목록 요소에 그대로 펼쳐 붙인다: <div {...dragScroll}>
 */
export function useDragScroll<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const drag = useRef({ active: false, moved: false, startX: 0, startLeft: 0, pointerId: 0 });

  const onPointerDown = (e: PointerEvent<T>) => {
    if (e.pointerType !== "mouse" || e.button !== 0 || !ref.current) return;
    drag.current = {
      active: true,
      moved: false,
      startX: e.clientX,
      startLeft: ref.current.scrollLeft,
      pointerId: e.pointerId,
    };
  };

  const onPointerMove = (e: PointerEvent<T>) => {
    const d = drag.current;
    const el = ref.current;
    if (!d.active || !el) return;
    const dx = e.clientX - d.startX;
    if (!d.moved && Math.abs(dx) > DRAG_THRESHOLD_PX) {
      d.moved = true;
      // 목록 밖으로 나가도 계속 끌리게 포인터를 붙잡는다
      el.setPointerCapture(d.pointerId);
    }
    if (d.moved) el.scrollLeft = d.startLeft - dx;
  };

  const end = () => {
    drag.current.active = false;
  };

  // 끈 뒤 손을 뗀 자리의 버튼이 눌리지 않게 막는다
  const onClickCapture = (e: MouseEvent<T>) => {
    if (!drag.current.moved) return;
    drag.current.moved = false;
    e.preventDefault();
    e.stopPropagation();
  };

  // 칩 안 그림이 따로 끌려 나오지 않게
  const onDragStart = (e: DragEvent<T>) => e.preventDefault();

  return {
    ref,
    onPointerDown,
    onPointerMove,
    onPointerUp: end,
    onPointerCancel: end,
    onClickCapture,
    onDragStart,
  };
}
