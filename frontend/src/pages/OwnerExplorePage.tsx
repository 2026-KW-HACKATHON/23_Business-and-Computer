import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ExploreCard, FieldFilter, KindTabs, LoadNotice, SearchBar, TextButton } from "../components";
import type { CardKind } from "../components";
import {
  ExploreSortSheet,
  SORT_LABEL,
  categoryNames,
  exploreItemKey,
  jobStatusLabel,
  matchesKeyword,
  useExploreFeed,
  useLoadMoreSentinel,
} from "../features/explore";
import type { ExploreSort } from "../features/explore";
import {
  OWNER_PATHS,
  OwnerTabScreen,
  receivedProposalStatusLabel,
  useReceivedProposals,
} from "../features/owner";
import { formatMonthDay } from "../lib/date";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import { useDragScroll } from "../hooks/useDragScroll";
import "./OwnerExplorePage.css";

/**
 * 피그마 「사장님 탐색」. GET /explore 의 다른 가게 제안 · 의뢰를 종류 · 분야 · 정렬로 서버에서 거르고,
 * 검색어는 불러온 카드의 제목 · 가게 이름에서 찾는다. 끝까지 내리면 다음 쪽을 부른다 (ADR 0026).
 * 우리 가게가 받은 제안(GET /me/received-proposals 에 있는 id)은 「우리 가게가 받은 제안이에요」로 보이고
 * 받은 제안 상세로 간다. 검색창은 스크롤해도 위에 붙어 있다.
 */
function OwnerExplorePage() {
  const navigate = useNavigate();
  const [kind, setKind] = useState<CardKind>("all");
  const [field, setField] = useState<Field | null>(null);
  const [sort, setSort] = useState<ExploreSort>("LATEST");
  const [sortOpen, setSortOpen] = useState(false);
  const [query, setQuery] = useState("");
  const fieldScroll = useDragScroll<HTMLDivElement>();
  const feed = useExploreFeed({ kind, field, sort });
  const { load: received } = useReceivedProposals();
  const receivedIds = new Set(
    received.status === "loaded" ? received.proposals.map((p) => p.proposalId) : [],
  );
  const sentinel = useLoadMoreSentinel(
    feed.status === "loaded" && feed.hasNext && feed.more === "idle",
    feed.loadMore,
  );

  // 공감 많은 순은 제안 탭에만 있어서, 다른 탭으로 가면 최신순으로 돌아간다
  const changeKind = (next: CardKind) => {
    setKind(next);
    if (next !== "proposal" && sort === "LIKES") setSort("LATEST");
  };

  const keyword = query.trim();
  const visible = feed.items.filter((item) => matchesKeyword(item, keyword));

  return (
    <OwnerTabScreen tab="explore" title="탐색">
      <SearchBar sticky value={query} onChange={(e) => setQuery(e.target.value)} />
      <div className="owner-explore">
        <div className="owner-explore__filters">
          <KindTabs value={kind} onChange={changeKind} />
          <div className="owner-explore__fields" {...fieldScroll}>
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
            <TextButton showFilter showChevron={false} onClick={() => setSortOpen(true)}>
              {SORT_LABEL[sort]}
            </TextButton>
          </div>
          <p className="owner-explore__description">
            {"다른 가게가 받은 제안과 맡긴 의뢰를 둘러보고\n우리 가게에 맞는 아이디어를 찾아보세요"}
          </p>
        </div>

        {feed.status !== "loaded" ? (
          <LoadNotice
            layout="cards"
            status={feed.status}
            loadingText="제안·의뢰를 불러오는 중이에요"
            errorText="제안·의뢰를 불러오지 못했어요"
            onRetry={feed.reload}
          />
        ) : (
          <>
            {visible.length > 0 && (
              <ul className="owner-explore__list">
                {visible.map((item) => (
                  <li key={exploreItemKey(item)}>
                    {item.type === "PROPOSAL" ? (
                      <ExploreCard
                        kind="proposal"
                        title={item.title}
                        fields={categoryNames(item.specialtyCategories)}
                        storeName={item.storeName}
                        empathyCount={item.likeCount}
                        status={item.status ? receivedProposalStatusLabel(item.status) : undefined}
                        hint={receivedIds.has(item.proposalId) ? "우리 가게가 받은 제안이에요" : undefined}
                        onOpen={() =>
                          navigate(
                            receivedIds.has(item.proposalId)
                              ? OWNER_PATHS.proposal(String(item.proposalId))
                              : OWNER_PATHS.exploreProposal(String(item.proposalId)),
                          )
                        }
                      />
                    ) : (
                      <ExploreCard
                        kind="request"
                        title={item.title}
                        fields={categoryNames(item.specialtyCategories)}
                        storeName={item.storeName}
                        deadline={
                          item.status === "OPEN"
                            ? { stage: "draft", due: `${formatMonthDay(item.draftDeadline)}까지` }
                            : undefined
                        }
                        status={jobStatusLabel(item.status)}
                        onOpen={() => navigate(OWNER_PATHS.exploreRequest(String(item.jobId)))}
                      />
                    )}
                  </li>
                ))}
              </ul>
            )}
            {visible.length === 0 && !feed.hasNext && (
              <p className="owner-explore__empty">조건에 맞는 제안·의뢰가 없어요</p>
            )}
            {feed.hasNext && <div ref={sentinel} aria-hidden="true" />}
            {feed.more !== "idle" && (
              <LoadNotice
                layout="more"
                status={feed.more}
                loadingText="더 불러오는 중이에요"
                errorText="더 불러오지 못했어요"
                onRetry={feed.loadMore}
              />
            )}
          </>
        )}
      </div>

      <ExploreSortSheet
        open={sortOpen}
        value={sort}
        likes={kind === "proposal"}
        onSelect={(next) => {
          setSort(next);
          setSortOpen(false);
        }}
        onClose={() => setSortOpen(false)}
      />
    </OwnerTabScreen>
  );
}

export default OwnerExplorePage;
