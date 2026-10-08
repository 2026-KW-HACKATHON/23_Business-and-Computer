import { useCallback, useRef, useState } from "react";
import type { CSSProperties, ReactNode, RefObject } from "react";
import { useGuideMeasure } from "../../hooks/useGuideMeasure";
import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import GuideOverlay from "../GuideOverlay/GuideOverlay";
import { placeGuide } from "./signupGuideLayout";
import type { Box, GuideMeasure, TipSpot } from "./signupGuideLayout";
import "./SignupGuide.css";

interface SignupGuideProps {
  tone: Role;
  /** 밝게 다시 그릴 확인할 일 카드. 안내에서는 눌러도 동작하지 않는다 */
  card: ReactNode;
  /** 화면에 있는 그 카드. 이 자리에 겹쳐 그린다 */
  cardRef: RefObject<HTMLElement | null>;
  /** 말풍선 ① 확인할 일 카드 · ② 새 의뢰 · 새 제안 버튼 · ③ 알림 종 */
  tips: readonly [string, string, string];
  onClose: () => void;
}

const boxOf = (el: Element | null | undefined, root: DOMRect): Box | undefined => {
  if (!el) return undefined;
  const r = el.getBoundingClientRect();
  return { top: r.top - root.top, left: r.left - root.left, width: r.width, height: r.height };
};

// 움직임(transform)과 상관없이 폭을 잰다
const widthOf = (el: Element | null | undefined) => (el instanceof HTMLElement ? el.offsetWidth : 0);

function TipBody({ n, text }: { n: number; text: string }) {
  return (
    <>
      <span className="signup-guide__tip-number" aria-hidden="true">
        {n}
      </span>
      <span className="signup-guide__tip-text">{text}</span>
    </>
  );
}

/** 고른 자리에 그린다. 폭이 정해져 있으면 그 폭으로 줄을 바꾼다 */
function Tip({ n, text, spot }: { n: number; text: string; spot?: TipSpot }) {
  if (!spot) return null;
  const style: CSSProperties = { top: spot.top, left: spot.left, width: spot.width };
  return (
    <p className={`signup-guide__tip signup-guide__tip--${n}${spot.width ? " signup-guide__tip--wrap" : ""}`} style={style}>
      <TipBody n={n} text={text} />
    </p>
  );
}

/**
 * 피그마 「사장님 홈 · 학생 홈 - 가입 후 첫 안내」 (ADR 0053). 첫 안내 덮개(`GuideOverlay`) 위에 확인할 일 카드 ·
 * 알림 종 · 새 의뢰(새 제안) 버튼만 밝게 다시 그린 뒤, 카드 위에 환영 문구, 말풍선 ①②③을 0.5초 간격으로 올리고
 * 2초에 반짝이는 「알겠어요」를 보인다. 말풍선 자리는 화면 크기에 맞춰 서로 겹치지 않게 고른다 (`placeGuide`)
 */
function SignupGuide({ tone, card, cardRef, tips, onClose }: SignupGuideProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [measured, setMeasured] = useState<GuideMeasure>();

  // 화면의 카드 · 종 · 플로팅 버튼 자리와 안내 글 크기를 재서 그 위에 겹친다
  const measure = useCallback(() => {
    const rootEl = rootRef.current;
    const root = rootEl?.getBoundingClientRect();
    if (!rootEl || !root) return;
    const welcome = rootEl.querySelector(".signup-guide__welcome-text");
    const next: GuideMeasure = {
      width: root.width,
      height: root.height,
      card: boxOf(cardRef.current, root),
      bell: boxOf(document.querySelector('[data-guide="bell"]'), root),
      fab: boxOf(document.querySelector(".main-tab-screen__fab"), root),
      close: boxOf(rootEl.querySelector(".guide-overlay__close"), root),
      ok: boxOf(rootEl.querySelector(".guide-overlay__ok"), root),
      welcome: {
        width: widthOf(welcome),
        height: welcome instanceof HTMLElement ? welcome.offsetHeight : 0,
      },
      tips: [1, 2, 3].map((n) => widthOf(rootEl.querySelector(`.signup-guide__measure-tip--${n}`))) as [
        number,
        number,
        number,
      ],
    };
    setMeasured((prev) => (JSON.stringify(prev) === JSON.stringify(next) ? prev : next));
  }, [cardRef]);
  useGuideMeasure(measure);

  const fabLabel = tone === "owner" ? "새 의뢰" : "새 제안";
  const layout = measured ? placeGuide(measured) : {};
  const bellCenter = measured?.bell && {
    x: measured.bell.left + measured.bell.width / 2,
    y: measured.bell.top + measured.bell.height / 2,
  };

  return (
    <GuideOverlay tone={tone} label="가입 후 첫 안내" okDelay={2} rootRef={rootRef} onClose={onClose}>
      {measured?.card && (
        <>
          <p className="signup-guide__welcome" style={{ top: layout.welcomeTop }}>
            <span className="signup-guide__welcome-text">골목인턴에 오신 걸 환영해요</span>
          </p>
          <div
            className="signup-guide__card"
            style={{ top: measured.card.top, left: measured.card.left, width: measured.card.width }}
            aria-hidden="true"
          >
            {card}
          </div>
        </>
      )}
      {measured?.fab && (
        // 실제 버튼에서 잰 크기 그대로 그린다 (크기를 맡기면 사파리가 글자 칸을 0으로 잡는다)
        <span
          className={`signup-guide__fab fab fab--${tone}`}
          style={{
            top: measured.fab.top,
            left: measured.fab.left,
            width: measured.fab.width,
            height: measured.fab.height,
          }}
          aria-hidden="true"
        >
          <svg className="fab__plus" viewBox="0 0 20 20" fill="none">
            <path d="M10 4v12M4 10h12" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
          </svg>
          <span className="fab__label">
            <span>{fabLabel}</span>
          </span>
        </span>
      )}
      {bellCenter && (
        <span className="signup-guide__bell" style={{ top: bellCenter.y - 22, left: bellCenter.x - 22 }} aria-hidden="true">
          <AppImage name="iconBell" alt="" />
        </span>
      )}
      {/* 말풍선을 한 줄로 그렸을 때의 폭을 재는 보이지 않는 사본 */}
      <div className="signup-guide__measure" aria-hidden="true">
        {tips.map((text, i) => (
          <p key={text} className={`signup-guide__tip signup-guide__measure-tip--${i + 1}`}>
            <TipBody n={i + 1} text={text} />
          </p>
        ))}
      </div>
      {measured?.card && <Tip n={1} text={tips[0]} spot={layout.tip1} />}
      {measured?.fab && <Tip n={2} text={tips[1]} spot={layout.tip2} />}
      {measured?.bell && <Tip n={3} text={tips[2]} spot={layout.tip3} />}
    </GuideOverlay>
  );
}

export default SignupGuide;
