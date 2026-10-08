import { useEffect, useId, useRef } from "react";
import type { PointerEvent, ReactNode } from "react";
import { createPortal } from "react-dom";
import { usePresence } from "../../hooks/usePresence";
import Frozen from "../Frozen/Frozen";
import "./BottomSheet.css";

/** 내려가며 닫히는 시간(ms). BottomSheet.css 의 나가는 전환과 같다 */
const EXIT_MS = 240;
/** 시트 높이의 이만큼 끌어내리거나 이보다 빠르게(px/ms) 튕겨 내리면 닫는다 */
const CLOSE_RATIO = 0.25;
const CLOSE_SPEED = 0.6;
/** 이만큼 움직여야 끌기로 본다. 그 전에는 위쪽에 있는 버튼을 그대로 누를 수 있다 */
const DRAG_SLOP = 6;

interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  /** 제목 자리에 다른 모양이 필요할 때 (예: 학생 사진 + 이름) */
  header?: ReactNode;
  /** 시트 맨 아래 고정 영역 (예: 확인 버튼) */
  footer?: ReactNode;
  children: ReactNode;
}

/**
 * 아래에서 올라오는 시트. 닫을 때(바깥 · Esc · 버튼)는 내려가며 사라지고,
 * 손잡이 · 제목 쪽을 끌어내리면 손가락을 따라 내려오다가 놓으면 닫히거나 제자리로 돌아간다
 */
function BottomSheet({ open, onClose, title, description, header, footer, children }: BottomSheetProps) {
  const titleId = useId();
  const { mounted, exiting } = usePresence(open, EXIT_MS);
  const overlayRef = useRef<HTMLDivElement>(null);
  const sheetRef = useRef<HTMLElement>(null);
  // 끌어내려 닫았는데 부모가 닫지 않으면 제자리로 돌려놓을 때 쓴다
  const openRef = useRef(open);

  useEffect(() => {
    openRef.current = open;
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = prevOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, onClose]);

  if (!mounted) return null;

  /** 끈 만큼 시트를 내리고 뒤를 그만큼 밝힌다. null 이면 제자리로 */
  const setOffset = (dy: number | null) => {
    const sheet = sheetRef.current;
    const overlay = overlayRef.current;
    if (!sheet || !overlay) return;
    if (dy === null) {
      sheet.style.removeProperty("transform");
      overlay.style.removeProperty("--sheet-dim");
      return;
    }
    sheet.style.transform = `translateY(${dy}px)`;
    overlay.style.setProperty("--sheet-dim", String(1 - Math.min(1, dy / sheet.offsetHeight)));
  };

  /**
   * 손잡이 · 제목 쪽을 누르면 그 뒤 움직임은 화면 어디서든 따라간다 (빠르게 튕겨 내려도 놓치지 않게).
   * DRAG_SLOP 만큼 내려가기 전에는 끌기가 아니어서 위쪽 버튼을 그대로 누를 수 있다
   */
  const handlePointerDown = (e: PointerEvent<HTMLDivElement>) => {
    const sheet = sheetRef.current;
    if (!open || !sheet || (e.pointerType === "mouse" && e.button !== 0)) return;
    const { pointerId } = e;
    const startY = e.clientY;
    let lastY = startY;
    let lastTime = e.timeStamp;
    // 마지막 움직임의 속도 (px/ms, 아래가 +)
    let speed = 0;
    let dragging = false;

    function stop() {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", release);
      window.removeEventListener("pointercancel", release);
    }

    function move(ev: globalThis.PointerEvent) {
      if (ev.pointerId !== pointerId || !sheet) return;
      const dy = ev.clientY - startY;
      if (!dragging) {
        // 위로 밀면 끌기가 아니다
        if (dy < -DRAG_SLOP) stop();
        if (dy < DRAG_SLOP) return;
        dragging = true;
        sheet.classList.add("bottom-sheet--dragging");
      }
      if (ev.timeStamp > lastTime) speed = (ev.clientY - lastY) / (ev.timeStamp - lastTime);
      lastY = ev.clientY;
      lastTime = ev.timeStamp;
      setOffset(Math.max(0, dy));
    }

    function release(ev: globalThis.PointerEvent) {
      if (ev.pointerId !== pointerId || !sheet) return;
      stop();
      if (!dragging) return;
      sheet.classList.remove("bottom-sheet--dragging");
      // 손을 뗀 자리가 바깥이어도 그 클릭으로 한 번 더 닫히지 않게
      const swallow = (click: MouseEvent) => click.stopPropagation();
      window.addEventListener("click", swallow, { capture: true, once: true });
      window.setTimeout(() => window.removeEventListener("click", swallow, { capture: true }));
      const dy = Math.max(0, ev.clientY - startY);
      // 잠깐 멈췄다 놓으면 튕긴 것으로 보지 않는다
      const flung = ev.timeStamp - lastTime < 100 && speed > CLOSE_SPEED;
      if (ev.type === "pointerup" && (dy > sheet.offsetHeight * CLOSE_RATIO || flung)) {
        // 끌던 자리에서 끝까지 내려가며 닫힌다
        sheet.style.transform = "translateY(100%)";
        overlayRef.current?.style.setProperty("--sheet-dim", "0");
        onClose();
        window.setTimeout(() => {
          if (openRef.current) setOffset(null);
        }, EXIT_MS);
      } else {
        setOffset(null);
      }
    }

    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", release);
    window.addEventListener("pointercancel", release);
  };

  return createPortal(
    <div
      ref={overlayRef}
      className={`bottom-sheet__overlay${exiting ? " bottom-sheet__overlay--exit" : ""}`}
      onClick={exiting ? undefined : onClose}
    >
      <section
        ref={sheetRef}
        className={`bottom-sheet${exiting ? " bottom-sheet--exit" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        onClick={(e) => e.stopPropagation()}
      >
        <Frozen live={open}>
          <div className="bottom-sheet__grab" onPointerDown={handlePointerDown}>
            <div className="bottom-sheet__handle" aria-hidden="true" />
            {header}
            {title && (
              <div className="bottom-sheet__head">
                <h2 id={titleId} className="bottom-sheet__title">
                  {title}
                </h2>
                {description && <p className="bottom-sheet__description">{description}</p>}
              </div>
            )}
          </div>
          <div className="bottom-sheet__body">{children}</div>
          {footer && <div className="bottom-sheet__footer">{footer}</div>}
        </Frozen>
      </section>
    </div>,
    document.body,
  );
}

export default BottomSheet;
