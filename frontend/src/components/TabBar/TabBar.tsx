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

interface TabBarProps {
  /** 점 색을 고른다 (사장님 노랑 · 학생 보라) */
  role: Role;
  current: MainTab;
  onSelect: (tab: MainTab) => void;
  /** 안 읽은 채팅 메시지가 있으면 「채팅」 아이콘에 점 */
  hasUnreadChat?: boolean;
  className?: string;
}

/** 하단 탭바 (글래스 · 3칸). 홈(내 일) · 탐색(다른 사람의 일) · 채팅 */
function TabBar({ role, current, onSelect, hasUnreadChat = false, className = "" }: TabBarProps) {
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
