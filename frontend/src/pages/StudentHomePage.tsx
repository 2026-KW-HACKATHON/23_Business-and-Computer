import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { CategoryBadge, SectionHeader, TaskRow } from "../components";
import {
  PeerProposalRow,
  STUDENT_PATHS,
  StudentFirstVisitGuide,
  StudentTabScreen,
  StudentTodoCarousel,
  deadlineText,
  toggleEmpathy,
  useStudentHome,
} from "../features/student";
import type { StudentTodo, StudentWaitingItem } from "../features/student";
import { formatMonthDay } from "../lib/date";
import "./StudentHomePage.css";

/**
 * 피그마 「학생 홈 (개선안)」.
 * 확인할 일 → 다른 학생들의 제안 공감하기 → 사장님이 확인 중 → 기다리는 중 → 이런 제안은 어때요? → 끝난 일.
 * 비어 있는 목록은 섹션째 숨긴다. 이력이 하나도 없으면 피그마 「학생 홈 - 처음」처럼
 * 사용법 안내 → 이런 제안은 어때요? → 공감하기만 보인다.
 */
function StudentHomePage() {
  const navigate = useNavigate();
  const home = useStudentHome();
  // 끝난 일은 접힌 채 최근 1건만 보인다
  const [doneExpanded, setDoneExpanded] = useState(false);
  const doneRows = doneExpanded ? home.done : home.done.slice(0, 1);
  const peers = home.peerProposals.slice(0, 2);

  // 상세보기: 지금 화면을 본다 / 아래 버튼: 바로 할 일로 간다
  const openDetail = ({ type, work }: StudentTodo) => {
    if (type === "drafting") return navigate(STUDENT_PATHS.workSubmit(work.id));
    if (type === "revising") return navigate(STUDENT_PATHS.workRevision(work.id));
    return navigate(STUDENT_PATHS.workStart(work.id));
  };
  const openAction = ({ type, work }: StudentTodo) => {
    if (type === "drafting") return navigate(STUDENT_PATHS.workSubmit(work.id));
    if (type === "revising") return navigate(STUDENT_PATHS.workRevisionSubmit(work.id));
    return navigate(STUDENT_PATHS.workStart(work.id));
  };

  const waitingRow = (item: StudentWaitingItem) =>
    item.type === "proposal" ? (
      <TaskRow
        key={item.proposal.id}
        kind="proposal"
        title={item.proposal.title}
        lines={[`${item.proposal.store.name}에 보낸 제안`, `손님 ${item.proposal.empathyCount}명 공감`]}
        status="수락 대기"
        onClick={() => navigate(STUDENT_PATHS.proposal(item.proposal.id))}
      />
    ) : (
      <TaskRow
        key={item.request.id}
        kind="request"
        title={item.request.title}
        lines={[`${item.request.store.name} 의뢰에 지원`, deadlineText("draft", item.request.draftDue)]}
        status="사장님이 고르는 중"
        onClick={() => navigate(STUDENT_PATHS.requestFull(item.request.id))}
      />
    );

  const peerSection = peers.length > 0 && (
    <section className="student-home__section">
      <SectionHeader
        title="다른 학생들의 제안 공감하기"
        actionLabel="전체 ›"
        onAction={() => navigate(STUDENT_PATHS.explore)}
      />
      <div className="student-home__list">
        {peers.map((proposal) => (
          <PeerProposalRow
            key={proposal.id}
            proposal={proposal}
            onOpen={() => navigate(STUDENT_PATHS.peerProposal(proposal.id))}
            onToggleEmpathy={() => toggleEmpathy(proposal.id)}
          />
        ))}
      </div>
    </section>
  );

  const examplesSection = (
    <section className="student-home__section student-home__section--wide">
      <div className="student-home__section-head">
        <SectionHeader
          title="이런 제안은 어때요?"
          actionLabel="더 보기 ›"
          onAction={() => navigate(STUDENT_PATHS.explore)}
        />
      </div>
      <ul className="student-home__examples">
        {home.examples.map((example) => (
          <li key={example.id}>
            <button
              type="button"
              className="student-home__example"
              onClick={() =>
                navigate(STUDENT_PATHS.newProposal, { state: { exampleId: example.id } })
              }
            >
              <CategoryBadge field={example.field} />
              <span className="student-home__example-title">{example.title}</span>
              <span className="student-home__example-link">이 예시로 제안 쓰기 ›</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );

  if (home.firstVisit) {
    return (
      <StudentTabScreen tab="home" hasUnread={home.hasUnreadNotifications} showFab>
        <StudentFirstVisitGuide onStart={() => navigate(STUDENT_PATHS.newProposal)} />
        {examplesSection}
        {peerSection}
      </StudentTabScreen>
    );
  }

  return (
    <StudentTabScreen tab="home" hasUnread={home.hasUnreadNotifications} showFab>
      {home.todos.length > 0 && (
        <section className="student-home__section">
          <SectionHeader title="확인할 일" count={home.todos.length} />
          <StudentTodoCarousel todos={home.todos} onDetail={openDetail} onAction={openAction} />
        </section>
      )}

      {peerSection}

      {home.checking.length > 0 && (
        <section className="student-home__section">
          <SectionHeader title="사장님이 확인 중" count={home.checking.length} />
          <div className="student-home__list">
            {home.checking.map((work) => (
              <TaskRow
                key={work.id}
                kind={work.kind}
                title={work.title}
                lines={[
                  `${work.store.name} 사장님`,
                  `${work.revisionCount > 0 ? "수정안" : "초안"} 제출 : ${formatMonthDay(work.submittedOn ?? "")}`,
                ]}
                onClick={() => navigate(STUDENT_PATHS.workSubmitted(work.id))}
              />
            ))}
          </div>
        </section>
      )}

      {home.waiting.length > 0 && (
        <section className="student-home__section">
          <SectionHeader title="기다리는 중" count={home.waiting.length} />
          <div className="student-home__list">{home.waiting.map(waitingRow)}</div>
        </section>
      )}

      {examplesSection}

      {home.done.length > 0 && (
        <section className="student-home__section">
          <SectionHeader
            title="끝난 일"
            count={home.done.length}
            actionLabel={home.done.length > 1 ? (doneExpanded ? "접기" : "펼치기 ›") : undefined}
            expanded={doneExpanded}
            onAction={() => setDoneExpanded((v) => !v)}
          />
          <div className="student-home__list">
            {doneRows.map((work) => (
              <TaskRow
                key={work.id}
                kind={work.kind}
                title={work.title}
                lines={[`${work.store.name} 사장님`, `완료 : ${formatMonthDay(work.completedOn ?? "")}`]}
                onClick={() => navigate(STUDENT_PATHS.workResult(work.id))}
              />
            ))}
          </div>
        </section>
      )}
    </StudentTabScreen>
  );
}

export default StudentHomePage;
