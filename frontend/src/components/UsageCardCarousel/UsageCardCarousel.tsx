import { useEffect, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { flushSync } from "react-dom";
import AppImage from "../AppImage/AppImage";
import { preloadImages } from "../AppImage/images";
import type { ImageName } from "../AppImage/images";
import PageDots from "../PageDots/PageDots";
import "./UsageCardCarousel.css";

/** 제안 → 공감 → 의뢰 순서 */
const CARDS: ImageName[] = ["usageProposal", "usageEmpathy", "usageRequest"];
const COUNT = CARDS.length;

/**
 * 양 끝에 복제 카드를 붙인다: [마지막 복제, 1, 2, 3, 첫 번째 복제].
 * 3 → 1 로 갈 때 되감기지 않고 오른쪽으로 계속 넘어가 보이게 하려는 것이다.
 */
const SLIDES: ImageName[] = [CARDS[COUNT - 1], ...CARDS, CARDS[0]];

const AUTO_ADVANCE_MS = 2000;
/** UsageCardCarousel.css 의 transition 시간과 같게 둔다 */
const SLIDE_MS = 300;
const SWIPE_THRESHOLD_PX = 40;

const isClone = (position: number) => position === 0 || position === COUNT + 1;

interface UsageCardCarouselProps {
  className?: string;
}

/**
 * 사용법 카드 넘기기. 2초마다 저절로 다음 카드로 넘어가고,
 * 옆으로 넘기거나 눌러도 넘어간다. 마지막 다음은 오른쪽으로 이어서 처음.
 */
function UsageCardCarousel({ className = "" }: UsageCardCarouselProps) {
  // SLIDES 에서의 위치. 1 ~ COUNT 가 진짜 카드, 0 과 COUNT + 1 은 복제 카드
  const [position, setPosition] = useState(1);
  const [animate, setAnimate] = useState(true);
  const trackRef = useRef<HTMLSpanElement>(null);
  const pointerStartX = useRef<number | null>(null);
  const swiped = useRef(false);
  const index = (position - 1 + COUNT) % COUNT;

  useEffect(() => {
    preloadImages(CARDS.slice(1));
  }, []);

  // 보이는 카드가 바뀔 때마다 타이머를 새로 건다. 손으로 넘기면 거기서부터 다시 2초.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setPosition((p) => (isClone(p) ? p : p + 1));
    }, AUTO_ADVANCE_MS);
    return () => window.clearTimeout(timer);
  }, [index]);

  // 복제 카드에 도착하면, 넘어가는 효과가 끝난 뒤 효과 없이 진짜 카드 자리로 옮긴다.
  // 옮긴 모습을 먼저 확정하고(flushSync) 레이아웃을 한 번 계산시킨 다음 효과를 다시 켠다.
  useEffect(() => {
    if (!isClone(position)) return;
    const timer = window.setTimeout(() => {
      flushSync(() => {
        setAnimate(false);
        setPosition(position === 0 ? COUNT : 1);
      });
      void trackRef.current?.offsetWidth;
      setAnimate(true);
    }, SLIDE_MS);
    return () => window.clearTimeout(timer);
  }, [position]);

  // 복제 카드에 머무는 동안(효과 도중)에는 무시한다
  const go = (step: number) => {
    setPosition((p) => (isClone(p) ? p : p + step));
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
        <span
          ref={trackRef}
          className={`usage-carousel__track${animate ? " usage-carousel__track--animate" : ""}`}
          style={{ transform: `translateX(-${position * 100}%)` }}
        >
          {SLIDES.map((name, i) => (
            <span
              key={i}
              className="usage-carousel__card"
              aria-hidden={i !== position || isClone(i)}
            >
              <AppImage name={name} priority={i === 1} />
            </span>
          ))}
        </span>
      </button>
      <PageDots total={COUNT} current={index + 1} />
    </section>
  );
}

export default UsageCardCarousel;
