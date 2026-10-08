import { useCallback, useRef, useState } from "react";
import { useGuideMeasure } from "../../hooks/useGuideMeasure";
import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import GuideOverlay from "../GuideOverlay/GuideOverlay";
import "./DemoGuide.css";

interface DemoGuideProps {
  /** 지금 둘러보는 역할 */
  tone: Role;
  onClose: () => void;
}

interface Box {
  top: number;
  left: number;
  height: number;
}

/**
 * 피그마 「사장님 홈 · 학생 홈 - 둘러보기 첫 안내」 (ADR 0053). 흐린 덮개 위에 역할 전환 뱃지만 밝게 다시 그리고,
 * 가운데 반투명 카드(「둘러보기 모드예요」, 요약, 세 항목) → 뱃지 아래 말풍선 → 반짝이는 「알겠어요」 차례로 보인다
 */
function DemoGuide({ tone, onClose }: DemoGuideProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [badge, setBadge] = useState<Box>();

  // 앱바의 역할 전환 뱃지 자리를 재서 그 위에 겹친다 (뱃지 · 둘러보기 띠는 조금 늦게 자리를 잡는다)
  const measure = useCallback(() => {
    const root = rootRef.current?.getBoundingClientRect();
    const el = document.querySelector('[data-guide="demo-badge"]')?.getBoundingClientRect();
    if (!root || !el) return;
    const next = { top: el.top - root.top, left: el.left - root.left, height: el.height };
    setBadge((prev) =>
      prev && prev.top === next.top && prev.left === next.left && prev.height === next.height ? prev : next,
    );
  }, []);
  useGuideMeasure(measure);

  const other = tone === "owner" ? "학생" : "사장님";

  return (
    <GuideOverlay tone={tone} label="둘러보기 첫 안내" blur okDelay={1.5} rootRef={rootRef} onClose={onClose}>
      {badge && (
        <>
          <span className="demo-guide__badge demo-role-badge" style={{ top: badge.top, left: badge.left }} aria-hidden="true">
            <span className="demo-role-badge__dot" />
            <span className="demo-role-badge__switch">{other}으로 보기 ⇄</span>
          </span>
          <p className="demo-guide__bubble" style={{ top: badge.top + badge.height + 10, left: badge.left }}>
            누르면 {other} 화면으로 바뀌어요
          </p>
        </>
      )}
      <div className="demo-guide__place">
        <section className="demo-guide__card">
          <div className="demo-guide__head">
            <h2 className="demo-guide__title">둘러보기 모드예요</h2>
            <p className="demo-guide__summary">
              <strong>서버까지 구현한 실제 서비스 기능</strong>이에요
              <br />
              체험할 수 있게 예시 데이터를 넣어 놨어요
            </p>
          </div>
          <div className="demo-guide__divider" />
          <ul className="demo-guide__items">
            <li className="demo-guide__item">
              <span className="demo-guide__icon">
                <AppImage name="roleOwner" width={30} alt="" />
              </span>
              <span className="demo-guide__item-text">
                <strong>사장님 화면</strong>
                <small>사업자등록번호 없이 볼 수 있어요</small>
              </span>
            </li>
            <li className="demo-guide__item">
              <span className="demo-guide__icon">
                <AppImage name="roleStudent" width={30} alt="" />
              </span>
              <span className="demo-guide__item-text">
                <strong>학생 화면</strong>
                <small>학교 메일 인증 없이 볼 수 있어요</small>
              </span>
            </li>
            <li className="demo-guide__item">
              <span className="demo-guide__icon demo-guide__icon--chat">
                <AppImage name="iconTabChat" width={20} alt="" />
              </span>
              <span className="demo-guide__item-text">
                <strong>다른 회원과 주고받으려면</strong>
                <small>카카오로 로그인해 주세요</small>
              </span>
            </li>
          </ul>
        </section>
      </div>
    </GuideOverlay>
  );
}

export default DemoGuide;
