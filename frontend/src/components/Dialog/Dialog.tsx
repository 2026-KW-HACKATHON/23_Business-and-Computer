import { useEffect, useId } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { usePresence } from "../../hooks/usePresence";
import AppImage from "../AppImage/AppImage";
import type { ImageName } from "../AppImage/images";
import Frozen from "../Frozen/Frozen";
import "./Dialog.css";

interface DialogProps {
  open: boolean;
  /** 제목 위 일러스트 (완료 · 실패 · 경고) */
  image?: ImageName;
  title: string;
  /** 줄바꿈(\n)은 그대로 보인다 */
  description?: ReactNode;
  /** 버튼 묶음. 세로로 쌓인다 */
  actions: ReactNode;
  /** 넣으면 배경을 누르거나 Esc 로 닫힌다 */
  onClose?: () => void;
  /** 설명 아래 더 넣을 내용 */
  children?: ReactNode;
}

/** 작아지며 사라지는 시간(ms). Dialog.css 의 나가는 전환과 같다 */
const EXIT_MS = 160;

/** 화면 가운데 알림 팝업 (결제 완료 · 취소 확인 등). 뒤 배경은 검정 45%. 살짝 커지며 나타나고 작아지며 사라진다 */
function Dialog({ open, image, title, description, actions, onClose, children }: DialogProps) {
  const titleId = useId();
  const { mounted, exiting } = usePresence(open, EXIT_MS);

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose?.();
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

  return createPortal(
    <div className={`dialog__overlay${exiting ? " dialog__overlay--exit" : ""}`} onClick={exiting ? undefined : onClose}>
      <section
        className={`dialog${exiting ? " dialog--exit" : ""}`}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <Frozen live={open}>
          {image && <AppImage name={image} width={96} priority />}
          <h2 id={titleId} className="dialog__title">
            {title}
          </h2>
          {description && <div className="dialog__description">{description}</div>}
          {children}
          <div className="dialog__actions">{actions}</div>
        </Frozen>
      </section>
    </div>,
    document.body,
  );
}

export default Dialog;
