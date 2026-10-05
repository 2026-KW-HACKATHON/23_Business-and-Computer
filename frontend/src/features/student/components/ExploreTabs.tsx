import { useNavigate } from "react-router-dom";
import { STUDENT_PATHS } from "../lib/paths";
import "./ExploreTabs.css";

interface ExploreTabsProps {
  current: "works" | "stores";
}

/** 학생 탐색 위 「제안·의뢰 · 가게」 탭. 고른 탭은 자주 밑줄 */
function ExploreTabs({ current }: ExploreTabsProps) {
  const navigate = useNavigate();
  const tabs = [
    { key: "works", label: "제안·의뢰", path: STUDENT_PATHS.explore },
    { key: "stores", label: "가게", path: STUDENT_PATHS.exploreStores },
  ] as const;

  return (
    <nav className="explore-tabs" aria-label="탐색 종류">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          type="button"
          className={`explore-tabs__tab${current === tab.key ? " explore-tabs__tab--current" : ""}`}
          aria-current={current === tab.key ? "page" : undefined}
          onClick={() => current !== tab.key && navigate(tab.path, { replace: true })}
        >
          {tab.label}
        </button>
      ))}
    </nav>
  );
}

export default ExploreTabs;
