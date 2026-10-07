import { useEffect, useId, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { formatMonthDayWeekday } from "../../lib/date";
import "./DateWheel.css";

/** 바퀴 한 칸 높이(px). 다섯 칸이 보이고 가운데 칸이 고른 값이다 */
const ROW = 44;

interface DateWheelProps {
  /** 고른 날 "2026-09-27" */
  value: string;
  /** 고를 수 있는 첫날 (이 날보다 앞은 목록에 없다) */
  min: string;
  /** 고를 수 있는 마지막 날 */
  max: string;
  onChange: (value: string) => void;
}

interface Parts {
  year: number;
  month: number;
  day: number;
}

function partsOf(isoDate: string): Parts {
  const [year, month, day] = isoDate.split("-").map(Number);
  return { year, month, day };
}

function isoOf({ year, month, day }: Parts): string {
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

function range(from: number, to: number): number[] {
  return Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i);
}

/** min ~ max 안의 가장 가까운 날로 맞춘다. 달을 바꿔 그 날이 없으면 그 달의 마지막 날 */
function clampDate(parts: Parts, min: Parts, max: Parts): Parts {
  const year = Math.min(Math.max(parts.year, min.year), max.year);
  const monthFrom = year === min.year ? min.month : 1;
  const monthTo = year === max.year ? max.month : 12;
  const month = Math.min(Math.max(parts.month, monthFrom), monthTo);
  const dayFrom = year === min.year && month === min.month ? min.day : 1;
  const dayTo = year === max.year && month === max.month ? max.day : daysInMonth(year, month);
  const day = Math.min(Math.max(parts.day, dayFrom), dayTo);
  return { year, month, day };
}

/**
 * 날짜 바퀴 (피그마 「날짜 바퀴」). 위에 고른 날, 가운데 연 · 월 · 일 세 줄 바퀴, 아래 안내 한 줄.
 * 위아래로 밀거나 숫자를 눌러 고르고, 키보드는 줄마다 위아래 방향키로 바꾼다. min 보다 앞선 날은 목록에 없다.
 */
function DateWheel({ value, min, max, onChange }: DateWheelProps) {
  const minParts = partsOf(min);
  const maxParts = partsOf(max);
  const current = clampDate(partsOf(value || min), minParts, maxParts);

  const years = range(minParts.year, maxParts.year);
  const months = range(
    current.year === minParts.year ? minParts.month : 1,
    current.year === maxParts.year ? maxParts.month : 12,
  );
  const days = range(
    current.year === minParts.year && current.month === minParts.month ? minParts.day : 1,
    current.year === maxParts.year && current.month === maxParts.month
      ? maxParts.day
      : daysInMonth(current.year, current.month),
  );

  const change = (next: Partial<Parts>) => {
    onChange(isoOf(clampDate({ ...current, ...next }, minParts, maxParts)));
  };

  return (
    <div className="date-wheel">
      <div className="date-wheel__head" aria-live="polite">
        <span className="date-wheel__year">{current.year}년</span>
        <strong className="date-wheel__date">{formatMonthDayWeekday(isoOf(current))}</strong>
      </div>
      <div className="date-wheel__wheel">
        <span className="date-wheel__bar" aria-hidden="true" />
        <WheelColumn
          label="연"
          items={years}
          unit="년"
          selected={current.year}
          onSelect={(year) => change({ year })}
        />
        <WheelColumn
          label="월"
          items={months}
          unit="월"
          selected={current.month}
          onSelect={(month) => change({ month })}
        />
        <WheelColumn label="일" items={days} unit="일" selected={current.day} onSelect={(day) => change({ day })} />
      </div>
      <p className="date-wheel__hint">위아래로 밀거나 숫자를 눌러 골라 주세요</p>
    </div>
  );
}

interface WheelColumnProps {
  label: string;
  items: number[];
  unit: string;
  selected: number;
  onSelect: (value: number) => void;
}

/** 바퀴 한 줄. 밀면 칸에 맞춰 멈추고(스크롤 스냅), 멈춘 칸을 고른다. 숫자를 누르면 그 칸으로 간다 */
function WheelColumn({ label, items, unit, selected, onSelect }: WheelColumnProps) {
  const ref = useRef<HTMLDivElement>(null);
  const idBase = useId();
  const selectedIndex = Math.max(0, items.indexOf(selected));
  // 미는 동안 가운데 온 칸 (글자 크기 · 색만 바꾼다). 멈춰 있으면 null
  const [live, setLive] = useState<number | null>(null);
  const settle = useRef<number | undefined>(undefined);
  const center = live ?? selectedIndex;

  // 고른 값이 바뀌면(다른 줄 때문에 맞춰진 경우 포함) 그 칸을 가운데로
  useEffect(() => {
    const el = ref.current;
    if (!el || live !== null) return;
    if (Math.round(el.scrollTop / ROW) !== selectedIndex) el.scrollTop = selectedIndex * ROW;
  }, [selectedIndex, items.length, live]);

  useEffect(() => () => window.clearTimeout(settle.current), []);

  const handleScroll = () => {
    const el = ref.current;
    if (!el) return;
    const index = Math.min(items.length - 1, Math.max(0, Math.round(el.scrollTop / ROW)));
    setLive(index);
    window.clearTimeout(settle.current);
    // 손을 떼고 칸에 멈추면 고른다
    settle.current = window.setTimeout(() => {
      setLive(null);
      if (items[index] !== selected) onSelect(items[index]);
    }, 120);
  };

  const pick = (index: number) => {
    ref.current?.scrollTo({ top: index * ROW, behavior: "smooth" });
    if (items[index] !== selected) onSelect(items[index]);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const step = e.key === "ArrowDown" ? 1 : e.key === "ArrowUp" ? -1 : 0;
    if (step === 0) return;
    e.preventDefault();
    const index = Math.min(items.length - 1, Math.max(0, selectedIndex + step));
    if (items[index] !== selected) onSelect(items[index]);
  };

  return (
    <div
      ref={ref}
      className="date-wheel__column"
      role="listbox"
      aria-label={label}
      aria-activedescendant={`${idBase}-${selectedIndex}`}
      tabIndex={0}
      onScroll={handleScroll}
      onKeyDown={handleKeyDown}
    >
      {items.map((item, i) => {
        const distance = Math.min(Math.abs(i - center), 3);
        return (
          <div
            key={item}
            id={`${idBase}-${i}`}
            role="option"
            aria-selected={i === selectedIndex}
            className={`date-wheel__item date-wheel__item--d${distance}`}
            onClick={() => pick(i)}
          >
            {item}
            {unit}
          </div>
        );
      })}
    </div>
  );
}

export default DateWheel;
