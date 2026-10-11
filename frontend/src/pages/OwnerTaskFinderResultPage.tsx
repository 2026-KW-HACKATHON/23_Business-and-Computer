import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Button, CategoryBadge, SubScreen, TextButton } from "../components";
import { OWNER_PATHS, TASK_FINDER_QUESTIONS, readTaskFinderAnswers, taskFinderResult } from "../features/owner";
import type { TaskFinderAreaStatus, TaskFinderPick } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerTaskFinderPage.css";

const STATUS_LABEL: Record<TaskFinderAreaStatus, string> = {
  good: "좋아요",
  watch: "살펴보기",
  need: "챙겨야 해요",
  unknown: "답 안 함",
};

/**
 * 피그마 「맡길 일 찾기 - 결과」 · 「결과 (해당 없음)」 (ADR 0068).
 * 맨 위 「우리 가게 점검표」에 네 영역(손님이 찾아오게 · 가게 알리기 · 단골 만들기 · 주문·안내)마다 질문 한 칸씩
 * (초록 괜찮음 · 빨강 필요 · 회색 건너뜀)과 상태를 보이고,
 * 그 아래 점수 높은 추천 3개를 「먼저 맡기면 좋은 일」로, 나머지는 「다른 추천 N가지 더 보기」에 접어 둔다.
 * 추천 답이 하나도 없으면 「이런 일도 맡겨 볼 수 있어요」.
 * 카드를 누르면 홈 「이런 의뢰는 어때요?」처럼 그 예시로 채운 의뢰 등록 1/3 으로 간다 (B-2).
 * ← 는 마지막으로 본 질문으로, 「다시 점검하기」는 첫 질문부터, 「홈으로」는 사장님 홈.
 */
function OwnerTaskFinderResultPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.home);
  const answers = readTaskFinderAnswers(location.state);
  const [showMore, setShowMore] = useState(false);

  if (!answers || answers.length !== TASK_FINDER_QUESTIONS.length) {
    return <Navigate to={OWNER_PATHS.taskFinder(1)} replace />;
  }
  const { matched, picks, more, areas } = taskFinderResult(answers);
  const answeredNone = areas.every(({ status }) => status === "unknown");

  const pickCard = ({ example, cardTitle, reason }: TaskFinderPick) => (
    <li key={example.id}>
      <button
        type="button"
        className="task-finder__pick"
        onClick={() => navigate(OWNER_PATHS.newRequest, { state: { exampleId: example.id } })}
      >
        <CategoryBadge field={example.field} />
        <strong className="task-finder__pick-title">{cardTitle}</strong>
        <span className="task-finder__pick-reason">{reason}</span>
        <span className="task-finder__pick-foot">
          <span>예시 작업비 {formatWon(example.content.budget)}</span>
          <span className="task-finder__pick-link">이 일로 의뢰하기 ›</span>
        </span>
      </button>
    </li>
  );

  return (
    <SubScreen
      title="맡길 일 찾기"
      onBack={back}
      footer={
        <div className="task-finder__footer">
          <Button variant="secondary" fullWidth onClick={() => navigate(OWNER_PATHS.taskFinder(1))}>
            다시 점검하기
          </Button>
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.home)}>
            홈으로
          </Button>
        </div>
      }
    >
      <div className="task-finder task-finder--result">
        <div className="task-finder__intro">
          <h2 className="task-finder__question">우리 가게 점검 결과</h2>
          <p className="task-finder__hint">답해 주신 내용으로 가게를 네 가지로 살펴봤어요</p>
        </div>

        <section className="task-finder__report" aria-label="우리 가게 점검표">
          <div className="task-finder__report-head">
            <h3 className="task-finder__report-title">우리 가게 점검표</h3>
            <span className="task-finder__legend" aria-hidden="true">
              <span className="task-finder__legend-dot task-finder__area-mark--good" />
              괜찮음
              <span className="task-finder__legend-dot task-finder__area-mark--need" />
              필요
            </span>
          </div>
          <ul className="task-finder__areas">
            {areas.map(({ area, status, marks }) => (
              <li key={area.name} className="task-finder__area">
                <span className="task-finder__area-name">{area.name}</span>
                <span className="task-finder__area-marks" aria-hidden="true">
                  {marks.map((mark, i) => (
                    <span key={i} className={`task-finder__area-mark task-finder__area-mark--${mark}`} />
                  ))}
                </span>
                <span className={`task-finder__area-status task-finder__area-status--${status}`}>
                  {STATUS_LABEL[status]}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className="task-finder__section">
          <div className="task-finder__section-head">
            <h3 className="task-finder__section-title">
              {matched ? `먼저 맡기면 좋은 일 ${picks.length}가지` : "이런 일도 맡겨 볼 수 있어요"}
            </h3>
            <p className="task-finder__hint">
              {matched
                ? "여러 답과 이어지는 일부터 골랐어요. 누르면 의뢰서가 채워져요"
                : answeredNone
                  ? "답한 질문이 없어서 손님을 모을 때 많이 맡기는 일부터 골랐어요"
                  : "지금도 가게를 잘 챙기고 계세요. 손님을 더 모으고 싶다면 이런 일부터 시작해 보세요"}
            </p>
          </div>
          <ul className="task-finder__picks">{picks.map(pickCard)}</ul>
          {more.length > 0 && (
            <>
              <TextButton
                className="task-finder__more"
                showChevron={!showMore}
                aria-expanded={showMore}
                onClick={() => setShowMore((v) => !v)}
              >
                {showMore ? "다른 추천 접기" : `다른 추천 ${more.length}가지 더 보기`}
              </TextButton>
              {showMore && <ul className="task-finder__picks">{more.map(pickCard)}</ul>}
            </>
          )}
        </section>

      </div>
    </SubScreen>
  );
}

export default OwnerTaskFinderResultPage;
