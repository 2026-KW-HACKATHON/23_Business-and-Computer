import { useEffect, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { flushSync } from "react-dom";

const SWIPE_THRESHOLD_PX = 40;

/** [마지막 복제, ...카드, 첫 번째 복제] 에서 양 끝 복제 카드 자리인지 */
const isCloneAt = (position: number, count: number) =>
  count > 1 && (position === 0 || position === count + 1);

/**
 * 카드가 2장 이상이면 양 끝에 복제 카드를 붙인다.
 * 마지막 → 처음으로 갈 때 되감기지 않고 오른쪽으로 계속 넘어가 보이게 하려는 것이다.
 */
export function withLoopClones<T>(items: T[]): T[] {
  return items.length > 1 ? [items[items.length - 1], ...items, items[0]] : items;
}

interface LoopCarouselOptions {
  /** 진짜 카드 수 (복제 제외) */
  count: number;
  autoAdvanceMs: number;
  /** CSS transition 시간과 같게 둔다 */
  slideMs: number;
}

/**
 * 끝없이 도는 카드 넘기기. 일정 시간마다 저절로 다음 카드로 넘어가고,
 * 손으로 넘기면 거기서부터 다시 센다. 카드가 1장이면 넘기지 않는다.
 * 트랙은 withLoopClones 로 만든 목록을 그리고 offset 만큼 옮긴다.
 */
export function useLoopCarousel<T extends HTMLElement>({
  count,
  autoAdvanceMs,
  slideMs,
}: LoopCarouselOptions) {
  const looping = count > 1;
  // 복제를 붙인 목록에서의 위치. 1 ~ count 가 진짜 카드, 0 과 count + 1 은 복제 카드
  const [position, setPosition] = useState(1);
  const [animate, setAnimate] = useState(true);
  const trackRef = useRef<T>(null);
  const pointerStartX = useRef<number | null>(null);
  const swiped = useRef(false);
  const index = looping ? (position - 1 + count) % count : 0;

  // 보이는 카드가 바뀔 때마다 타이머를 새로 건다
  useEffect(() => {
    if (!looping) return;
    const timer = window.setTimeout(() => {
      setPosition((p) => (isCloneAt(p, count) ? p : p + 1));
    }, autoAdvanceMs);
    return () => window.clearTimeout(timer);
  }, [index, looping, count, autoAdvanceMs]);

  // 복제 카드에 도착하면, 넘어가는 효과가 끝난 뒤 효과 없이 진짜 카드 자리로 옮긴다.
  // 옮긴 모습을 먼저 확정하고(flushSync) 레이아웃을 한 번 계산시킨 다음 효과를 다시 켠다.
  useEffect(() => {
    if (!isCloneAt(position, count)) return;
    const timer = window.setTimeout(() => {
      flushSync(() => {
        setAnimate(false);
        setPosition(position === 0 ? count : 1);
      });
      void trackRef.current?.offsetWidth;
      setAnimate(true);
    }, slideMs);
    return () => window.clearTimeout(timer);
  }, [position, count, slideMs]);

  // 복제 카드에 머무는 동안(효과 도중)에는 무시한다
  const go = (step: number) => {
    setPosition((p) => (!looping || isCloneAt(p, count) ? p : p + step));
  };

  /** 옆으로 넘기기. 카드 영역에 그대로 펼쳐 넣는다 */
  const swipeHandlers = {
    onPointerDown: (e: PointerEvent<HTMLElement>) => {
      pointerStartX.current = e.clientX;
      swiped.current = false;
    },
    onPointerUp: (e: PointerEvent<HTMLElement>) => {
      if (pointerStartX.current === null) return;
      const dx = e.clientX - pointerStartX.current;
      pointerStartX.current = null;
      if (Math.abs(dx) < SWIPE_THRESHOLD_PX) return;
      swiped.current = true;
      go(dx < 0 ? 1 : -1);
    },
    onPointerCancel: () => {
      pointerStartX.current = null;
    },
  };

  /** 방금 옆으로 넘겼으면 true 를 돌려주고 표시를 지운다. 뒤따르는 click 을 무시할 때 쓴다 */
  const takeSwipe = () => {
    const was = swiped.current;
    swiped.current = false;
    return was;
  };

  return {
    /** 트랙을 옮길 칸 수 */
    offset: looping ? position : 0,
    /** 진짜 카드 기준 0부터 */
    index,
    animate,
    looping,
    trackRef,
    go,
    swipeHandlers,
    takeSwipe,
    isClone: (slide: number) => isCloneAt(slide, count),
  };
}
