import type { ReactNode } from "react";
import { usePushed } from "../../hooks/usePushed";
import AppBar from "../AppBar/AppBar";
import "./SubScreen.css";

interface SubScreenProps {
  title: ReactNode;
  onBack: () => void;
  /** 앱바 오른쪽 (예: 모두 읽음) */
  right?: ReactNode;
  /** 회색 앱바. 기본 true */
  muted?: boolean;
  /** 화면 아래에 고정되는 영역 (예: 주요 버튼) */
  footer?: ReactNode;
  children: ReactNode;
}

/**
 * 메인 탭이 아닌 화면 틀. 「← 제목」 앱바는 고정이고 본문만 스크롤된다.
 * 앞으로 들어온 화면은 오른쪽에서 밀려 들어온다 (ADR 0060)
 */
function SubScreen({ title, onBack, right, muted = true, footer, children }: SubScreenProps) {
  const pushed = usePushed();
  return (
    <div className={`sub-screen${pushed ? " screen-pushed" : ""}`}>
      <AppBar title={title} onBack={onBack} right={right} muted={muted} />
      <main className="sub-screen__body">{children}</main>
      {footer && <footer className="sub-screen__footer">{footer}</footer>}
    </div>
  );
}

export default SubScreen;
