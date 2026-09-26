import type { ReactNode } from "react";
import "./AppBar.css";

interface AppBarProps {
  title?: string;
  /** 넣으면 왼쪽에 ← 버튼이 생긴다 */
  onBack?: () => void;
  /** 오른쪽 영역 (예: SKIP) */
  right?: ReactNode;
  /** 회색 배경 (회원가입 화면) */
  muted?: boolean;
  /** 앱바 아래쪽에 붙는 요소 (예: StepIndicator) */
  bottom?: ReactNode;
}

function AppBar({ title, onBack, right, muted = false, bottom }: AppBarProps) {
  return (
    <header className={`app-bar${muted ? " app-bar--muted" : ""}`}>
      <div className="app-bar__row">
        {onBack && (
          <button type="button" className="app-bar__back" onClick={onBack} aria-label="뒤로 가기">
            ←
          </button>
        )}
        {title && <h1 className="app-bar__title">{title}</h1>}
        {right && <div className="app-bar__right">{right}</div>}
      </div>
      {bottom && <div className="app-bar__bottom">{bottom}</div>}
    </header>
  );
}

export default AppBar;
