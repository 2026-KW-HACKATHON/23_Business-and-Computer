import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CategoryBadge,
  DemoGuide,
  LoadNotice,
  SectionHeader,
  SignupGuide,
  TaskRow,
  TodoNoneCard,
} from "../components";
import {
  PeerProposalRow,
  STUDENT_PATHS,
  StudentFirstVisitGuide,
  StudentTabScreen,
  StudentTodoCarousel,
  deadlineText,
  useStudentHome,
} from "../features/student";
import type { StudentPeerProposal, StudentTodo, StudentWaitingItem } from "../features/student";
import { formatMonthDay } from "../lib/date";
import { useDragScroll } from "../hooks/useDragScroll";
import { finishDemoGuide, pendingDemoGuide } from "../lib/demoGuide";
import { clearSignupGuide, pendingSignupGuide } from "../lib/signupGuide";
import "./StudentHomePage.css";
import { useProposalLikes } from "../features/proposal";

/**
 * 피그마 「학생 홈 (개선안)」.
 * 확인할 일 → 다른 학생들의 제안 공감하기 → 사장님이 확인 중 → 기다리는 중 → 이런 제안은 어때요? → 끝난 일.
 * 비어 있는 목록은 섹션째 숨기되, 확인할 일은 할 일이 없어도 남아 「지금 확인할 일이 없어요」 카드를 보인다.
 * 이력이 하나도 없으면 피그마 「학생 홈 - 처음」처럼 확인할 일 자리의 첫 제안 안내 → 이런 제안은 어때요? →
 * 공감하기만 보인다 (ADR 0051). 공감하기는 이력으로 세지 않는다.
 * 모두 GET /me/home 한 번에서 온다 (ADR 0065). 통째로 못 불러오면 「다시 시도」 한 줄,
 * 섹션 하나만 못 불러오면 그 섹션 자리에 「다시 시도」 줄을 보인다 (같은 요청을 다시 보낸다).
 * 공감하기는 못 불러오면 섹션째 숨기고 나머지 홈은 그대로 보인다 (ADR 0026).
 * 처음인지 알 수 없고 보이는 항목도 없으면 사용법 안내 대신 「다시 시도」 줄만 보인다.
 */
/** 가입 후 첫 안내 말풍선: ① 확인할 일 · ② 새 제안 · ③ 알림 */
const SIGNUP_GUIDE_TIPS = [
  "진행 상황은 여기 확인할 일에서 봐요",
  "새 제안은 여기서 써요",
  "사장님 답이 오면 알림으로 알려 드려요",
] as const;

function StudentHomePage() {
  const navigate = useNavigate();
  // 가입하고 처음 들어온 홈이면 한 번만 가입 후 첫 안내 (ADR 0053)
  const [signupGuide, setSignupGuide] = useState(() => pendingSignupGuide("student"));
  const firstCardRef = useRef<HTMLDivElement>(null);
  const closeSignupGuide = () => {
    clearSignupGuide();
    setSignupGuide(false);
  };
  // 역할 선택에서 둘러보기를 막 시작했으면 둘러보기 첫 안내 (역할 전환 뱃지로 넘어올 때는 없음)
  const [demoGuide, setDemoGuide] = useState(() => pendingDemoGuide());
  const closeDemoGuide = () => {
    finishDemoGuide();
    setDemoGuide(false);
  };
  const { load, reload, examples } = useStudentHome();
  const likes = useProposalLikes();
  // 끝난 일은 접힌 채 최근 1건만 보인다
  const [doneExpanded, setDoneExpanded] = useState(false);
  const exampleScroll = useDragScroll<HTMLUListElement>();

  // 상세보기: 지금 화면을 본다 / 아래 버튼: 바로 할 일로 간다
  const openDetail = (todo: StudentTodo) => {
    if (todo.type === "proposalAgreement") {
      return navigate(STUDENT_PATHS.proposalStart(String(todo.proposalId)));
    }
    const id = String(todo.jobId);
    if (todo.type === "drafting") return navigate(STUDENT_PATHS.workSubmit(id));
    return navigate(STUDENT_PATHS.workRevision(id));
  };
  const openAction = (todo: StudentTodo) => {
    if (todo.type === "proposalAgreement") {
      return navigate(STUDENT_PATHS.proposalStart(String(todo.proposalId)));
    }
    const id = String(todo.jobId);
    if (todo.type === "drafting") return navigate(STUDENT_PATHS.workSubmit(id));
    return navigate(STUDENT_PATHS.workRevisionSubmit(id));
  };

  const waitingRow = (item: StudentWaitingItem) =>
    item.type === "proposal" ? (
      <TaskRow
        key={item.proposalId}
        kind="proposal"
        title={item.title}
        lines={[
          item.storeName ? `${item.storeName}에 보낸 제안` : "보낸 제안",
          `손님 ${item.likeCount}명 공감`,
        ]}
        status="수락 대기"
        onClick={() => navigate(STUDENT_PATHS.proposal(String(item.proposalId)))}
      />
    ) : (
      <TaskRow
        key={`job-${item.jobApplicationId}`}
        kind="request"
        title={item.title}
        lines={[
          item.storeName ? `${item.storeName} 의뢰에 지원` : "의뢰에 지원",
          ...(item.draftDeadline ? [deadlineText("draft", item.draftDeadline)] : []),
        ]}
        status="사장님이 고르는 중"
        onClick={() => navigate(STUDENT_PATHS.requestFull(String(item.jobId)))}
      />
    );

  // 이 화면에서 누른 공감이 있으면 그 값, 없으면 목록의 값
  const likeOf = (proposal: StudentPeerProposal) =>
    likes.likeOf(proposal.proposalId, {
      likeCount: proposal.likeCount,
      likedByMe: proposal.likedByMe,
    });

  const examplesSection = (
    <section className="student-home__section student-home__section--wide">
      <div className="student-home__section-head">
        <SectionHeader
          title="이런 제안은 어때요?"
          actionLabel="더 보기 ›"
          onAction={() => navigate(STUDENT_PATHS.explore)}
        />
      </div>
      <ul className="student-home__examples" {...exampleScroll}>
        {examples.map((example) => (
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

  // 홈 전체를 불러오는 중 · 실패
  if (load.status !== "loaded") {
    return (
      <StudentTabScreen tab="home" showFab>
        <section className="student-home__section">
          <LoadNotice
            status={load.status}
            loadingText="홈을 불러오는 중이에요"
            errorText="홈을 불러오지 못했어요"
            onRetry={reload}
          />
        </section>
      </StudentTabScreen>
    );
  }

  const { home, retrying } = load;
  /** 못 불러온 섹션 자리. 「다시 시도」는 홈을 다시 불러오고, 그동안 불러오는 중을 보인다 */
  const sectionNotice = (subject: string) => (
    <LoadNotice
      status={retrying ? "loading" : "error"}
      loadingText={`${subject} 불러오는 중이에요`}
      errorText={`${subject} 불러오지 못했어요`}
      onRetry={reload}
    />
  );

  const peerSection = home.peerProposals.length > 0 && (
    <section className="student-home__section">
      <SectionHeader
        title="다른 학생들의 제안 공감하기"
        actionLabel="전체 ›"
        onAction={() => navigate(STUDENT_PATHS.explore)}
      />
      <div className="student-home__list">
        {home.peerProposals.map((proposal) => (
          <PeerProposalRow
            key={proposal.proposalId}
            proposal={proposal}
            like={likeOf(proposal)}
            onToggleLike={() => likes.toggle(proposal.proposalId, likeOf(proposal))}
            onOpen={() => navigate(STUDENT_PATHS.peerProposal(String(proposal.proposalId)))}
          />
        ))}
      </div>
    </section>
  );

  if (home.firstVisit === undefined) {
    return (
      <StudentTabScreen tab="home" showFab>
        <section className="student-home__section">{sectionNotice("홈을")}</section>
      </StudentTabScreen>
    );
  }

  if (home.firstVisit) {
    return (
      <StudentTabScreen tab="home" showFab>
        <section className="student-home__section">
          <SectionHeader title="확인할 일" count={0} />
          <div ref={firstCardRef}>
            <StudentFirstVisitGuide onStart={() => navigate(STUDENT_PATHS.newProposal)} />
          </div>
        </section>
        {examplesSection}
        {peerSection}
        {signupGuide && (
          <SignupGuide
            tone="student"
            card={<StudentFirstVisitGuide onStart={() => undefined} />}
            cardRef={firstCardRef}
            tips={SIGNUP_GUIDE_TIPS}
            onClose={closeSignupGuide}
          />
        )}
        {demoGuide && <DemoGuide tone="student" onClose={closeDemoGuide} />}
      </StudentTabScreen>
    );
  }

  const { todos, checking, waiting, done } = home;

  return (
    <StudentTabScreen tab="home" showFab>
      <section className="student-home__section">
        {todos === null ? (
          <>
            <SectionHeader title="확인할 일" />
            {sectionNotice("확인할 일을")}
          </>
        ) : (
          <>
            <SectionHeader title="확인할 일" count={todos.length} />
            {todos.length > 0 ? (
              <StudentTodoCarousel todos={todos} onDetail={openDetail} onAction={openAction} />
            ) : (
              <TodoNoneCard tone="student" />
            )}
          </>
        )}
      </section>

      {peerSection}

      {checking === null ? (
        <section className="student-home__section">
          <SectionHeader title="사장님이 확인 중" />
          {sectionNotice("사장님이 확인 중인 작업을")}
        </section>
      ) : (
        checking.length > 0 && (
          <section className="student-home__section">
            <SectionHeader title="사장님이 확인 중" count={checking.length} />
            <div className="student-home__list">
              {checking.map((job) => (
                <TaskRow
                  key={job.jobId}
                  kind={job.kind}
                  title={job.title}
                  lines={[
                    ...(job.storeName ? [`${job.storeName} 사장님`] : []),
                    job.submittedOn
                      ? `${job.revisionSubmitted ? "수정안" : "초안"} 제출 : ${formatMonthDay(job.submittedOn)}`
                      : `${job.revisionSubmitted ? "수정안" : "초안"} 제출, 사장님 확인 중`,
                  ]}
                  onClick={() => navigate(STUDENT_PATHS.workSubmitted(String(job.jobId)))}
                />
              ))}
            </div>
          </section>
        )
      )}

      {waiting === null ? (
        <section className="student-home__section">
          <SectionHeader title="기다리는 중" />
          {sectionNotice("보낸 제안과 지원한 의뢰를")}
        </section>
      ) : (
        waiting.length > 0 && (
          <section className="student-home__section">
            <SectionHeader title="기다리는 중" count={waiting.length} />
            <div className="student-home__list">{waiting.map(waitingRow)}</div>
          </section>
        )
      )}

      {examplesSection}

      {done === null ? (
        <section className="student-home__section">
          <SectionHeader title="끝난 일" />
          {sectionNotice("끝난 일을")}
        </section>
      ) : (
        done.length > 0 && (
          <section className="student-home__section">
            <SectionHeader
              title="끝난 일"
              count={done.length}
              actionLabel={done.length > 1 ? (doneExpanded ? "접기" : "펼치기 ›") : undefined}
              expanded={doneExpanded}
              onAction={() => setDoneExpanded((v) => !v)}
            />
            <div className={`student-home__list${doneExpanded ? " student-home__list--expanded" : ""}`}>
              {(doneExpanded ? done : done.slice(0, 1)).map((job) => (
                <TaskRow
                  key={job.jobId}
                  kind={job.kind}
                  title={job.title}
                  lines={[
                    `${job.storeName ?? "가게"} 사장님`,
                    ...(job.completedOn ? [`완료 : ${formatMonthDay(job.completedOn)}`] : []),
                  ]}
                  onClick={() => navigate(STUDENT_PATHS.workResult(String(job.jobId)))}
                />
              ))}
            </div>
          </section>
        )
      )}
      {demoGuide && <DemoGuide tone="student" onClose={closeDemoGuide} />}
    </StudentTabScreen>
  );
}

export default StudentHomePage;
