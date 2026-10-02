import type { MouseEvent } from "react";
import { Button, CategoryBadge, WorkKindIcon } from "../../../components";
import { useLoopCarousel, withLoopClones } from "../../../hooks/useLoopCarousel";
import { formatMonthDay } from "../../../lib/date";
import { studentLabel } from "../lib/format";
import type { OwnerTodo } from "../types";
import "./TodoCarousel.css";

const AUTO_ADVANCE_MS = 4000;
/** TodoCarousel.css 의 transition 시간과 같게 둔다 */
const SLIDE_MS = 300;
/** TodoCarousel.css 의 카드 사이 간격과 같게 둔다 */
const SLIDE_GAP_PX = 12;

/** 카드 종류마다 보여 줄 문구 */
function describe(todo: OwnerTodo) {
  switch (todo.type) {
    case "draftArrived":
      return {
        meta: studentLabel(todo.student),
        status: "초안이 도착했어요",
        detail: `${formatMonthDay(todo.autoCompleteOn)}까지 확인하지 않으면 자동으로 완료돼요`,
        action: "초안 확인하기",
      };
    case "proposalArrived":
      return {
        meta: studentLabel(todo.student),
        status: "새 제안이 도착했어요",
        detail: `광운대생 손님 ${todo.empathyCount}명이 공감했어요`,
        action: "제안 보기",
      };
    case "applicants":
      return {
        meta: `예산 ${todo.budget.toLocaleString("ko-KR")}원`,
        status: `학생 ${todo.applicantCount}명이 지원했어요`,
        detail: `초안 마감 : ${formatMonthDay(todo.draftDue)}`,
        action: "학생 고르기",
      };
  }
}

interface TodoCarouselProps {
  todos: OwnerTodo[];
  onAction: (todo: OwnerTodo) => void;
}

/**
 * 사장님 홈 「확인할 일」. 할 일 하나에 카드 한 장, 4초마다 다음 카드로 넘어간다.
 * 옆으로 넘기거나 양옆 화살표로도 넘기고, 마지막 다음은 오른쪽으로 이어서 처음.
 */
function TodoCarousel({ todos, onAction }: TodoCarouselProps) {
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
    <section className="todo-carousel" aria-roledescription="carousel" aria-label="확인할 일">
      <div className="todo-carousel__frame">
        <div
          className="todo-carousel__viewport"
          {...swipeHandlers}
          onClickCapture={handleClickCapture}
        >
          <div
            ref={trackRef}
            className={`todo-carousel__track${animate ? " todo-carousel__track--animate" : ""}`}
            style={{ transform: `translateX(calc(${-offset} * (100% + ${SLIDE_GAP_PX}px)))` }}
          >
            {slides.map((todo, i) => {
              const hidden = i !== offset || isClone(i);
              const { meta, status, detail, action } = describe(todo);
              return (
                <article
                  key={i}
                  className="todo-carousel__card"
                  aria-roledescription="slide"
                  aria-hidden={hidden}
                  inert={hidden}
                >
                  <div className="todo-carousel__heading">
                    <div className="todo-carousel__title-row">
                      <WorkKindIcon kind={todo.kind} />
                      <h3 className="todo-carousel__title">{todo.title}</h3>
                    </div>
                    <div className="todo-carousel__meta">
                      <CategoryBadge field={todo.field} />
                      <span className="todo-carousel__meta-text">{meta}</span>
                    </div>
                  </div>
                  <div className="todo-carousel__status">
                    <p className="todo-carousel__status-title">{status}</p>
                    <p className="todo-carousel__status-detail">{detail}</p>
                  </div>
                  <Button
                    size="medium"
                    fullWidth
                    className="todo-carousel__action"
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
              className="todo-carousel__arrow todo-carousel__arrow--prev"
              aria-label="이전 할 일"
              onClick={() => go(-1)}
            >
              <svg viewBox="0 0 20 72" fill="none" aria-hidden="true">
                <path d="M13 29.5 7 36l6 6.5" stroke="currentColor" strokeWidth="2" />
              </svg>
            </button>
            <button
              type="button"
              className="todo-carousel__arrow todo-carousel__arrow--next"
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
        <div className="todo-carousel__dots" aria-label={`${todos.length}개 중 ${index + 1}번째`}>
          {todos.map((todo, i) => (
            <span
              key={todo.id}
              className={`todo-carousel__dot${i === index ? " todo-carousel__dot--current" : ""}`}
            />
          ))}
        </div>
      )}
    </section>
  );
}

export default TodoCarousel;
