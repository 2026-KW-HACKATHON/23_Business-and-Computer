import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FieldFilter, KindTabs, SearchBar, TextButton } from "../components";
import type { CardKind } from "../components";
import {
  ExploreSortSheet,
  SORT_LABEL,
  exploreItemKey,
  matchesKeyword,
  useExploreFeed,
  useLoadMoreSentinel,
} from "../features/explore";
import type { ExploreProposalCard, ExploreSort } from "../features/explore";
import {
  ExploreTabs,
  PeerProposalCard,
  RequestCard,
  STUDENT_PATHS,
  StudentTabScreen,
  useSentProposals,
} from "../features/student";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import { useDragScroll } from "../hooks/useDragScroll";
import "./StudentExplorePage.css";
import { LoadNotice } from "../components";
import { useProposalLikes } from "../features/proposal";

/**
 * 피그마 「학생 탐색」. GET /explore 의 의뢰 · 제안을 종류 · 분야 · 정렬로 서버에서 거르고,
 * 검색어는 불러온 카드의 제목 · 가게 이름에서 찾는다. 끝까지 내리면 다음 쪽을 부른다 (ADR 0026).
 * 내 제안(GET /me/proposals 에 있는 id)은 「내 제안이에요」로 보이고 보낸 제안서로 간다.
 * 다른 학생 제안은 하트로 공감하고 다시 누르면 취소한다 (useProposalLikes).
 */
function StudentExplorePage() {
  const navigate = useNavigate();
  const [kind, setKind] = useState<CardKind>("all");
  const [field, setField] = useState<Field | null>(null);
  const [sort, setSort] = useState<ExploreSort>("LATEST");
  const [sortOpen, setSortOpen] = useState(false);
  const [query, setQuery] = useState("");
  const fieldScroll = useDragScroll<HTMLDivElement>();
  const feed = useExploreFeed({ kind, field, sort });
  const { load: sent } = useSentProposals();
  const likes = useProposalLikes();
  const sentIds = new Set(sent.status === "loaded" ? sent.proposals.map((p) => p.proposalId) : []);
  const sentinel = useLoadMoreSentinel(
    feed.status === "loaded" && feed.hasNext && feed.more === "idle",
    feed.loadMore,
  );

  // 공감 많은 순은 제안 탭에만 있어서, 다른 탭으로 가면 최신순으로 돌아간다
  const changeKind = (next: CardKind) => {
    setKind(next);
    if (next !== "proposal" && sort === "LIKES") setSort("LATEST");
  };

  // 이 화면에서 누른 공감이 있으면 그 값, 없으면 카드의 값
  const likeOf = (item: ExploreProposalCard) =>
    likes.likeOf(item.proposalId, { likeCount: item.likeCount, likedByMe: item.likedByMe === true });

  const keyword = query.trim();
  const visible = feed.items.filter((item) => matchesKeyword(item, keyword));

  return (
    <StudentTabScreen tab="explore" title="탐색">
      <ExploreTabs current="works" />
      <SearchBar
        sticky
        placeholder="가게 이름이나 작업으로 검색"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div className="student-explore">
        <div className="student-explore__filters">
          <KindTabs value={kind} onChange={changeKind} />
          <div className="student-explore__fields" {...fieldScroll}>
            <FieldFilter selected={field === null} onClick={() => setField(null)} />
            {FIELDS.map((f) => (
              <FieldFilter key={f} field={f} selected={field === f} onClick={() => setField(f)} />
            ))}
          </div>
        </div>

        <div className="student-explore__divider" />

        <div className="student-explore__head">
          <div className="student-explore__title-row">
            <h2 className="student-explore__title">지금 올라온 의뢰·제안</h2>
            <TextButton showFilter showChevron={false} onClick={() => setSortOpen(true)}>
              {SORT_LABEL[sort]}
            </TextButton>
          </div>
          <p className="student-explore__description">
            {"가게 의뢰에 지원하고, 다른 학생의 제안에\n하트를 눌러 손님으로서 공감해 보세요."}
          </p>
        </div>

        {feed.status !== "loaded" ? (
          <LoadNotice
            status={feed.status}
            loadingText="의뢰·제안을 불러오는 중이에요"
            errorText="의뢰·제안을 불러오지 못했어요"
            onRetry={feed.reload}
          />
        ) : (
          <>
            {visible.length > 0 && (
              <ul className="student-explore__list">
                {visible.map((item) => (
                  <li key={exploreItemKey(item)}>
                    {item.type === "JOB" ? (
                      <RequestCard
                        job={item}
                        onOpen={() => navigate(STUDENT_PATHS.requestFull(String(item.jobId)))}
                        onApply={() => navigate(STUDENT_PATHS.apply(String(item.jobId)))}
                      />
                    ) : (
                      <PeerProposalCard
                        proposal={item}
                        mine={sentIds.has(item.proposalId)}
                        like={likeOf(item)}
                        onToggleLike={() => likes.toggle(item.proposalId, likeOf(item))}
                        onOpen={() =>
                          navigate(
                            sentIds.has(item.proposalId)
                              ? STUDENT_PATHS.proposal(String(item.proposalId))
                              : STUDENT_PATHS.peerProposal(String(item.proposalId)),
                          )
                        }
                      />
                    )}
                  </li>
                ))}
              </ul>
            )}
            {visible.length === 0 && !feed.hasNext && (
              <p className="student-explore__empty">조건에 맞는 의뢰·제안이 없어요</p>
            )}
            {feed.hasNext && <div ref={sentinel} aria-hidden="true" />}
            {feed.more !== "idle" && (
              <LoadNotice
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
    </StudentTabScreen>
  );
}

export default StudentExplorePage;
