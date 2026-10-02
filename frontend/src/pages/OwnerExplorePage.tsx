import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ExploreCard, FieldFilter, KindTabs, SearchBar, TextButton } from "../components";
import type { CardKind } from "../components";
import {
  EXPLORE_PROGRESS_LABEL,
  OWNER_PATHS,
  OwnerTabScreen,
  useOwnerExplore,
} from "../features/owner";
import { formatMonthDay } from "../lib/date";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import "./OwnerExplorePage.css";

/**
 * 피그마 「사장님 탐색」. 다른 가게의 제안·의뢰를 종류 · 분야 · 검색어로 거른다.
 * 검색창은 스크롤해도 위에 붙어 있다.
 */
function OwnerExplorePage() {
  const navigate = useNavigate();
  const items = useOwnerExplore();
  const [kind, setKind] = useState<CardKind>("all");
  const [field, setField] = useState<Field | null>(null);
  const [query, setQuery] = useState("");

  const keyword = query.trim();
  const visible = items
    .filter((item) => kind === "all" || item.kind === kind)
    .filter((item) => field === null || item.field === field)
    .filter(
      (item) => keyword === "" || item.title.includes(keyword) || item.storeName.includes(keyword),
    )
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <OwnerTabScreen tab="explore" title="탐색">
      <SearchBar sticky value={query} onChange={(e) => setQuery(e.target.value)} />
      <div className="owner-explore">
        <div className="owner-explore__filters">
          <KindTabs value={kind} onChange={setKind} />
          <div className="owner-explore__fields">
            <FieldFilter selected={field === null} onClick={() => setField(null)} />
            {FIELDS.map((f) => (
              <FieldFilter key={f} field={f} selected={field === f} onClick={() => setField(f)} />
            ))}
          </div>
        </div>

        <div className="owner-explore__divider" />

        <div className="owner-explore__head">
          <div className="owner-explore__title-row">
            <h2 className="owner-explore__title">다른 가게의 제안·의뢰</h2>
            <TextButton showFilter showChevron={false}>
              최신순
            </TextButton>
          </div>
          <p className="owner-explore__description">
            {"다른 가게가 받은 제안과 맡긴 의뢰를 둘러보고\n우리 가게에 맞는 아이디어를 찾아보세요"}
          </p>
        </div>

        {visible.length > 0 ? (
          <ul className="owner-explore__list">
            {visible.map((item) => (
              <li key={item.id}>
                <ExploreCard
                  kind={item.kind}
                  title={item.title}
                  field={item.field}
                  storeName={item.storeName}
                  empathyCount={item.empathyCount}
                  deadline={
                    item.deadline && {
                      stage: item.deadline.stage,
                      due: `${formatMonthDay(item.deadline.due)}까지`,
                    }
                  }
                  status={item.progress && EXPLORE_PROGRESS_LABEL[item.progress]}
                  onOpen={() =>
                    navigate(
                      item.kind === "proposal"
                        ? OWNER_PATHS.exploreProposal(item.id)
                        : OWNER_PATHS.exploreRequest(item.id),
                    )
                  }
                />
              </li>
            ))}
          </ul>
        ) : (
          <p className="owner-explore__empty">조건에 맞는 제안·의뢰가 없어요</p>
        )}
      </div>
    </OwnerTabScreen>
  );
}

export default OwnerExplorePage;
