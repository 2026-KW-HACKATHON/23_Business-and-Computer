import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { CategoryBadge, LoadNotice, SectionHeader, TaskRow } from "../components";
import {
  FirstVisitGuide,
  OWNER_PATHS,
  OwnerTabScreen,
  TodoCarousel,
  WorkPlanSheet,
  WAITING_STATUS_LABEL,
  deadlineText,
  studentLabel,
  useOwnerHome,
  useProgressPlanSheet,
} from "../features/owner";
import type { OwnerTodo, OwnerWorkingItem } from "../features/owner";
import { formatMonthDay } from "../lib/date";
import { useDragScroll } from "../hooks/useDragScroll";
import "./OwnerHomePage.css";

/**
 * 피그마 「사장님 홈 (개선안)」.
 * 확인할 일 → 학생이 작업 중 → 기다리는 중 → 이런 의뢰는 어때요? → 끝난 일.
 * 비어 있는 목록은 섹션째 숨기고, 「이런 의뢰는 어때요?」는 늘 보인다.
 * 첫 활동 계정(피그마 「사장님 홈 - 처음」)은 할 일 목록 대신 사용법 안내를 보여 준다.
 * 확인할 일의 「새 제안」은 GET /me/received-proposals 의 결정 대기 제안 (ADR 0025), 도착한 초안 · 수정안과
 * 「학생이 작업 중」은 GET /me/jobs?status=MATCHED (ADR 0035). 불러오는 중 · 실패면 확인할 일 아래에 안내 줄을
 * 보이고, 개수는 둘 다 불러온 뒤에만 보인다.
 */
function OwnerHomePage() {
  const navigate = useNavigate();
  const home = useOwnerHome();
  // 끝난 일은 접힌 채 최근 1건만 보인다
  const [doneExpanded, setDoneExpanded] = useState(false);
  const exampleScroll = useDragScroll<HTMLUListElement>();
  const doneRows = doneExpanded ? home.done : home.done.slice(0, 1);
  // 「학생이 작업 중」 줄을 누르면 작업계획서 바텀시트, 제안으로 시작했으면 받은 제안
  const planSheet = useProgressPlanSheet();
  const openWorking = ({ planJob, proposalId }: OwnerWorkingItem) => {
    if (planJob) planSheet.open(planJob);
    else if (proposalId) navigate(OWNER_PATHS.proposal(proposalId));
  };
  const todosLoaded = home.receivedProposals === "loaded" && home.progress === "loaded";

  const openTodo = (todo: OwnerTodo) => {
    switch (todo.type) {
      case "draftArrived":
        return navigate(OWNER_PATHS.workCheck(todo.id));
      case "proposalArrived":
        return navigate(OWNER_PATHS.proposal(todo.id));
      case "applicants":
        return navigate(OWNER_PATHS.requestApplicants(todo.id));
    }
  };

  return (
    <OwnerTabScreen tab="home" showFab>
      {home.firstVisit && <FirstVisitGuide onStart={() => navigate(OWNER_PATHS.newRequest)} />}

      {!home.firstVisit && (home.todos.length > 0 || !todosLoaded) && (
        <section className="owner-home__section">
          <SectionHeader title="확인할 일" count={todosLoaded ? home.todos.length : undefined} />
          {home.todos.length > 0 && <TodoCarousel todos={home.todos} onAction={openTodo} />}
          {home.progress !== "loaded" && (
            <LoadNotice
              status={home.progress}
              loadingText="진행 중인 작업을 불러오는 중이에요"
              errorText="진행 중인 작업을 불러오지 못했어요"
              onRetry={home.reloadProgress}
            />
          )}
          {home.receivedProposals !== "loaded" && (
            <LoadNotice
              status={home.receivedProposals}
              loadingText="받은 제안을 불러오는 중이에요"
              errorText="받은 제안을 불러오지 못했어요"
              onRetry={home.reloadReceivedProposals}
            />
          )}
        </section>
      )}

      {!home.firstVisit && home.working.length > 0 && (
        <section className="owner-home__section">
          <SectionHeader title="학생이 작업 중" count={home.working.length} />
          <div className="owner-home__list">
            {home.working.map((work) => (
              <TaskRow
                key={work.id}
                kind={work.kind}
                title={work.title}
                lines={[studentLabel({ name: work.student.name }), deadlineText(work.stage, work.due)]}
                onClick={work.planJob || work.proposalId ? () => openWorking(work) : undefined}
              />
            ))}
          </div>
        </section>
      )}

      {!home.firstVisit && home.waiting.length > 0 && (
        <section className="owner-home__section">
          <SectionHeader title="기다리는 중" count={home.waiting.length} />
          <div className="owner-home__list">
            {home.waiting.map((item) => (
              <TaskRow
                key={item.id}
                kind={item.kind}
                title={item.title}
                lines={[deadlineText(item.stage, item.due)]}
                status={WAITING_STATUS_LABEL[item.status]}
                onClick={() => navigate(OWNER_PATHS.request(item.id))}
              />
            ))}
          </div>
        </section>
      )}

      <section className="owner-home__section owner-home__section--wide">
        <div className="owner-home__section-head">
          <SectionHeader
            title="이런 의뢰는 어때요?"
            actionLabel="더 보기 ›"
            onAction={() => navigate(OWNER_PATHS.explore)}
          />
        </div>
        <ul className="owner-home__examples" {...exampleScroll}>
          {home.examples.map((example) => (
            <li key={example.id}>
              <button
                type="button"
                className="owner-home__example"
                onClick={() => navigate(OWNER_PATHS.newRequest, { state: { exampleId: example.id } })}
              >
                <CategoryBadge field={example.field} />
                <span className="owner-home__example-title">{example.title}</span>
                <span className="owner-home__example-link">이 예시로 의뢰하기 ›</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      {!home.firstVisit && home.done.length > 0 && (
        <section className="owner-home__section">
          <SectionHeader
            title="끝난 일"
            count={home.done.length}
            actionLabel={home.done.length > 1 ? (doneExpanded ? "접기" : "펼치기 ›") : undefined}
            expanded={doneExpanded}
            onAction={() => setDoneExpanded((v) => !v)}
          />
          <div className="owner-home__list">
            {doneRows.map((item) => (
              <TaskRow
                key={item.id}
                kind={item.kind}
                title={item.title}
                lines={[studentLabel({ name: item.student.name }), `완료 : ${formatMonthDay(item.completedOn)}`]}
                onClick={() => navigate(OWNER_PATHS.workResult(item.id))}
              />
            ))}
          </div>
        </section>
      )}

      <WorkPlanSheet
        content={planSheet.content}
        onClose={planSheet.close}
        onChat={() => navigate(OWNER_PATHS.chats)}
      />
    </OwnerTabScreen>
  );
}

export default OwnerHomePage;
