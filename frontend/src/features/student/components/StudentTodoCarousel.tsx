import type { MouseEvent } from "react";
import { Button, CategoryBadge, TextButton, WorkKindIcon } from "../../../components";
import { useLoopCarousel, withLoopClones } from "../../../hooks/useLoopCarousel";
import { proposalBadgeNames } from "../../proposal";
import { deadlineText } from "../lib/format";
import type { StudentTodo } from "../types";
import "./StudentTodoCarousel.css";

const AUTO_ADVANCE_MS = 4000;
/** StudentTodoCarousel.css 의 transition 시간과 같게 둔다 */
const SLIDE_MS = 300;
/** StudentTodoCarousel.css 의 카드 사이 간격과 같게 둔다 */
const SLIDE_GAP_PX = 12;

/** 카드 종류마다 보여 줄 문구 */
function describe(todo: StudentTodo) {
  switch (todo.type) {
    case "drafting":
      return { status: deadlineText("draft", todo.job.draftDeadline), action: "초안 제출하기" };
    case "revising":
      return { status: "수정 요청이 도착했어요", action: "수정안 제출하기" };
    case "agreement":
    case "proposalAgreement":
      return { status: "제안이 받아들여졌어요", action: "조건 확인" };
  }
}

/** 카드 머리 (종류 · 제목 · 분야 · 가게) */
function heading(todo: StudentTodo) {
  if (todo.type === "proposalAgreement") {
    const { proposal } = todo;
    return {
      key: `proposal-${proposal.proposalId}`,
      kind: "proposal" as const,
      title: proposal.title,
      fields: proposalBadgeNames(proposal.specialtyCategories),
      store: proposal.store.storeName,
    };
  }
  if (todo.type === "agreement") {
    const { work } = todo;
    return {
      key: `agreement-${work.id}`,
      kind: work.kind,
      title: work.title,
      fields: [work.field],
      store: work.store.name,
    };
  }
  const { job } = todo;
  return {
    key: `${todo.type}-${job.jobId}`,
    kind: job.kind,
    title: job.title,
    fields: proposalBadgeNames(job.specialtyCategories),
    store: job.storeName ?? "",
  };
}

interface StudentTodoCarouselProps {
  todos: StudentTodo[];
  /** 「상세보기 ›」 */
  onDetail: (todo: StudentTodo) => void;
  /** 아래 큰 버튼 */
  onAction: (todo: StudentTodo) => void;
}

/**
 * 학생 홈 「확인할 일」. 할 일 하나에 카드 한 장, 4초마다 다음 카드로 넘어간다.
 * 옆으로 넘기거나 양옆 화살표로도 넘기고, 마지막 다음은 오른쪽으로 이어서 처음.
 */
function StudentTodoCarousel({ todos, onDetail, onAction }: StudentTodoCarouselProps) {
  const slides = withLoopClones(todos);
  const { offset, index, animate, looping, trackRef, go, swipeHandlers, takeSwipe, isClone } =
    useLoopCarousel<HTMLDivElement>({
      count: todos.length,
      autoAdvanceMs: AUTO_ADVANCE_MS,
      slideMs: SLIDE_MS,
    });

  // 옆으로 넘긴 직후 손을 뗀 자리의 버튼이 눌리지 않게 막는다
  const handleClickCapture = (e: MouseEvent) => {
    if (!takeSwipe()) return;
    e.preventDefault();
    e.stopPropagation();
  };

  return (
    <section className="student-todo" aria-roledescription="carousel" aria-label="확인할 일">
      <div className="student-todo__frame">
        <div
          className="student-todo__viewport"
          {...swipeHandlers}
          onClickCapture={handleClickCapture}
        >
          <div
            ref={trackRef}
            className={`student-todo__track${animate ? " student-todo__track--animate" : ""}`}
            style={{ transform: `translateX(calc(${-offset} * (100% + ${SLIDE_GAP_PX}px)))` }}
          >
            {slides.map((todo, i) => {
              const hidden = i !== offset || isClone(i);
              const { status, action } = describe(todo);
              const head = heading(todo);
              return (
                <article
                  key={i}
                  className="student-todo__card"
                  aria-roledescription="slide"
                  aria-hidden={hidden}
                  inert={hidden}
                >
                  <div className="student-todo__heading">
                    <div className="student-todo__title-row">
                      <WorkKindIcon kind={head.kind} />
                      <h3 className="student-todo__title">{head.title}</h3>
                    </div>
                    <div className="student-todo__meta">
                      {head.fields.map((name) => (
                        <CategoryBadge key={name} field={name} />
                      ))}
                      <span className="student-todo__meta-text">{head.store}</span>
                    </div>
                  </div>
                  <div className="student-todo__status">
                    <p className="student-todo__status-title">{status}</p>
                    <TextButton onClick={() => onDetail(todo)}>상세보기</TextButton>
                  </div>
                  <Button
                    tone="student"
                    size="medium"
                    fullWidth
                    className="student-todo__action"
                    onClick={() => onAction(todo)}
                  >
                    {action}
                  </Button>
                </article>
              );
            })}
          </div>
        </div>
        {looping && (
          <>
            <button
              type="button"
              className="student-todo__arrow student-todo__arrow--prev"
              aria-label="이전 할 일"
              onClick={() => go(-1)}
            >
              <svg viewBox="0 0 20 72" fill="none" aria-hidden="true">
                <path d="M13 29.5 7 36l6 6.5" stroke="currentColor" strokeWidth="2" />
              </svg>
            </button>
            <button
              type="button"
              className="student-todo__arrow student-todo__arrow--next"
              aria-label="다음 할 일"
              onClick={() => go(1)}
            >
              <svg viewBox="0 0 20 72" fill="none" aria-hidden="true">
                <path d="m7 29.5 6 6.5-6 6.5" stroke="currentColor" strokeWidth="2" />
              </svg>
            </button>
          </>
        )}
      </div>
      {looping && (
        <div className="student-todo__dots" role="img" aria-label={`${todos.length}개 중 ${index + 1}번째`}>
          {todos.map((todo, i) => (
            <span
              key={heading(todo).key}
              className={`student-todo__dot${i === index ? " student-todo__dot--current" : ""}`}
            />
          ))}
        </div>
      )}
    </section>
  );
}

export default StudentTodoCarousel;
