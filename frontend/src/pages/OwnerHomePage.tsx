import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, CategoryBadge, ChatButton, SectionHeader, TaskRow } from "../components";
import {
  OWNER_PATHS,
  OwnerTabScreen,
  TodoCarousel,
  WAITING_STATUS_LABEL,
  deadlineText,
  studentLabel,
  useOwnerHome,
} from "../features/owner";
import type { OwnerTodo } from "../features/owner";
import { formatMonthDay } from "../lib/date";
import "./OwnerHomePage.css";

/**
 * 피그마 「사장님 홈 (개선안)」.
 * 확인할 일 → 학생이 작업 중 → 기다리는 중 → 이런 의뢰는 어때요? → 끝난 일.
 * 비어 있는 목록은 섹션째 숨기고, 「이런 의뢰는 어때요?」는 늘 보인다.
 */
function OwnerHomePage() {
  const navigate = useNavigate();
  const home = useOwnerHome();
  // 끝난 일은 접힌 채 최근 1건만 보인다
  const [doneExpanded, setDoneExpanded] = useState(false);
  const doneRows = doneExpanded ? home.done : home.done.slice(0, 1);

  const openTodo = (todo: OwnerTodo) => {
    navigate(todo.kind === "proposal" ? OWNER_PATHS.proposal(todo.id) : OWNER_PATHS.request(todo.id));
  };

  return (
    <OwnerTabScreen tab="home" hasUnread={home.hasUnreadNotifications} showFab>
      {home.todos.length > 0 && (
        <section className="owner-home__section">
          <SectionHeader title="확인할 일" count={home.todos.length} />
          <TodoCarousel todos={home.todos} onAction={openTodo} />
        </section>
      )}

      {home.working.length > 0 && (
        <section className="owner-home__section">
          <SectionHeader title="학생이 작업 중" count={home.working.length} />
          <div className="owner-home__list">
            {home.working.map((work) => (
              <TaskRow
                key={work.id}
                kind={work.kind}
                title={work.title}
                lines={[studentLabel(work.student), deadlineText(work.stage, work.due)]}
                trailing={
                  <>
                    <ChatButton
                      role="owner"
                      label={`${work.student.name} 학생과 채팅`}
                      onClick={() => navigate(OWNER_PATHS.chat(work.chatId))}
                    />
                    <Button
                      variant="secondary"
                      size="small"
                      onClick={() => navigate(OWNER_PATHS.request(work.id))}
                    >
                      상세보기
                    </Button>
                  </>
                }
              />
            ))}
          </div>
        </section>
      )}

      {home.waiting.length > 0 && (
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
                trailing={
                  <Button
                    variant="secondary"
                    size="small"
                    onClick={() => navigate(OWNER_PATHS.request(item.id))}
                  >
                    상세보기
                  </Button>
                }
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
            onAction={() => navigate(OWNER_PATHS.requestExamples)}
          />
        </div>
        <ul className="owner-home__examples">
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

      {home.done.length > 0 && (
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
                lines={[studentLabel(item.student), `완료 : ${formatMonthDay(item.completedOn)}`]}
                trailing={
                  <Button
                    variant="secondary"
                    size="small"
                    onClick={() => navigate(OWNER_PATHS.requestResult(item.id))}
                  >
                    결과물 보기
                  </Button>
                }
              />
            ))}
          </div>
        </section>
      )}
    </OwnerTabScreen>
  );
}

export default OwnerHomePage;
