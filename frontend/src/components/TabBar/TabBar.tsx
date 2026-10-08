import { useLayoutEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import type { Role } from "../../types/role";
import type { ImageName } from "../AppImage/images";
import MaskIcon from "../MaskIcon/MaskIcon";
import "./TabBar.css";

export type MainTab = "home" | "explore" | "chat";

const TABS: { tab: MainTab; label: string; icon: ImageName }[] = [
  { tab: "home", label: "홈", icon: "iconTabHome" },
  { tab: "explore", label: "탐색", icon: "iconTabSearch" },
  { tab: "chat", label: "채팅", icon: "iconTabChat" },
];

const tabIndex = (tab: MainTab) => TABS.findIndex((t) => t.tab === tab);

/**
 * 마지막으로 그린 탭바의 탭. 탭을 누르면 화면이 바뀌며 탭바도 새로 그려지므로, 새 탭바는 선택 배경을
 * 이 탭 자리에서 시작해 새 탭으로 옮긴다. 앱을 처음 열었을 때는 없다
 */
let lastTab: MainTab | undefined;

interface TabBarProps {
  /** 점 색을 고른다 (사장님 노랑 · 학생 보라) */
  role: Role;
  current: MainTab;
  onSelect: (tab: MainTab) => void;
  /** 안 읽은 채팅 메시지가 있으면 「채팅」 아이콘에 점 */
  hasUnreadChat?: boolean;
  className?: string;
}

/**
 * 하단 탭바 (글래스 · 3칸). 홈(내 일) · 탐색(다른 사람의 일) · 채팅.
 * 다른 탭에서 넘어오면 선택 배경이 그 탭 자리에서 새 탭으로 미끄러지고(가는 동안 살짝 늘어남),
 * 새 탭 아이콘이 한 번 톡 튄다 (ADR 0055)
 */
function TabBar({ role, current, onSelect, hasUnreadChat = false, className = "" }: TabBarProps) {
  const index = tabIndex(current);
  // 선택 배경을 처음 그릴 자리. 다른 탭에서 넘어왔으면 그 탭 자리
  const [from] = useState(() => (lastTab === undefined ? index : tabIndex(lastTab)));
  const moved = from !== index;
  const indicator = useRef<HTMLSpanElement>(null);
  const played = useRef(false);

  useLayoutEffect(() => {
    lastTab = current;
  }, [current]);

  // 넘어온 탭 자리를 스타일로 한 번 계산시킨 뒤 새 자리로 바꿔야 미끄러진다. 화면을 그리기 전에 한 번만
  useLayoutEffect(() => {
    const el = indicator.current;
    if (!el || !moved || played.current) return;
    played.current = true;
    el.style.transition = "none";
    el.style.setProperty("--tab-index", String(from));
    el.getBoundingClientRect();
    el.style.transition = "";
    el.style.setProperty("--tab-index", String(index));
    el.classList.add("tab-bar__indicator--moving");
  }, [from, index, moved]);

  return (
    <nav className={`tab-bar ${className}`.trim()} aria-label="주요 메뉴">
      <span
        ref={indicator}
        className="tab-bar__indicator"
        style={{ "--tab-index": index } as CSSProperties}
        aria-hidden="true"
      />
      {TABS.map(({ tab, label, icon }) => (
        <button
          key={tab}
          type="button"
          className={`tab-bar__tab${tab === current ? " tab-bar__tab--current" : ""}${
            tab === current && moved ? " tab-bar__tab--arrived" : ""
          }`}
          aria-current={tab === current ? "page" : undefined}
          onClick={() => onSelect(tab)}
        >
          <span className="tab-bar__icon">
            <MaskIcon name={icon} size={24} />
            {tab === "chat" && hasUnreadChat && (
              <span className={`tab-bar__dot tab-bar__dot--${role}`} aria-hidden="true" />
            )}
          </span>
          {label}
          {/* 점은 눈으로만 보이니 화면 낭독기에는 「채팅, 안 읽은 메시지 있음」으로 읽힌다 */}
          {tab === "chat" && hasUnreadChat && <span className="tab-bar__sr-only">, 안 읽은 메시지 있음</span>}
        </button>
      ))}
    </nav>
  );
}

export default TabBar;
