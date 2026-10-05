import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FieldFilter, KindTabs, SearchBar, TextButton } from "../components";
import type { CardKind } from "../components";
import {
  ExploreTabs,
  PeerProposalCard,
  RequestCard,
  STUDENT_PATHS,
  StudentTabScreen,
  toggleEmpathy,
  useExploreRequests,
  usePeerProposals,
  useStudentApplications,
} from "../features/student";
import type { PeerProposal, StudentRequest } from "../features/student";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import { useDragScroll } from "../hooks/useDragScroll";
import "./StudentExplorePage.css";

type Item =
  | { kind: "request"; createdAt: string; request: StudentRequest }
  | { kind: "proposal"; createdAt: string; proposal: PeerProposal };

/**
 * 피그마 「학생 탐색」. 가게 의뢰와 다른 학생 제안을 종류 · 분야 · 검색어로 거른다.
 * 의뢰는 바로 지원하고, 제안에는 하트로 공감한다.
 */
function StudentExplorePage() {
  const navigate = useNavigate();
  const requests = useExploreRequests();
  const peers = usePeerProposals();
  const applications = useStudentApplications();
  const [kind, setKind] = useState<CardKind>("all");
  const [field, setField] = useState<Field | null>(null);
  const [query, setQuery] = useState("");
  const fieldScroll = useDragScroll<HTMLDivElement>();

  const keyword = query.trim();
  const items: Item[] = [
    ...requests.map((request) => ({ kind: "request" as const, createdAt: request.createdAt, request })),
    ...peers.map((proposal) => ({ kind: "proposal" as const, createdAt: proposal.createdAt, proposal })),
  ];
  const visible = items
    .filter((item) => kind === "all" || item.kind === kind)
    .filter((item) => {
      const f = item.kind === "request" ? item.request.field : item.proposal.field;
      return field === null || f === field;
    })
    .filter((item) => {
      if (keyword === "") return true;
      const title = item.kind === "request" ? item.request.title : item.proposal.title;
      const store = item.kind === "request" ? item.request.store.name : item.proposal.storeName;
      return title.includes(keyword) || store.includes(keyword);
    })
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

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
          <KindTabs value={kind} onChange={setKind} />
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
            <TextButton showFilter showChevron={false}>
              최신순
            </TextButton>
          </div>
          <p className="student-explore__description">
            {"가게 의뢰에 지원하고, 다른 학생의 제안에\n하트를 눌러 손님으로서 공감해 보세요."}
          </p>
        </div>

        {visible.length > 0 ? (
          <ul className="student-explore__list">
            {visible.map((item) =>
              item.kind === "request" ? (
                <li key={item.request.id}>
                  <RequestCard
                    request={item.request}
                    applied={applications.some((a) => a.requestId === item.request.id)}
                    onOpen={() => navigate(STUDENT_PATHS.request(item.request.id))}
                    onApply={() => navigate(STUDENT_PATHS.apply(item.request.id))}
                  />
                </li>
              ) : (
                <li key={item.proposal.id}>
                  <PeerProposalCard
                    proposal={item.proposal}
                    onOpen={() =>
                      navigate(
                        item.proposal.mine
                          ? STUDENT_PATHS.proposal(item.proposal.id)
                          : STUDENT_PATHS.peerProposal(item.proposal.id),
                      )
                    }
                    onToggleEmpathy={() => toggleEmpathy(item.proposal.id)}
                  />
                </li>
              ),
            )}
          </ul>
        ) : (
          <p className="student-explore__empty">조건에 맞는 의뢰·제안이 없어요</p>
        )}
      </div>
    </StudentTabScreen>
  );
}

export default StudentExplorePage;
