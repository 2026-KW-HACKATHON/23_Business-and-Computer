import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { LoadNotice, RoleAvatar, SearchBar, TextButton } from "../components";
import {
  ExploreTabs,
  STUDENT_PATHS,
  StudentTabScreen,
  useExploreStores,
} from "../features/student";
import { STORE_CATEGORIES } from "../types/storeCategory";
import type { StoreCategory } from "../types/storeCategory";
import { useDragScroll } from "../hooks/useDragScroll";
import "./StudentExplorePage.css";
import "./StudentStoresPage.css";

/**
 * 피그마 「학생 탐색 · 가게」. 월계1동 가게를 업종 · 이름으로 찾고,
 * 「제안하기」를 누르면 그 가게를 고른 채 제안 보내기 2/4 로 간다.
 * 가게는 GET /explore/stores 를 모두 불러와 화면에서 거른다 (이름 검색 API 가 없다, ADR 0020).
 */
function StudentStoresPage() {
  const navigate = useNavigate();
  const { load, reload } = useExploreStores();
  const [category, setCategory] = useState<StoreCategory | null>(null);
  const [query, setQuery] = useState("");
  const chipScroll = useDragScroll<HTMLDivElement>();

  const keyword = query.trim();
  const stores = load.status === "loaded" ? load.stores : [];
  // 업종 칩은 피그마의 고정 11개, 백엔드 업종 이름과 같은 글자로 거른다
  const visible = stores
    .filter((s) => category === null || s.category === category)
    .filter((s) => keyword === "" || s.name.includes(keyword));

  const chip = (label: string, value: StoreCategory | null) => {
    const selected = category === value;
    return (
      <button
        key={label}
        type="button"
        className={`student-stores__chip${selected ? " student-stores__chip--selected" : ""}`}
        aria-pressed={selected}
        onClick={() => setCategory(value)}
      >
        {selected && (
          <span className="student-stores__chip-check" aria-hidden="true">
            <svg viewBox="0 0 10 10" fill="none">
              <path d="m2 5.2 2 2L8 3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
        )}
        {label}
      </button>
    );
  };

  return (
    <StudentTabScreen tab="explore" title="탐색">
      <ExploreTabs current="stores" />
      <SearchBar
        sticky
        placeholder="가게 이름으로 검색"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div className="student-explore">
        <div className="student-stores__chips" {...chipScroll}>
          {chip("전체", null)}
          {STORE_CATEGORIES.map((c) => chip(c, c))}
        </div>

        <div className="student-explore__divider" />

        <div className="student-explore__head">
          <div className="student-explore__title-row">
            <h2 className="student-explore__title">월계1동 가게</h2>
            <TextButton showFilter showChevron={false}>
              등록순
            </TextButton>
          </div>
          <p className="student-explore__description">자주 가는 가게를 골라 먼저 제안해 보세요.</p>
        </div>

        {load.status !== "loaded" ? (
          <LoadNotice
            status={load.status}
            loadingText="가게 목록을 불러오는 중이에요"
            errorText="가게 목록을 불러오지 못했어요"
            onRetry={reload}
          />
        ) : visible.length > 0 ? (
          <ul className="student-stores__list">
            {visible.map((store) => (
              <li key={store.ownerProfileId} className="student-stores__row">
                <RoleAvatar role="owner" size={40} />
                <span className="student-stores__info">
                  <span className="student-stores__name">
                    <strong>{store.name}</strong>
                    <span className="student-stores__category">{store.category}</span>
                  </span>
                  <span className="student-stores__address">{store.address}</span>
                </span>
                <TextButton
                  onClick={() =>
                    navigate(STUDENT_PATHS.newProposalStep(2), { state: { store } })
                  }
                >
                  제안하기
                </TextButton>
              </li>
            ))}
          </ul>
        ) : (
          <p className="student-explore__empty">조건에 맞는 가게가 없어요</p>
        )}
      </div>
    </StudentTabScreen>
  );
}

export default StudentStoresPage;
