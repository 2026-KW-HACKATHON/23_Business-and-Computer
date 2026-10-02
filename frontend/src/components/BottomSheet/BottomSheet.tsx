import { useEffect, useId } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import "./BottomSheet.css";

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

function BottomSheet({ open, onClose, title, description, header, footer, children }: BottomSheetProps) {
  const titleId = useId();

  useEffect(() => {
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

  if (!open) return null;

  return createPortal(
    <div className="bottom-sheet__overlay" onClick={onClose}>
      <section
        className="bottom-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        onClick={(e) => e.stopPropagation()}
      >
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
        <div className="bottom-sheet__body">{children}</div>
        {footer && <div className="bottom-sheet__footer">{footer}</div>}
      </section>
    </div>,
    document.body,
  );
}

export default BottomSheet;
