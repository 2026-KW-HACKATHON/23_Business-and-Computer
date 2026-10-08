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
  FirstVisitGuide,
  OWNER_PATHS,
  OwnerTabScreen,
  TodoCarousel,
  WAITING_STATUS_LABEL,
  deadlineText,
  studentLabel,
  useOwnerHome,
} from "../features/owner";
import type { OwnerTodo, OwnerWorkingItem } from "../features/owner";
import { formatMonthDay } from "../lib/date";
import { useDragScroll } from "../hooks/useDragScroll";
import { finishDemoGuide, pendingDemoGuide } from "../lib/demoGuide";
import { clearSignupGuide, pendingSignupGuide } from "../lib/signupGuide";
import "./OwnerHomePage.css";

/**
 * 피그마 「사장님 홈 (개선안)」.
 * 확인할 일 → 학생이 작업 중 → 기다리는 중 → 이런 의뢰는 어때요? → 끝난 일.
 * 비어 있는 목록은 섹션째 숨기고, 「이런 의뢰는 어때요?」는 늘 보인다. 확인할 일은 할 일이 없어도 남아
 * 「지금 확인할 일이 없어요」 카드를, 이력이 하나도 없는 계정(피그마 「사장님 홈 - 처음」)이면 첫 의뢰 안내 카드를
 * 보인다 (ADR 0051).
 * 섹션은 모두 GET /me/home 한 번에서 온다 (ADR 0064). 홈을 불러오는 중 · 실패면 확인할 일 아래에 안내 줄
 * 하나만 보이고, 홈은 왔는데 섹션 하나만 실패하면 그 섹션 자리에 안내 줄을 보인다. 개수는 불러온 섹션만 보인다.
 */
/** 가입 후 첫 안내 말풍선: ① 확인할 일 · ② 새 의뢰 · ③ 알림 */
const SIGNUP_GUIDE_TIPS = [
  "진행 상황은 여기 확인할 일에서 봐요",
  "새 의뢰는 여기서 올려요",
  "학생 제안이 오면 알림으로 알려 드려요",
] as const;

function OwnerHomePage() {
  const navigate = useNavigate();
  // 가입하고 처음 들어온 홈이면 한 번만 가입 후 첫 안내 (ADR 0053)
  const [signupGuide, setSignupGuide] = useState(() => pendingSignupGuide("owner"));
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
  const home = useOwnerHome();
  // 끝난 일은 접힌 채 최근 1건만 보인다
  const [doneExpanded, setDoneExpanded] = useState(false);
  const exampleScroll = useDragScroll<HTMLUListElement>();
  const doneRows = doneExpanded ? home.done : home.done.slice(0, 1);
  // 「학생이 작업 중」 줄은 「기다리는 중」처럼 상세 화면으로: 보낸 의뢰, 제안으로 시작했으면 받은 제안
  const openWorking = ({ id, proposalId }: OwnerWorkingItem) =>
    navigate(proposalId ? OWNER_PATHS.proposal(proposalId) : OWNER_PATHS.request(id));
  const { sections } = home;
  // 홈 요청 자체를 기다리거나 실패했으면 확인할 일 자리에 홈 안내 줄 하나만
  const wholeHome = home.status !== "loaded";
  const shows = (section: keyof typeof sections, count: number) =>
    !home.firstVisit && !wholeHome && (sections[section] !== "loaded" || count > 0);
  const countOf = (section: keyof typeof sections, count: number) =>
    sections[section] === "loaded" ? count : undefined;

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
      {home.firstVisit && (
        <section className="owner-home__section">
          <SectionHeader title="확인할 일" count={0} />
          <div ref={firstCardRef}>
            <FirstVisitGuide onStart={() => navigate(OWNER_PATHS.newRequest)} />
          </div>
        </section>
      )}

      {!home.firstVisit && (
        <section className="owner-home__section">
          <SectionHeader title="확인할 일" count={countOf("todos", home.todos.length)} />
          {home.todos.length > 0 && <TodoCarousel todos={home.todos} onAction={openTodo} />}
          {home.todos.length === 0 && sections.todos === "loaded" && <TodoNoneCard tone="owner" />}
          {sections.todos !== "loaded" && (
            <LoadNotice
              status={sections.todos}
              loadingText={wholeHome ? "홈을 불러오는 중이에요" : "확인할 일을 불러오는 중이에요"}
              errorText={wholeHome ? "홈을 불러오지 못했어요" : "확인할 일을 불러오지 못했어요"}
              onRetry={home.reload}
            />
          )}
        </section>
      )}

      {shows("working", home.working.length) && (
        <section className="owner-home__section">
          <SectionHeader title="학생이 작업 중" count={countOf("working", home.working.length)} />
          {sections.working === "loaded" ? (
            <div className="owner-home__list">
              {home.working.map((work) => (
                <TaskRow
                  key={work.id}
                  kind={work.kind}
                  title={work.title}
                  lines={[studentLabel({ name: work.student.name }), deadlineText(work.stage, work.due)]}
                  onClick={() => openWorking(work)}
                />
              ))}
            </div>
          ) : (
            <LoadNotice
              status={sections.working}
              loadingText="학생이 작업 중인 일을 불러오는 중이에요"
              errorText="학생이 작업 중인 일을 불러오지 못했어요"
              onRetry={home.reload}
            />
          )}
        </section>
      )}

      {shows("waiting", home.waiting.length) && (
        <section className="owner-home__section">
          <SectionHeader title="기다리는 중" count={countOf("waiting", home.waiting.length)} />
          {sections.waiting === "loaded" ? (
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
          ) : (
            <LoadNotice
              status={sections.waiting}
              loadingText="기다리는 의뢰를 불러오는 중이에요"
              errorText="기다리는 의뢰를 불러오지 못했어요"
              onRetry={home.reload}
            />
          )}
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

      {signupGuide && home.firstVisit === true && (
        <SignupGuide
          tone="owner"
          card={<FirstVisitGuide onStart={() => undefined} />}
          cardRef={firstCardRef}
          tips={SIGNUP_GUIDE_TIPS}
          onClose={closeSignupGuide}
        />
      )}
      {demoGuide && <DemoGuide tone="owner" onClose={closeDemoGuide} />}

      {shows("done", home.done.length) && (
        <section className="owner-home__section">
          <SectionHeader
            title="끝난 일"
            count={countOf("done", home.done.length)}
            actionLabel={home.done.length > 1 ? (doneExpanded ? "접기" : "펼치기 ›") : undefined}
            expanded={doneExpanded}
            onAction={() => setDoneExpanded((v) => !v)}
          />
          {sections.done === "loaded" ? (
            <div className={`owner-home__list${doneExpanded ? " owner-home__list--expanded" : ""}`}>
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
          ) : (
            <LoadNotice
              status={sections.done}
              loadingText="끝난 일을 불러오는 중이에요"
              errorText="끝난 일을 불러오지 못했어요"
              onRetry={home.reload}
            />
          )}
        </section>
      )}
    </OwnerTabScreen>
  );
}

export default OwnerHomePage;
