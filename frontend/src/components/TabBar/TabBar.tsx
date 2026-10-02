import type { ImageName } from "../AppImage/images";
import MaskIcon from "../MaskIcon/MaskIcon";
import "./TabBar.css";

export type MainTab = "home" | "explore" | "chat";

const TABS: { tab: MainTab; label: string; icon: ImageName }[] = [
  { tab: "home", label: "홈", icon: "iconTabHome" },
  { tab: "explore", label: "탐색", icon: "iconTabSearch" },
  { tab: "chat", label: "채팅", icon: "iconTabChat" },
];

interface TabBarProps {
  current: MainTab;
  onSelect: (tab: MainTab) => void;
  className?: string;
}

/** 하단 탭바 (글래스 · 3칸). 홈(내 일) · 탐색(다른 사람의 일) · 채팅 */
function TabBar({ current, onSelect, className = "" }: TabBarProps) {
  return (
    <nav className={`tab-bar ${className}`.trim()} aria-label="주요 메뉴">
      {TABS.map(({ tab, label, icon }) => (
        <button
          key={tab}
          type="button"
          className={`tab-bar__tab${tab === current ? " tab-bar__tab--current" : ""}`}
          aria-current={tab === current ? "page" : undefined}
          onClick={() => onSelect(tab)}
        >
          <MaskIcon name={icon} size={24} />
          {label}
        </button>
      ))}
    </nav>
  );
}

export default TabBar;
