import { useEffect, useRef, useState } from "react";
import type { PointerEvent } from "react";
import AppImage from "../AppImage/AppImage";
import { preloadImages } from "../AppImage/images";
import type { ImageName } from "../AppImage/images";
import PageDots from "../PageDots/PageDots";
import "./UsageCardCarousel.css";

/** 제안 → 공감 → 의뢰 순서 */
const CARDS: ImageName[] = ["usageProposal", "usageEmpathy", "usageRequest"];

const AUTO_ADVANCE_MS = 2000;
const SWIPE_THRESHOLD_PX = 40;

interface UsageCardCarouselProps {
  className?: string;
}

/**
 * 사용법 카드 넘기기. 2초마다 저절로 다음 카드로 넘어가고,
 * 옆으로 넘기거나 눌러도 넘어간다 (마지막 다음은 처음).
 */
function UsageCardCarousel({ className = "" }: UsageCardCarouselProps) {
  const [index, setIndex] = useState(0);
  const pointerStartX = useRef<number | null>(null);
  const swiped = useRef(false);

  useEffect(() => {
    preloadImages(CARDS.slice(1));
  }, []);

  // index 가 바뀔 때마다 타이머를 새로 건다. 손으로 넘기면 거기서부터 다시 2초.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setIndex((i) => (i + 1) % CARDS.length);
    }, AUTO_ADVANCE_MS);
    return () => window.clearTimeout(timer);
  }, [index]);

  const go = (step: number) => {
    setIndex((i) => (i + step + CARDS.length) % CARDS.length);
  };

  const handlePointerDown = (e: PointerEvent<HTMLButtonElement>) => {
    pointerStartX.current = e.clientX;
    swiped.current = false;
  };

  const handlePointerUp = (e: PointerEvent<HTMLButtonElement>) => {
    if (pointerStartX.current === null) return;
    const dx = e.clientX - pointerStartX.current;
    pointerStartX.current = null;
    if (Math.abs(dx) < SWIPE_THRESHOLD_PX) return;
    swiped.current = true;
    go(dx < 0 ? 1 : -1);
  };

  // 누르기(또는 키보드)는 다음 카드. 방금 옆으로 넘겼다면 뒤따르는 click 은 무시한다.
  const handleClick = () => {
    if (swiped.current) {
      swiped.current = false;
      return;
    }
    go(1);
  };

  return (
    <section
      className={`usage-carousel ${className}`.trim()}
      aria-roledescription="carousel"
      aria-label="가꿈 사용법"
    >
      <button
        type="button"
        className="usage-carousel__viewport"
        aria-label="다음 사용법 보기"
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerCancel={() => (pointerStartX.current = null)}
        onClick={handleClick}
      >
        <span className="usage-carousel__track" style={{ transform: `translateX(-${index * 100}%)` }}>
          {CARDS.map((name, i) => (
            <span key={name} className="usage-carousel__card" aria-hidden={i !== index}>
              <AppImage name={name} priority={i === 0} />
            </span>
          ))}
        </span>
      </button>
      <PageDots total={CARDS.length} current={index + 1} />
    </section>
  );
}

export default UsageCardCarousel;
