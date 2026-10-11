import { useEffect } from "react";
import { useLoopCarousel, withLoopClones } from "../../hooks/useLoopCarousel";
import AppImage from "../AppImage/AppImage";
import { preloadImages } from "../AppImage/images";
import type { ImageName } from "../AppImage/images";
import PageDots from "../PageDots/PageDots";
import "./UsageCardCarousel.css";

/** 사용법 카드 한 장. 위 라벨은 누가 먼저 시작하는지, 아래 설명은 한 줄 */
interface UsageCard {
  image: ImageName;
  label: string;
  tone: "owner" | "student" | "empathy";
  caption: string;
}

/** 의뢰(사장님이 먼저)와 제안(학생이 먼저)은 서로 다른 시작이고, 공감은 제안에 더하는 것 (ADR 0075) */
const CARDS: UsageCard[] = [
  {
    image: "usageRequest",
    label: "사장님이 먼저 · 의뢰",
    tone: "owner",
    caption: "사장님이 의뢰를 올리면 학생이 지원해요",
  },
  {
    image: "usageProposal",
    label: "학생이 먼저 · 제안",
    tone: "student",
    caption: "학생이 가게에 먼저 제안하면 사장님이 골라요",
  },
  {
    image: "usageEmpathy",
    label: "제안에 더하기 · 공감",
    tone: "empathy",
    caption: "다른 학생들이 좋은 제안에 공감해요",
  },
];
const SLIDES = withLoopClones(CARDS);

const AUTO_ADVANCE_MS = 2000;
/** UsageCardCarousel.css 의 transition 시간과 같게 둔다 */
const SLIDE_MS = 300;

interface UsageCardCarouselProps {
  className?: string;
}

/**
 * 사용법 카드 넘기기 (의뢰 → 제안 → 공감). 카드마다 위에 라벨, 아래에 한 줄 설명. 2초마다 저절로 다음 카드로 넘어가고,
 * 옆으로 넘기거나 눌러도 넘어간다. 마지막 다음은 오른쪽으로 이어서 처음.
 */
function UsageCardCarousel({ className = "" }: UsageCardCarouselProps) {
  const { offset, index, animate, trackRef, go, swipeHandlers, takeSwipe, isClone } =
    useLoopCarousel<HTMLSpanElement>({
      count: CARDS.length,
      autoAdvanceMs: AUTO_ADVANCE_MS,
      slideMs: SLIDE_MS,
    });

  useEffect(() => {
    preloadImages(CARDS.slice(1).map((card) => card.image));
  }, []);

  // 누르기(또는 키보드)는 다음 카드. 방금 옆으로 넘겼다면 뒤따르는 click 은 무시한다.
  const handleClick = () => {
    if (takeSwipe()) return;
    go(1);
  };

  return (
    <section
      className={`usage-carousel ${className}`.trim()}
      aria-roledescription="carousel"
      aria-label="골목인턴 사용법"
    >
      <button
        type="button"
        className="usage-carousel__viewport"
        aria-label="다음 사용법 보기"
        {...swipeHandlers}
        onClick={handleClick}
      >
        <span
          ref={trackRef}
          className={`usage-carousel__track${animate ? " usage-carousel__track--animate" : ""}`}
          style={{ transform: `translateX(-${offset * 100}%)` }}
        >
          {SLIDES.map((card, i) => (
            <span
              key={i}
              className="usage-carousel__card"
              aria-hidden={i !== offset || isClone(i)}
            >
              <span className={`usage-carousel__label usage-carousel__label--${card.tone}`}>{card.label}</span>
              <AppImage name={card.image} priority={i === 1} alt="" />
              <span className="usage-carousel__caption">{card.caption}</span>
            </span>
          ))}
        </span>
      </button>
      <PageDots total={CARDS.length} current={index + 1} />
    </section>
  );
}

export default UsageCardCarousel;
