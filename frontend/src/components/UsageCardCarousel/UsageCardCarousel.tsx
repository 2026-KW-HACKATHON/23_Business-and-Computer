import { useEffect } from "react";
import { useLoopCarousel, withLoopClones } from "../../hooks/useLoopCarousel";
import AppImage from "../AppImage/AppImage";
import { preloadImages } from "../AppImage/images";
import type { ImageName } from "../AppImage/images";
import PageDots from "../PageDots/PageDots";
import "./UsageCardCarousel.css";

/** 제안 → 공감 → 의뢰 순서 */
const CARDS: ImageName[] = ["usageProposal", "usageEmpathy", "usageRequest"];
const SLIDES = withLoopClones(CARDS);

const AUTO_ADVANCE_MS = 2000;
/** UsageCardCarousel.css 의 transition 시간과 같게 둔다 */
const SLIDE_MS = 300;

interface UsageCardCarouselProps {
  className?: string;
}

/**
 * 사용법 카드 넘기기. 2초마다 저절로 다음 카드로 넘어가고,
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
    preloadImages(CARDS.slice(1));
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
          {SLIDES.map((name, i) => (
            <span
              key={i}
              className="usage-carousel__card"
              aria-hidden={i !== offset || isClone(i)}
            >
              <AppImage name={name} priority={i === 1} />
            </span>
          ))}
        </span>
      </button>
      <PageDots total={CARDS.length} current={index + 1} />
    </section>
  );
}

export default UsageCardCarousel;
