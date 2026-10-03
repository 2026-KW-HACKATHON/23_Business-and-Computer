import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button, RoleAvatar, SearchBar, StepIndicator, SubScreen } from "../components";
import { STUDENT_PATHS, readNewProposalState, useStores } from "../features/student";
import type { NewProposalState } from "../features/student";
import { useBack } from "../hooks/useBack";
import { useDragScroll } from "../hooks/useDragScroll";
import { STORE_CATEGORIES } from "../types/storeCategory";
import type { StoreCategory } from "../types/storeCategory";
import "./StudentStoresPage.css";
import "./StudentProposalNewPage.css";

/**
 * 피그마 「제안 보내기 1/4 - 가게 고르기」. 업종 · 이름으로 찾아 가게 하나를 고른다.
 * 홈 「+ 새 제안」 · 「이런 제안은 어때요?」 예시에서 들어온다.
 */
function StudentProposalStorePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(STUDENT_PATHS.home);
  const stores = useStores();
  const exampleId = (location.state as { exampleId?: string } | null)?.exampleId;
  // 2/4 에서 ← 로 돌아오면 저장해 둔 값으로 시작한다
  const saved = readNewProposalState(location.state);
  const [storeId, setStoreId] = useState(saved?.storeId);
  const [category, setCategory] = useState<StoreCategory | null>(null);
  const [query, setQuery] = useState("");
  const chipScroll = useDragScroll<HTMLDivElement>();

  const keyword = query.trim();
  const visible = stores
    .filter((s) => category === null || s.category === category)
    .filter((s) => keyword === "" || s.name.includes(keyword));

  const goNext = () => {
    const next: NewProposalState = {
      ...saved,
      exampleId: saved?.exampleId ?? exampleId,
      storeId,
      fields: saved?.fields ?? [],
      picked: saved?.picked ?? [],
    };
    navigate(location.pathname, { replace: true, state: next });
    navigate(STUDENT_PATHS.newProposalStep(2), { state: next });
  };

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
    <SubScreen
      title="제안 보내기"
      onBack={back}
      footer={
        <Button tone="student" fullWidth disabled={!storeId} onClick={goNext}>
          다음
        </Button>
      }
    >
      <div className="student-new">
        <StepIndicator total={4} current={1} tone="student" />

        <div className="student-new__intro">
          <h2 className="student-new__title">어느 가게에 제안할까요?</h2>
          <p className="student-new__description">자주 가는 월계1동 가게를 골라 주세요</p>
        </div>

        <SearchBar
          placeholder="가게 이름으로 검색"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <div className="student-stores__chips" {...chipScroll}>
          {chip("전체", null)}
          {STORE_CATEGORIES.map((c) => chip(c, c))}
        </div>

        <ul className="student-new__stores" role="radiogroup" aria-label="제안할 가게">
          {visible.map((store) => {
            const selected = store.id === storeId;
            return (
              <li key={store.id}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  aria-label={store.name}
                  className={`student-new__store${selected ? " student-new__store--selected" : ""}`}
                  onClick={() => setStoreId(store.id)}
                >
                  <RoleAvatar role="owner" size={40} />
                  <span className="student-new__store-info">
                    <strong>{store.name}</strong>
                    <span>
                      {store.category} · {store.address}
                    </span>
                  </span>
                  <span className="student-new__radio" aria-hidden="true">
                    {selected && (
                      <svg viewBox="0 0 12 12" fill="none">
                        <path d="M2.5 6.2 4.9 8.5 9.5 3.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    )}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
        {visible.length === 0 && <p className="student-new__empty">조건에 맞는 가게가 없어요</p>}
      </div>
    </SubScreen>
  );
}

export default StudentProposalStorePage;
