import { useEffect } from "react";
import type { CSSProperties, ReactNode, RefObject } from "react";
import { createPortal } from "react-dom";
import type { Role } from "../../types/role";
import Button from "../Button/Button";
import "./GuideOverlay.css";

interface GuideOverlayProps {
  tone: Role;
  /** 화면 읽기 프로그램이 읽는 이름 (「가입 후 첫 안내」) */
  label: string;
  /** 덮개를 흐리게도 한다 (둘러보기 첫 안내) */
  blur?: boolean;
  /** 「알겠어요」가 올라오는 때(초). 그 뒤로 뒤의 빛이 퍼지기를 되풀이한다 */
  okDelay: number;
  /** 밝게 다시 그릴 곳을 잴 때 기준이 되는 덮개 (앱 화면 폭) */
  rootRef?: RefObject<HTMLDivElement | null>;
  onClose: () => void;
  /** 밝게 다시 그린 것 · 말풍선 · 카드. 덮개 안에 절대 위치로 놓는다 */
  children: ReactNode;
}

/**
 * 첫 안내 덮개 (ADR 0053). 앱 화면 폭 안을 서서히 어둡게 덮고, 오른쪽 위 닫기(✕)와 아래 반짝이는 「알겠어요」를
 * 둔다. 알겠어요 · ✕ · 아무 곳이나 누르기 · Esc 로 닫는다
 */
function GuideOverlay({ tone, label, blur = false, okDelay, rootRef, onClose, children }: GuideOverlayProps) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return createPortal(
    <div
      ref={rootRef}
      className={`guide-overlay guide-overlay--${tone}${blur ? " guide-overlay--blur" : ""}`}
      style={{ "--guide-ok-delay": `${okDelay}s` } as CSSProperties}
      role="dialog"
      aria-modal="true"
      aria-label={label}
      onClick={onClose}
    >
      <div className="guide-overlay__dim" aria-hidden="true" />
      {children}
      <button type="button" className="guide-overlay__close" aria-label="닫기" onClick={onClose}>
        <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M3 3l10 10M13 3L3 13" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
        </svg>
      </button>
      <div className="guide-overlay__ok">
        <span className="guide-overlay__glow" aria-hidden="true" />
        <Button tone={tone} fullWidth onClick={onClose}>
          알겠어요
        </Button>
      </div>
    </div>,
    document.body,
  );
}

export default GuideOverlay;
