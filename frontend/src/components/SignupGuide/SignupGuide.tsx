import { useCallback, useRef, useState } from "react";
import type { CSSProperties, ReactNode, RefObject } from "react";
import { useGuideMeasure } from "../../hooks/useGuideMeasure";
import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import GuideOverlay from "../GuideOverlay/GuideOverlay";
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

interface Box {
  top: number;
  left: number;
  width: number;
  height: number;
}

interface Boxes {
  width: number;
  card?: Box;
  bell?: Box;
  fab?: Box;
}

const boxOf = (el: Element | null, root: DOMRect): Box | undefined => {
  if (!el) return undefined;
  const r = el.getBoundingClientRect();
  return { top: r.top - root.top, left: r.left - root.left, width: r.width, height: r.height };
};

function Tip({ n, text, style }: { n: number; text: string; style: CSSProperties }) {
  return (
    <p className={`signup-guide__tip signup-guide__tip--${n}`} style={style}>
      <span className="signup-guide__tip-number" aria-hidden="true">
        {n}
      </span>
      {text}
    </p>
  );
}

/**
 * 피그마 「사장님 홈 · 학생 홈 - 가입 후 첫 안내」 (ADR 0053). 첫 안내 덮개(`GuideOverlay`) 위에 확인할 일 카드 ·
 * 알림 종 · 새 의뢰(새 제안) 버튼만 밝게 다시 그린 뒤, 카드 위에 환영 문구, 말풍선 ①②③을 0.5초 간격으로 올리고
 * 2초에 반짝이는 「알겠어요」를 보인다
 */
function SignupGuide({ tone, card, cardRef, tips, onClose }: SignupGuideProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [boxes, setBoxes] = useState<Boxes>();

  // 화면의 카드 · 종 · 플로팅 버튼 자리를 재서 그 위에 겹친다
  const measure = useCallback(() => {
    const root = rootRef.current?.getBoundingClientRect();
    if (!root) return;
    const next: Boxes = {
      width: root.width,
      card: boxOf(cardRef.current, root),
      bell: boxOf(document.querySelector('[data-guide="bell"]'), root),
      fab: boxOf(document.querySelector(".main-tab-screen__fab"), root),
    };
    setBoxes((prev) => (JSON.stringify(prev) === JSON.stringify(next) ? prev : next));
  }, [cardRef]);
  useGuideMeasure(measure);

  const fabLabel = tone === "owner" ? "새 의뢰" : "새 제안";
  const bellCenter = boxes?.bell && {
    x: boxes.bell.left + boxes.bell.width / 2,
    y: boxes.bell.top + boxes.bell.height / 2,
  };

  return (
    <GuideOverlay tone={tone} label="가입 후 첫 안내" okDelay={2} rootRef={rootRef} onClose={onClose}>
      {boxes?.card && (
        <>
          <p className="signup-guide__welcome" style={{ top: boxes.card.top - 43 }}>
            골목인턴에 오신 걸 환영해요
          </p>
          <div
            className="signup-guide__card"
            style={{ top: boxes.card.top, left: boxes.card.left, width: boxes.card.width }}
            aria-hidden="true"
          >
            {card}
          </div>
          <Tip n={1} text={tips[0]} style={{ top: boxes.card.top + boxes.card.height + 12, left: boxes.card.left }} />
        </>
      )}
      {boxes?.fab && (
        <>
          <span
            className={`signup-guide__fab fab fab--${tone}`}
            style={{ top: boxes.fab.top, left: boxes.fab.left }}
            aria-hidden="true"
          >
            <svg className="fab__plus" viewBox="0 0 20 20" fill="none">
              <path d="M10 4v12M4 10h12" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
            </svg>
            <span className="fab__label">
              <span>{fabLabel}</span>
            </span>
          </span>
          <Tip
            n={2}
            text={tips[1]}
            style={{ top: boxes.fab.top + boxes.fab.height / 2 - 21, right: boxes.width - boxes.fab.left + 8 }}
          />
        </>
      )}
      {boxes && bellCenter && (
        <>
          <span className="signup-guide__bell" style={{ top: bellCenter.y - 22, left: bellCenter.x - 22 }} aria-hidden="true">
            <AppImage name="iconBell" alt="" />
          </span>
          <Tip n={3} text={tips[2]} style={{ top: bellCenter.y - 21, right: boxes.width - bellCenter.x + 22 + 8 }} />
        </>
      )}
    </GuideOverlay>
  );
}

export default SignupGuide;
